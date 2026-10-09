package com.nrdhvnstd.halgventmod.client.map;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * Надписи текущего мира и измерения. Хранятся рядом с картой: <папка карты>/texts.json,
 * записываются сразу после каждого изменения.
 */
public final class MapTexts {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<MapText>>() {}.getType();
    private static final String FILE = "texts.json";

    private static final List<MapText> LIST = new ArrayList<>();
    private static Path file;

    private MapTexts() {}

    public static List<MapText> all() {
        return Collections.unmodifiableList(LIST);
    }

    public static boolean available() {
        return file != null;
    }

    public static void add(MapText text) {
        LIST.add(text);
        save();
    }

    public static void remove(MapText text) {
        LIST.remove(text);
        save();
    }

    /** После правки полей надписи. */
    public static void changed() {
        save();
    }

    /** Смена мира или измерения (из WorldMap): папка карты или null. */
    static void switchTo(Path mapDirectory) {
        LIST.clear();
        file = mapDirectory == null ? null : mapDirectory.resolve(FILE);
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            List<MapText> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) {
                loaded.stream().filter(text -> text != null && text.text != null && !text.text.isBlank()).forEach(LIST::add);
                // Надписи старого формата (размер 1–8) — в масштаб; сразу и записать
                boolean upgraded = false;
                for (MapText text : LIST) {
                    upgraded |= text.upgrade();
                }
                if (upgraded) {
                    save();
                }
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Не удалось прочитать надписи карты {}", file, e);
        }
    }

    private static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(LIST, LIST_TYPE, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Не удалось сохранить надписи карты {}", file, e);
        }
    }
}
