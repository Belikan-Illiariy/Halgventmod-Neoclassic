package com.nrdhvnstd.halgventmod.combat;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.WeaponProfilesSyncPayload;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Профили оружия (ТЗ боя, 11): классы — data/&lt;пространство&gt;/stats/weapons/&lt;класс&gt;.json, предметы —
 * data/halgventmod/stats/weapon_items.json:
 * <pre>
 * {
 *   "items": { "conquest_armory:shortsword": "sword",
 *              "conquest_armory:scimitar": { "class": "sword", "attacks": { "slash": { "type": "cutting" } } } },
 *   "tags":  { "minecraft:swords": "sword" }
 * }
 * </pre>
 * У предмета можно переопределить damage и windup (множители), blade_length, accuracy, penetration и отдельные удары
 * (attacks, alt_attacks). Изысканный вид conquest_armory (exquisite_&lt;имя&gt;) берёт запись обычного. Пустая рука —
 * класс «кулак». Предмета нет в игре (оружие conquest_armory без мода) — запись молча пропускается.
 *
 * Сервер читает при запуске и по /reload и рассылает исходный JSON клиентам (WeaponProfilesSyncPayload), клиент
 * разбирает его тем же кодом — для отклика интерфейса и анимаций.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class WeaponProfiles {
    public static final String CLASS_DIR = "stats/weapons";
    public static final ResourceLocation ITEMS_FILE = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "stats/weapon_items.json");
    private static final Gson GSON = new Gson();
    /** Щиты (ТЗ боя, 5.6): во второй руке — блок щитом и удар щитом на R. */
    public static final TagKey<Item> SHIELDS = ItemTags.create(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "shields"));

    /** Разобранный набор: классы, предметы, теги. */
    public record Set(Map<WeaponClass, WeaponProfile> classes, Map<Item, WeaponProfile> items,
                      List<Map.Entry<TagKey<Item>, WeaponProfile>> tags) {
        static final Set EMPTY = new Set(Map.of(), Map.of(), List.of());

        @Nullable
        WeaponProfile find(ItemStack stack) {
            if (stack.isEmpty()) {
                return classes.get(WeaponClass.FIST);
            }
            WeaponProfile direct = items.get(stack.getItem());
            if (direct != null) {
                return direct;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id.getPath().startsWith("exquisite_")) {
                Optional<Item> base = BuiltInRegistries.ITEM.getOptional(
                        id.withPath(id.getPath().substring("exquisite_".length())));
                if (base.isPresent() && items.containsKey(base.get())) {
                    return items.get(base.get());
                }
            }
            for (Map.Entry<TagKey<Item>, WeaponProfile> tag : tags) {
                if (stack.is(tag.getKey())) {
                    return tag.getValue();
                }
            }
            return null;
        }
    }

    private static Set server = Set.EMPTY;
    private static Set client = Set.EMPTY;
    private static WeaponProfilesSyncPayload sync = new WeaponProfilesSyncPayload(List.of(), "{}");

    private WeaponProfiles() {}

    /** Профиль того, что в руке (у игрока и моба), или null — бьёт ванильно (не оружие боя). Пустая рука — кулак. */
    @Nullable
    public static WeaponProfile of(LivingEntity entity, ItemStack stack) {
        return (entity.level().isClientSide ? client : server).find(stack);
    }

    /** Удар и ПКМ этого игрока ведёт бой, а не ваниль: в основной руке оружие из профилей или пусто, или щит во второй. */
    public static boolean handles(Player player) {
        return of(player, player.getMainHandItem()) != null || hasShield(player);
    }

    /** Щит во второй руке. */
    public static boolean hasShield(LivingEntity entity) {
        return entity.getOffhandItem().is(SHIELDS);
    }

    /** Профиль класса (щит, кулак) или null. */
    @Nullable
    public static WeaponProfile ofClass(LivingEntity player, WeaponClass weaponClass) {
        return (player.level().isClientSide ? client : server).classes().get(weaponClass);
    }

    /** Только для клиента: набор с сервера. */
    public static void applyClient(WeaponProfilesSyncPayload payload) {
        client = build(payload.classes(), payload.items());
    }

    // ---------------------------------------------------------------- разбор

    /** Разобрать набор из исходного JSON: каждая строка classes — один класс, items — файл предметов. */
    static Set build(List<String> classJson, String itemsJson) {
        Map<WeaponClass, WeaponProfile> classes = new EnumMap<>(WeaponClass.class);
        for (String text : classJson) {
            try {
                WeaponProfile profile = WeaponProfile.parse(JsonParser.parseString(text).getAsJsonObject());
                classes.put(profile.weaponClass(), profile);
            } catch (RuntimeException e) {
                HalgventMod.LOGGER.warn("Профиль оружия не прочитан: {}", e.getMessage());
            }
        }
        Map<Item, WeaponProfile> items = new HashMap<>();
        List<Map.Entry<TagKey<Item>, WeaponProfile>> tags = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(itemsJson).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(root, "items", new JsonObject()).entrySet()) {
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(entry.getKey()));
                WeaponProfile profile = entryProfile(classes, entry.getKey(), entry.getValue());
                if (item.isPresent() && profile != null) {
                    items.put(item.get(), profile);
                }
            }
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(root, "tags", new JsonObject()).entrySet()) {
                WeaponProfile profile = entryProfile(classes, entry.getKey(), entry.getValue());
                if (profile != null) {
                    tags.add(Map.entry(TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.getKey())), profile));
                }
            }
        } catch (RuntimeException e) {
            HalgventMod.LOGGER.warn("{} не прочитан: {}", ITEMS_FILE, e.getMessage());
        }
        return new Set(Map.copyOf(classes), Map.copyOf(items), List.copyOf(tags));
    }

    // "sword" или { "class": "sword", ...переопределения }
    @Nullable
    private static WeaponProfile entryProfile(Map<WeaponClass, WeaponProfile> classes, String key, JsonElement value) {
        try {
            JsonObject overrides = value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
            String classId = value.isJsonObject() ? GsonHelper.getAsString(overrides, "class") : value.getAsString();
            WeaponClass weaponClass = WeaponClass.byId(classId);
            WeaponProfile profile = weaponClass == null ? null : classes.get(weaponClass);
            if (profile == null) {
                HalgventMod.LOGGER.warn("{}: «{}» — нет профиля класса «{}»", ITEMS_FILE, key, classId);
                return null;
            }
            return value.isJsonObject() ? profile.forItem(overrides) : profile;
        } catch (RuntimeException e) {
            HalgventMod.LOGGER.warn("{}: «{}» — {}", ITEMS_FILE, key, e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- загрузка и рассылка

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    // При входе — одному, по /reload — всем
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, sync));
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(GSON, CLASS_DIR);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<String> classes = new ArrayList<>();
            files.values().forEach(json -> classes.add(GSON.toJson(json)));
            String items = "{}";
            Optional<Resource> resource = manager.getResource(ITEMS_FILE);
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    items = GSON.toJson(JsonParser.parseReader(reader));
                } catch (Exception e) {
                    HalgventMod.LOGGER.warn("{} не прочитан: {}", ITEMS_FILE, e.getMessage());
                }
            }
            server = build(classes, items);
            sync = new WeaponProfilesSyncPayload(List.copyOf(classes), items);
            HalgventMod.LOGGER.info("Профилей оружия: {}, предметов: {}, тегов: {}",
                    server.classes().size(), server.items().size(), server.tags().size());
        }
    }
}
