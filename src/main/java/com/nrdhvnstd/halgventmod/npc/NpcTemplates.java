package com.nrdhvnstd.halgventmod.npc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.character.Appearance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Шаблоны НПС — данные датапака: data/&lt;пространство&gt;/npc/templates/&lt;id&gt;.json. Сервер читает их при запуске
 * и /reload; клиенту они не нужны (личность приходит с самим НПС).
 * <pre>
 * {
 *   "role": "guard",                                  // citizen | merchant | guard
 *   "weight": 1,                                      // вес при случайном выборе среди шаблонов роли
 *   "names": { "male": ["Ганс"], "female": ["Берта"], "surnames": ["Рыжий"] },
 *   "skins": ["halgventmod:guard_1"],                 // необязательно: готовые скины; нет — облик из частей
 *   "appearance": { "sex": ["male"], "age": ["young", "mature"] }   // допустимые варианты частей облика
 * }
 * </pre>
 * Ошибка в файле — предупреждение в лог, шаблон пропускается.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class NpcTemplates {
    public static final String DIR = "npc/templates";
    private static final Gson GSON = new Gson();

    private static Map<ResourceLocation, NpcTemplate> all = Map.of();

    private NpcTemplates() {}

    @Nullable
    public static NpcTemplate get(ResourceLocation id) {
        return all.get(id);
    }

    public static Collection<ResourceLocation> ids() {
        return all.keySet();
    }

    /** Случайный шаблон роли с учётом веса; null — у роли нет шаблонов. */
    @Nullable
    public static NpcTemplate random(NpcRole role, RandomSource random) {
        List<NpcTemplate> candidates = all.values().stream().filter(template -> template.role() == role).toList();
        int total = candidates.stream().mapToInt(NpcTemplate::weight).sum();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (NpcTemplate template : candidates) {
            roll -= template.weight();
            if (roll < 0) {
                return template;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(GSON, DIR);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, NpcTemplate> loaded = new LinkedHashMap<>();
            files.forEach((id, json) -> {
                try {
                    loaded.put(id, parse(id, GsonHelper.convertToJsonObject(json, "шаблон")));
                } catch (RuntimeException e) {
                    HalgventMod.LOGGER.warn("Шаблон НПС {} не прочитан: {}", id, e.getMessage());
                }
            });
            all = Map.copyOf(loaded);
            HalgventMod.LOGGER.info("Шаблонов НПС: {}", all.size());
        }
    }

    private static NpcTemplate parse(ResourceLocation id, JsonObject json) {
        String roleId = GsonHelper.getAsString(json, "role");
        NpcRole role = NpcRole.byId(roleId);
        if (role == null) {
            throw new IllegalArgumentException("неизвестная роль «" + roleId + "»");
        }
        JsonObject names = GsonHelper.getAsJsonObject(json, "names", new JsonObject());
        Map<String, List<String>> appearance = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, "appearance", new JsonObject()).entrySet()) {
            Appearance.Part part = Appearance.Part.byId(entry.getKey());
            if (part == null) {
                throw new IllegalArgumentException("неизвестная часть облика «" + entry.getKey() + "»");
            }
            List<String> variants = strings(entry.getValue().getAsJsonArray());
            for (String variant : variants) {
                if (!part.options().contains(variant)) {
                    throw new IllegalArgumentException("у части «" + part.id() + "» нет варианта «" + variant + "»");
                }
            }
            appearance.put(part.id(), variants);
        }
        List<String> skins = strings(GsonHelper.getAsJsonArray(json, "skins", new JsonArray()));
        for (String skin : skins) {
            if (ResourceLocation.tryParse(skin) == null) {
                throw new IllegalArgumentException("неверный скин «" + skin + "»");
            }
        }
        return new NpcTemplate(id, role, Math.max(0, GsonHelper.getAsInt(json, "weight", 1)),
                strings(GsonHelper.getAsJsonArray(names, "male", new JsonArray())),
                strings(GsonHelper.getAsJsonArray(names, "female", new JsonArray())),
                strings(GsonHelper.getAsJsonArray(names, "surnames", new JsonArray())),
                skins, Map.copyOf(appearance));
    }

    private static List<String> strings(JsonArray array) {
        List<String> list = new ArrayList<>();
        for (JsonElement element : array) {
            list.add(element.getAsString());
        }
        return List.copyOf(list);
    }
}
