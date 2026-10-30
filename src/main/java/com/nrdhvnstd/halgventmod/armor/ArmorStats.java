package com.nrdhvnstd.halgventmod.armor;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.ArmorStatsSyncPayload;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.quality.GearQuality;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Параметры вещи брони для боя (ТЗ боя, 7.3): КБ (покрытие), твёрдость 0–3, износ и сопротивления пяти типам урона.
 * Своя вещь — data/&lt;пакет&gt;/stats/armor/&lt;пространство&gt;/&lt;вещь&gt;.json:
 * <pre>
 * { "coverage": 30, "hardness": 2.0, "durability_loss": 0.4,
 *   "resist": { "chopping": 0.5, "cutting": 0.8, "piercing": 0.3, "blunt": 0.1, "penetrating": 0.4 } }
 * </pre>
 * Поля, которых нет, — по умолчанию; файла нет — всё по умолчанию: КБ — по слоту, остальное — по материалу (ArmorKind).
 * Качество вещи умножает КБ и сопротивления. Прочность: ниже 50 % защита падает пропорционально, на нуле вещь разбита —
 * остаётся надетой, но не защищает (ремонта пока нет).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ArmorStats {
    public static final String DIR = "stats/armor";
    public static final int DURABILITY = 100;          // прочность всей брони
    public static final float WORN_FROM = 0.5F;        // ниже этой доли прочности защита падает
    public static final float MAX_RESIST = 0.9F;
    private static final Gson GSON = new Gson();

    /** Параметры вещи: coverage — КБ, resist — по DamageKind.ordinal(). */
    public record Stats(float coverage, float hardness, float durabilityLoss, float[] resist) {
        public float resist(DamageKind kind) {
            return resist[kind.ordinal()];
        }

        Stats scaled(float coverageFactor, float resistFactor, float hardnessFactor) {
            float[] scaled = new float[resist.length];
            for (int i = 0; i < resist.length; i++) {
                scaled[i] = Math.min(MAX_RESIST, resist[i] * resistFactor);
            }
            return new Stats(coverage * coverageFactor, hardness * hardnessFactor, durabilityLoss, scaled);
        }
    }

    // КБ по слоту ⚠️: одна вещь своего слоя почти закрывает зону (нормы слоёв — ArmorLayers)
    private static final Map<ArmorSlot, Float> SLOT_COVERAGE = new EnumMap<>(Map.ofEntries(
            Map.entry(ArmorSlot.CLOAK, 7.0F), Map.entry(ArmorSlot.HELMET, 100.0F), Map.entry(ArmorSlot.COIF, 47.0F),
            Map.entry(ArmorSlot.ARMING_CAP, 40.0F), Map.entry(ArmorSlot.GLOVES, 10.0F), Map.entry(ArmorSlot.BREASTPLATE, 100.0F),
            Map.entry(ArmorSlot.MAIL, 47.0F), Map.entry(ArmorSlot.PADDING, 40.0F), Map.entry(ArmorSlot.BOOTS, 10.0F),
            Map.entry(ArmorSlot.GREAVES, 95.0F), Map.entry(ArmorSlot.CHAUSSES, 47.0F), Map.entry(ArmorSlot.TROUSERS, 35.0F)));

    // По материалу ⚠️: твёрдость, износ, сопротивления (рубящий, режущий, колющий, дробящий, проникающий)
    private static final Map<ArmorKind, Stats> KIND_DEFAULTS = new EnumMap<>(Map.of(
            ArmorKind.PLATE,   new Stats(0, 2.5F, 0.4F, new float[] {0.70F, 0.85F, 0.45F, 0.20F, 0.50F}),
            ArmorKind.MAIL,    new Stats(0, 1.5F, 0.6F, new float[] {0.45F, 0.80F, 0.25F, 0.10F, 0.30F}),
            ArmorKind.PADDED,  new Stats(0, 0.0F, 1.0F, new float[] {0.25F, 0.35F, 0.20F, 0.35F, 0.25F}),
            ArmorKind.LEATHER, new Stats(0, 0.5F, 0.8F, new float[] {0.20F, 0.30F, 0.15F, 0.15F, 0.15F})));
    // Накидка — ткань поверх всего: почти ничего не держит
    private static final Stats CLOAK_DEFAULTS = new Stats(0, 0.0F, 1.0F, new float[] {0.05F, 0.10F, 0.05F, 0.05F, 0.05F});

    private static Map<ResourceLocation, JsonObject> overrides = Map.of();
    private static int generation;

    /** Растёт при каждой загрузке: по нему сбрасываются сводки слоёв (ArmorLayers). */
    public static int generation() {
        return generation;
    }

    private ArmorStats() {}

    /** Параметры вещи с качеством и износом; null — не броня или разбита. */
    @Nullable
    public static Stats of(ItemStack stack) {
        ModArmor.ArmorInfo info = ModArmor.get(stack);
        if (info == null || broken(stack)) {
            return null;
        }
        Stats base = base(info, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        float condition = condition(stack);
        GearQuality.Grade grade = GearQuality.get(stack);
        // Твёрдость — от качества, но не от износа: помятые латы пропускают чаще, но мягче не становятся
        return base.scaled(grade.protection() * condition, grade.resistance() * condition, grade.protection());
    }

    /** Без качества и износа: из JSON или по умолчанию. */
    public static Stats base(ModArmor.ArmorInfo info, ResourceLocation id) {
        Stats defaults = info.slot() == ArmorSlot.CLOAK ? CLOAK_DEFAULTS : KIND_DEFAULTS.get(info.kind());
        float coverage = SLOT_COVERAGE.get(info.slot());
        JsonObject json = overrides.get(id);
        if (json == null) {
            return new Stats(coverage, defaults.hardness(), defaults.durabilityLoss(), defaults.resist());
        }
        float[] resist = defaults.resist().clone();
        JsonObject resistJson = GsonHelper.getAsJsonObject(json, "resist", new JsonObject());
        for (DamageKind kind : DamageKind.values()) {
            resist[kind.ordinal()] = Math.min(MAX_RESIST, GsonHelper.getAsFloat(resistJson, kind.id(), resist[kind.ordinal()]));
        }
        return new Stats(GsonHelper.getAsFloat(json, "coverage", coverage),
                GsonHelper.getAsFloat(json, "hardness", defaults.hardness()),
                GsonHelper.getAsFloat(json, "durability_loss", defaults.durabilityLoss()), resist);
    }

    /** Доля защиты по износу: до 50 % прочности — 1, ниже — пропорционально, разбита — 0. */
    public static float condition(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return 1.0F;
        }
        float left = 1.0F - (float) stack.getDamageValue() / stack.getMaxDamage();
        return left >= WORN_FROM ? 1.0F : Math.max(0, left / WORN_FROM);
    }

    /** Вещь разбита: прочность на нуле. Остаётся надетой, но не защищает. */
    public static boolean broken(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage();
    }

    // ---------------------------------------------------------------- загрузка

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(GSON, DIR);
        }

        // Путь stats/armor/<пространство>/<вещь>.json → id <пространство>:<вещь>
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> loaded = new HashMap<>();
            files.forEach((file, json) -> {
                int slash = file.getPath().indexOf('/');
                if (slash <= 0 || !json.isJsonObject()) {
                    HalgventMod.LOGGER.warn("Параметры брони {}: ожидался объект в {}/<пространство>/<вещь>.json", file, DIR);
                    return;
                }
                loaded.put(ResourceLocation.fromNamespaceAndPath(file.getPath().substring(0, slash),
                        file.getPath().substring(slash + 1)), json.getAsJsonObject());
            });
            overrides = Map.copyOf(loaded);
            generation++;
            Map<String, String> sync = new HashMap<>();
            loaded.forEach((id, json) -> sync.put(id.toString(), GSON.toJson(json)));
            syncPayload = new ArmorStatsSyncPayload(Map.copyOf(sync));
            HalgventMod.LOGGER.info("Параметров брони из файлов: {}", overrides.size());
        }
    }

    // Клиенту — для подсказки (на сервере без них клиент видел бы только значения по материалу)
    private static ArmorStatsSyncPayload syncPayload = new ArmorStatsSyncPayload(Map.of());

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, syncPayload));
    }

    /** Только клиент: параметры с сервера. */
    public static void applyClient(Map<String, String> items) {
        Map<ResourceLocation, JsonObject> loaded = new HashMap<>();
        items.forEach((id, json) -> {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null) {
                try {
                    loaded.put(location, JsonParser.parseString(json).getAsJsonObject());
                } catch (RuntimeException e) {
                    HalgventMod.LOGGER.warn("Параметры брони {} с сервера не прочитаны: {}", id, e.getMessage());
                }
            }
        });
        overrides = Map.copyOf(loaded);
        generation++;
    }
}
