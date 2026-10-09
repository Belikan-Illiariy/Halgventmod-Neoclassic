package com.nrdhvnstd.halgventmod.client.map;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import org.slf4j.Logger;

/**
 * Пометки игрока на карте: прозрачный слой поверх неё, по пикселю на блок, кусками как регионы карты.
 * Штрих — от нажатия до отпускания кнопки; его можно отменить (последние UNDO_LIMIT штрихов).
 * Хранится рядом с картой: <папка карты>/<rx>_<rz>.draw.png, записывается после каждого штриха.
 */
public final class MapDrawing {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EXTENSION = ".draw.png";
    private static final int UNDO_LIMIT = 50;

    private static final class Layer {
        final NativeImage image;
        final DynamicTexture texture;
        final ResourceLocation id;
        boolean needsUpload;
        boolean needsSave;

        Layer(NativeImage image) {
            this.image = image;
            this.texture = new DynamicTexture(image);
            this.id = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "map/drawing_" + nextId++);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    private static int nextId;
    private static Path directory;
    private static final Map<Long, Layer> LAYERS = new HashMap<>();
    private static final Set<Long> MISSING = new HashSet<>();
    // Регионы, где есть пометки (файл или слой в памяти): окно карты рисует только их, а не все регионы на экране
    private static final Set<Long> WITH_DRAWINGS = new HashSet<>();
    /** Штрихи для отмены: блок (ключ из x и z) → каким пиксель был до штриха (ABGR). */
    private static final Deque<Map<Long, Integer>> UNDO = new ArrayDeque<>();
    private static Map<Long, Integer> stroke;

    private MapDrawing() {}

    /** Смена мира или измерения (из WorldMap): папка карты или null. */
    static void switchTo(Path mapDirectory) {
        endStroke();
        LAYERS.values().forEach(Layer::close);
        LAYERS.clear();
        MISSING.clear();
        UNDO.clear();
        WITH_DRAWINGS.clear();
        directory = mapDirectory;
        if (directory != null && Files.isDirectory(directory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    String[] parts = name.substring(0, name.length() - EXTENSION.length()).split("_");
                    if (parts.length == 2) {
                        WITH_DRAWINGS.add(ChunkPos.asLong(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                    }
                }
            } catch (IOException | NumberFormatException e) {
                LOGGER.warn("Не удалось просмотреть пометки карты в {}", directory, e);
            }
        }
    }

    public static boolean available() {
        return directory != null;
    }

    /** Регионы, где есть пометки. */
    public static Set<Long> regionsWithDrawings() {
        return WITH_DRAWINGS;
    }

    /** Текстура пометок региона; null — там ничего не нарисовано. */
    public static ResourceLocation texture(int rx, int rz) {
        Layer layer = layer(rx, rz, false);
        if (layer == null) {
            return null;
        }
        if (layer.needsUpload) {
            layer.texture.upload();
            layer.needsUpload = false;
        }
        return layer.id;
    }

    // ===== Рисование =====

    public static void beginStroke() {
        endStroke();
        if (available()) {
            stroke = new LinkedHashMap<>();
        }
    }

    /** Конец штриха: в историю отмены и на диск. */
    public static void endStroke() {
        if (stroke == null) {
            return;
        }
        if (!stroke.isEmpty()) {
            UNDO.push(stroke);
            while (UNDO.size() > UNDO_LIMIT) {
                UNDO.removeLast();
            }
        }
        stroke = null;
        saveDirty();
    }

    /**
     * Линия кистью диаметром size блоков от одной точки мира до другой.
     * color — ARGB; 0 — стирать.
     */
    public static void line(double fromX, double fromZ, double toX, double toZ, int size, int color) {
        if (stroke == null) {
            return;
        }
        double length = Math.sqrt((toX - fromX) * (toX - fromX) + (toZ - fromZ) * (toZ - fromZ));
        double step = Math.max(0.25, size / 4.0);
        int steps = Math.max(1, (int) Math.ceil(length / step));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            dab(fromX + (toX - fromX) * t, fromZ + (toZ - fromZ) * t, size, color);
        }
    }

