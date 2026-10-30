package com.nrdhvnstd.halgventmod.combat.anim;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;

/**
 * Анимации боя по имени: файлы assets/halgventmod/combat_anim/*.json, в каждом — сколько угодно анимаций
 * («animations» → имя → анимация). Общие для сервера и клиента: по ним клиент рисует удар (client/anim/CombatClips),
 * а сервер ведёт клинок (BladePose) — что видно, то и бьёт. Сервер читает их прямо из файла мода (у него нет
 * ресурсов), клиент — из ресурсов, с ресурс-паками (перечитываются по F3+T).
 * Файлы ударов, блоков и стоек — из Kingdom Come Combat (MIT, docs/licenses/KingdomComeCombat-LICENSE.txt).
 */
public final class AnimLibrary {
    public static final String FOLDER = "combat_anim";
    private static Map<String, AnimClip> clips;

    private AnimLibrary() {}

    @Nullable
    public static AnimClip get(String name) {
        return clips().get(name);
    }

    /** Первая из имён, что есть. */
    @Nullable
    public static AnimClip first(String... names) {
        for (String name : names) {
            AnimClip clip = clips().get(name);
            if (clip != null) {
                return clip;
            }
        }
        return null;
    }

    private static synchronized Map<String, AnimClip> clips() {
        if (clips == null) {
            clips = fromModFile();
        }
        return clips;
    }

    // Из файла мода — сервер и клиент до загрузки ресурсов
    private static Map<String, AnimClip> fromModFile() {
        Map<String, AnimClip> loaded = new HashMap<>();
        try {
            Path folder = ModList.get().getModFileById(HalgventMod.MODID).getFile()
                    .findResource("assets", HalgventMod.MODID, FOLDER);
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                    try (Reader reader = Files.newBufferedReader(file)) {
                        read(file.toString(), reader, loaded);
                    }
                }
            }
        } catch (Exception e) {
            HalgventMod.LOGGER.warn("Анимации боя не прочитаны из файла мода: {}", e.toString());
        }
        HalgventMod.LOGGER.info("Анимаций боя: {}", loaded.size());
        return loaded;
    }

    /** Клиент: из ресурсов (с ресурс-паками). */
    public static synchronized void reload(ResourceManager manager) {
        Map<String, AnimClip> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> file
                : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                read(file.getKey().toString(), reader, loaded);
            } catch (Exception e) {
                HalgventMod.LOGGER.warn("Анимация боя {} не прочитана: {}", file.getKey(), e.toString());
            }
        }
        if (!loaded.isEmpty()) {
            clips = loaded;
        }
    }

    private static void read(String source, Reader reader, Map<String, AnimClip> into) {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("animations")) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations").entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    into.put(entry.getKey(), AnimClip.parse(entry.getKey(), entry.getValue().getAsJsonObject()));
                }
            }
        } catch (RuntimeException e) {
            HalgventMod.LOGGER.warn("Анимация боя {} не прочитана: {}", source, e.toString());
        }
    }
}
