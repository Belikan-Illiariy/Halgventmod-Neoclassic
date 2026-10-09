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
 * Метки текущего мира и измерения. Хранятся рядом с картой: <папка карты>/waypoints.json,
 * записываются сразу после каждого изменения.
 */
public final class Waypoints {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {}.getType();
    private static final String FILE = "waypoints.json";

    private static final List<Waypoint> LIST = new ArrayList<>();
    private static Path file;

    private Waypoints() {}

    public static List<Waypoint> all() {
        return Collections.unmodifiableList(LIST);
    }

    /** Есть ли куда сохранять: игрок в мире. */
    public static boolean available() {
        return file != null;
    }

    public static void add(Waypoint waypoint) {
        LIST.add(waypoint);
        save();
    }

    public static void remove(Waypoint waypoint) {
        LIST.remove(waypoint);
        save();
    }

    /** После правки полей метки. */
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
            List<Waypoint> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) {
                loaded.stream().filter(waypoint -> waypoint != null && waypoint.name != null).forEach(LIST::add);
                // Метки старого формата — во флаги; сразу и записать
                boolean upgraded = false;
                for (Waypoint waypoint : LIST) {
                    upgraded |= waypoint.upgrade();
                }
                if (upgraded) {
                    save();
                }
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Не удалось прочитать метки {}", file, e);
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
            LOGGER.warn("Не удалось сохранить метки {}", file, e);
        }
    }
}