    // Круглое пятно: блоки, чья середина ближе радиуса; хотя бы блок под точкой
    private static void dab(double x, double z, int size, int color) {
        double radius = size / 2.0;
        int fromX = Mth.floor(x - radius);
        int toX = Mth.floor(x + radius);
        int fromZ = Mth.floor(z - radius);
        int toZ = Mth.floor(z + radius);
        for (int bz = fromZ; bz <= toZ; bz++) {
            for (int bx = fromX; bx <= toX; bx++) {
                double dx = bx + 0.5 - x;
                double dz = bz + 0.5 - z;
                if (dx * dx + dz * dz <= radius * radius) {
                    put(bx, bz, color);
                }
            }
        }
        put(Mth.floor(x), Mth.floor(z), color);
    }

    private static void put(int x, int z, int color) {
        int abgr = color == 0 ? 0 : FastColor.ABGR32.fromArgb32(color);
        Layer layer = layer(x >> MapRegion.SHIFT, z >> MapRegion.SHIFT, color != 0);
        if (layer == null) {
            return;
        }
        int lx = x & (MapRegion.SIZE - 1);
        int lz = z & (MapRegion.SIZE - 1);
        int old = layer.image.getPixelRGBA(lx, lz);
        if (old == abgr) {
            return;
        }
        stroke.putIfAbsent(blockKey(x, z), old);
        layer.image.setPixelRGBA(lx, lz, abgr);
        layer.needsUpload = true;
        layer.needsSave = true;
    }

    /** Отменить последний штрих. false — нечего отменять. */
    public static boolean undo() {
        endStroke();
        Map<Long, Integer> last = UNDO.poll();
        if (last == null) {
            return false;
        }
        last.forEach((key, abgr) -> {
            int x = (int) (key >> 32);
            int z = (int) (long) key;
            Layer layer = layer(x >> MapRegion.SHIFT, z >> MapRegion.SHIFT, true);
            if (layer != null) {
                layer.image.setPixelRGBA(x & (MapRegion.SIZE - 1), z & (MapRegion.SIZE - 1), abgr);
                layer.needsUpload = true;
                layer.needsSave = true;
            }
        });
        saveDirty();
        return true;
    }

    /** Стереть все пометки этого мира и измерения — и с диска. Не отменяется. */
    public static void clearAll() {
        stroke = null;
        UNDO.clear();
        LAYERS.values().forEach(Layer::close);
        LAYERS.clear();
        MISSING.clear();
        WITH_DRAWINGS.clear();
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            LOGGER.warn("Не удалось удалить пометки карты в {}", directory, e);
        }
    }

    private static long blockKey(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    // ===== Слои: память и диск =====

    private static Layer layer(int rx, int rz, boolean create) {
        if (directory == null) {
            return null;
        }
        long key = ChunkPos.asLong(rx, rz);
        Layer layer = LAYERS.get(key);
        if (layer != null) {
            return layer;
        }
        if (!MISSING.contains(key) && WITH_DRAWINGS.contains(key)) {
            layer = load(file(key));
            if (layer != null) {
                LAYERS.put(key, layer);
                return layer;
            }
            MISSING.add(key);
        }
        if (!create) {
            return null;
        }
        MISSING.remove(key);
        layer = new Layer(new NativeImage(MapRegion.SIZE, MapRegion.SIZE, true));
        LAYERS.put(key, layer);
        WITH_DRAWINGS.add(key);
        return layer;
    }

    private static Path file(long key) {
        return directory.resolve(ChunkPos.getX(key) + "_" + ChunkPos.getZ(key) + EXTENSION);
    }

    private static Layer load(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(input);
            if (image.getWidth() != MapRegion.SIZE || image.getHeight() != MapRegion.SIZE
                    || image.format() != NativeImage.Format.RGBA) {
                image.close();
                return null;
            }
            return new Layer(image);
        } catch (IOException e) {
            LOGGER.warn("Не удалось прочитать пометки карты {}", file, e);
            return null;
        }
    }

    // Запись — в фоновом потоке, с копии картинки
    private static void saveDirty() {
        if (directory == null) {
            return;
        }
        Path dir = directory;
        LAYERS.forEach((key, layer) -> {
            if (!layer.needsSave) {
                return;
            }
            layer.needsSave = false;
            NativeImage copy = new NativeImage(MapRegion.SIZE, MapRegion.SIZE, false);
            copy.copyFrom(layer.image);
            Path file = dir.resolve(ChunkPos.getX(key) + "_" + ChunkPos.getZ(key) + EXTENSION);
            Util.ioPool().execute(() -> {
                try (copy) {
                    Files.createDirectories(dir);
                    copy.writeToFile(file);
                } catch (IOException e) {
                    LOGGER.warn("Не удалось сохранить пометки карты {}", file, e);
                }
            });
        });
    }
}
