package com.nrdhvnstd.halgventmod.client.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.map.LocationMapData;
import com.nrdhvnstd.halgventmod.network.LocationMapRequestPayload;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;

/**
 * Снимки карт локаций на клиенте (LocationMaps на сервере). Кэш — те же файлы, что на сервере:
 * .minecraft/halgventmod/map/<мир>/locations/<id>.bin; есть файл нужной версии — не качать.
 * Клиент просит снимки сам: при входе и после каждой рассылки точек — для открытых мест, у которых версия на сервере
 * новее кэша (администратор — для всех мест). Пришедший файл разбирается в фоне и заливается в карту (WorldMap.fill).
 * Какие версии уже залиты в карту измерения — <папка карты>/locations.json: так снимок дозальётся, если игра упала
 * до записи регионов или файл пришёл, пока игрок был в другом измерении.
 */
public final class LocationMapCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Type APPLIED_TYPE = new TypeToken<Map<Integer, Integer>>() {}.getType();
    private static final String FOLDER = "locations";
    private static final String APPLIED_FILE = "locations.json";
    private static final String EXTENSION = ".bin";

    /** Версия снимка в кэше по номеру точки. */
    private static final Map<Integer, Integer> CACHED = new HashMap<>();
    /** Уже попрошенная версия — не просить её снова, пока не пришла. */
    private static final Map<Integer, Integer> REQUESTED = new HashMap<>();
    /** Залитая в карту этого измерения версия. */
    private static final Map<Integer, Integer> APPLIED = new HashMap<>();
    private static final Set<Integer> FILLING = new HashSet<>();
    private static final Map<Integer, Parts> PARTS = new HashMap<>();
    /** Ход снимка у администратора: номер точки → проценты. */
    private static final Map<Integer, Integer> PROGRESS = new HashMap<>();

    @Nullable
    private static Path cacheDirectory;
    @Nullable
    private static Path mapDirectory;

    private LocationMapCache() {}

    /** Куски одного файла. */
    private static final class Parts {
        final int version;
        final byte[][] pieces;
        int received;

        Parts(int version, int total) {
            this.version = version;
            this.pieces = new byte[total][];
        }
    }

    // ===== Смена мира =====

    /** Смена мира или измерения (из WorldMap): папка карты измерения или null. */
    static void switchTo(@Nullable Path directory) {
        CACHED.clear();
        REQUESTED.clear();
        APPLIED.clear();
        FILLING.clear();
        PARTS.clear();
        PROGRESS.clear();
        mapDirectory = directory;
        cacheDirectory = directory == null ? null : directory.getParent().resolve(FOLDER);
        if (directory == null) {
            return;
        }
        if (Files.isDirectory(cacheDirectory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(cacheDirectory, "*" + EXTENSION)) {
                for (Path file : files) {
                    int[] header = LocationMapData.readHeader(file);
                    if (header != null) {
                        CACHED.put(header[0], header[1]);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Не удалось прочитать кэш снимков локаций {}", cacheDirectory, e);
            }
        }
        Path applied = directory.resolve(APPLIED_FILE);
        if (Files.isRegularFile(applied)) {
            try (Reader reader = Files.newBufferedReader(applied)) {
                Map<Integer, Integer> loaded = GSON.fromJson(reader, APPLIED_TYPE);
                if (loaded != null) {
                    APPLIED.putAll(loaded);
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("Не удалось прочитать {}", applied, e);
            }
        }
        pointsChanged();
    }

    // ===== Запросы =====

    /** Точки или открытые места пришли заново: попросить новые снимки, дозалить незалитые. */
    public static void pointsChanged() {
        Minecraft minecraft = Minecraft.getInstance();
        if (cacheDirectory == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        boolean admin = Permissions.isAdmin(minecraft.player);
        String dimension = minecraft.level.dimension().location().toString();
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.isSpawn() || point.mapVersion <= 0) {
                continue;
            }
            int cached = CACHED.getOrDefault(point.id, 0);
            boolean allowed = admin || MapPoints.clientDiscovered.contains(point.id);
            if (allowed && point.mapVersion > cached && REQUESTED.getOrDefault(point.id, 0) < point.mapVersion) {
                REQUESTED.put(point.id, point.mapVersion);
                PacketDistributor.sendToServer(new LocationMapRequestPayload(point.id, cached));
            }
            if (cached > 0 && point.dimension.equals(dimension) && APPLIED.getOrDefault(point.id, 0) < cached
                    && !FILLING.contains(point.id)) {
                fillFromCache(point, cached);
            }
        }
    }

    /** Кусок файла от сервера; собран целиком — в кэш и в карту. */
    public static void receive(int id, int version, int part, int total, byte[] bytes) {
        if (cacheDirectory == null || total <= 0 || part < 0 || part >= total) {
            return;
        }
        Parts parts = PARTS.get(id);
        if (parts == null || parts.version != version || parts.pieces.length != total) {
            parts = new Parts(version, total);
            PARTS.put(id, parts);
        }
        if (parts.pieces[part] == null) {
            parts.pieces[part] = bytes;
            parts.received++;
        }
        if (parts.received < total) {
            return;
        }
        PARTS.remove(id);
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        for (byte[] piece : parts.pieces) {
            whole.writeBytes(piece);
        }
        byte[] file = whole.toByteArray();
        Path target = cacheDirectory.resolve(id + EXTENSION);
        int generation = MapWorker.generation();
        FILLING.add(id);
        MapWorker.submit(() -> {
            LocationMapData data;
            try {
                data = LocationMapData.read(file);
                write(target, file);
            } catch (IOException e) {
                LOGGER.warn("Снимок карты локации #{} не принят", id, e);
                MapWorker.toMain(generation, () -> {
                    FILLING.remove(id);
                    REQUESTED.remove(id);
                });
                return;
            }
            MapWorker.toMain(generation, () -> {
                CACHED.put(id, data.mapVersion);
                fill(data);
            });
        });
    }

    private static void fillFromCache(MapPoint point, int version) {
        Path file = cacheDirectory.resolve(point.id + EXTENSION);
        int generation = MapWorker.generation();
        FILLING.add(point.id);
        MapWorker.submit(() -> {
            LocationMapData data;
            try {
                data = LocationMapData.read(file);
            } catch (IOException e) {
                LOGGER.warn("Снимок карты локации {} не читается — попросим заново", file, e);
                MapWorker.toMain(generation, () -> {
                    FILLING.remove(point.id);
                    CACHED.remove(point.id);
                    pointsChanged();
                });
                return;
            }
            MapWorker.toMain(generation, () -> fill(data));
        });
    }

    // Залить в карту, если место в этом измерении; потом отметить залитым
    private static void fill(LocationMapData data) {
        MapPoint point = find(data.pointId);
        Minecraft minecraft = Minecraft.getInstance();
        if (point == null || minecraft.level == null
                || !point.dimension.equals(minecraft.level.dimension().location().toString())) {
            FILLING.remove(data.pointId);
            return;
        }
        WorldMap.fill(data, point.copy(), () -> {
            FILLING.remove(data.pointId);
            APPLIED.put(data.pointId, data.mapVersion);
            saveApplied();
        });
    }

    @Nullable
    private static MapPoint find(int id) {
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.id == id) {
                return point;
            }
        }
        return null;
    }

    /** Есть ли у клиента снимок этого места (для окна локации: изведана ли карта). */
    public static boolean has(int id) {
        return CACHED.getOrDefault(id, 0) > 0;
    }

    // ===== Ход снимка (администрация) =====

    public static void captureProgress(int id, int percent) {
        if (percent >= 100) {
            PROGRESS.remove(id);
        } else {
            PROGRESS.put(id, percent);
        }
    }

    /** Проценты идущего снимка; -1 — не снимается. */
    public static int progress(int id) {
        return PROGRESS.getOrDefault(id, -1);
    }

    // ===== Файлы =====

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, bytes);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void saveApplied() {
        if (mapDirectory == null) {
            return;
        }
        Path file = mapDirectory.resolve(APPLIED_FILE);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(APPLIED, APPLIED_TYPE, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Не удалось сохранить {}", file, e);
        }
    }
}
