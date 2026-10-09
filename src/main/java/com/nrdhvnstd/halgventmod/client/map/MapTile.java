package com.nrdhvnstd.halgventmod.client.map;

import java.util.Arrays;

import com.mojang.blaze3d.platform.NativeImage;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * Кусок карты 512 × 512 пикселей на уровне отдаления level: на уровне 0 пиксель — блок (MapRegion),
 * на уровне L — квадрат 2^L × 2^L блоков, среднее по ним (MapLod, строит MapPyramid).
 * Хранит «сырые» данные: цвет без тени (в альфе — вид пикселя, см. MapStyle.ALPHA_*), высоту и биомы
 * клетками 4 × 4 пикселя. Картинку по стилю (MapStyle) собирает фоновый поток (MapWorker) со снимка данных;
 * главный поток только заливает её в видеопамять — не больше UPLOAD_PIXELS_PER_FRAME за кадр.
 */
abstract class MapTile {
    static final int SIZE = 512;
    static final int SHIFT = 9;       // пиксель → тайл: x >> 9
    static final int CELL_SHIFT = 2;  // пиксель → клетка биома: x >> 2
    static final int CELLS = SIZE >> CELL_SHIFT;
    static final short NO_HEIGHT = Short.MIN_VALUE;

    // Заливка в видеопамять за кадр: столько пикселей — четыре полных тайла
    private static final int UPLOAD_PIXELS_PER_FRAME = 4 * SIZE * SIZE;
    private static int uploadPixelsLeft = UPLOAD_PIXELS_PER_FRAME;

    private static int nextId;

    final int level;
    final int tx;
    final int tz;
    final int[] colors;
    final short[] heights;
    /** Номер биома в MapPalettes; 0 — неизвестно. */
    final short[] biomes;
    /** Только у уровней отдаления: средний склон блоков под пикселем × SLOPE_SCALE (MapPyramid); у регионов null. */
    final byte[] slopes;
    static final float SLOPE_SCALE = 20; // склон ±6 → байт ±120

    private NativeImage display;
    private final DynamicTexture texture;
    private final ResourceLocation textureId;
    private int styleVersion = -1; // по какому стилю собрана картинка
    private int dataVersion;       // растёт, когда данные заменены целиком
    private int paintedData = -1;  // по какой версии данных собрана картинка; -1 — ещё не собиралась
    private boolean painting;      // сборка в фоновом потоке
    private boolean uploaded;      // собранная картинка уже в видеопамяти — тайл можно показывать
    private boolean closed;
    private boolean blur;
    private long lastUsed = Util.getMillis();

    // Что залить в видеопамять
    private int dirtyMinX = SIZE;
    private int dirtyMinZ = SIZE;
    private int dirtyMaxX = -1;
    private int dirtyMaxZ = -1;
    // Что изменилось, пока картинка собиралась в фоне: дорисовать, когда она вернётся
    private int lateMinX = SIZE;
    private int lateMinZ = SIZE;
    private int lateMaxX = -1;
    private int lateMaxZ = -1;

    MapTile(int level, int tx, int tz, int[] colors, short[] heights, short[] biomes, byte[] slopes) {
        this.slopes = slopes;
        this.level = level;
        this.tx = tx;
        this.tz = tz;
        this.colors = colors;
        this.heights = heights;
        this.biomes = biomes;
        this.display = new NativeImage(SIZE, SIZE, true);
        this.texture = new DynamicTexture(display);
        this.textureId = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "map/tile_" + nextId++);
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
    }

    static short[] emptyHeights() {
        short[] heights = new short[SIZE * SIZE];
        Arrays.fill(heights, NO_HEIGHT);
        return heights;
    }

    /** Новый кадр окна карты: снова можно залить сколько-то пикселей. */
    static void newFrame() {
        uploadPixelsLeft = UPLOAD_PIXELS_PER_FRAME;
    }

    /** Блоков мира на пиксель. */
    int blocksPerPixel() {
        return 1 << level;
    }

    short height(int x, int z) {
        return heights[z * SIZE + x];
    }

    short biome(int cellX, int cellZ) {
        return biomes[cellZ * CELLS + cellX];
    }

    void touch() {
        lastUsed = Util.getMillis();
    }

    long lastUsed() {
        return lastUsed;
    }

    // ===== Картинка =====

    /**
     * Текстура для рисования. Если картинка не по нынешнему стилю — заказывает сборку в фоне
     * (пока показывается прежняя); изменённое заливает в видеопамять, сколько позволяет бюджет кадра.
     * blur — сглаживать при уменьшении (отдаление), иначе чёткие пиксели.
     */
    ResourceLocation texture(boolean blur) {
        touch();
        int version = MapStyle.version();
        if (!painting && (styleVersion != version || paintedData != dataVersion)) {
            requestPaint(version);
        }
        if (dirtyMaxX >= 0) {
            int width = dirtyMaxX - dirtyMinX + 1;
            int height = dirtyMaxZ - dirtyMinZ + 1;
            if (uploadPixelsLeft > 0) {
                uploadPixelsLeft -= width * height;
                texture.bind();
                display.upload(0, dirtyMinX, dirtyMinZ, dirtyMinX, dirtyMinZ, width, height, false, false);
                uploaded = paintedData != -1;
                dirtyMinX = SIZE;
                dirtyMinZ = SIZE;
                dirtyMaxX = -1;
                dirtyMaxZ = -1;
            }
        }
        if (this.blur != blur) {
            this.blur = blur;
            texture.setFilter(blur, false);
        }
        return textureId;
    }

    /** Есть ли что показать: картинка собрана и залита в видеопамять. */
    boolean painted() {
        return uploaded;
    }

    // Снимок данных — в фоновый поток; там собирается новая картинка, здесь она подменяет прежнюю
    private void requestPaint(int version) {
        painting = true;
        int[] colorsCopy = colors.clone();
        short[] heightsCopy = heights.clone();
        byte[] slopesCopy = slopes == null ? null : slopes.clone();
        short[] north = northEdge();
        short[] west = westEdge();
        int data = dataVersion;
        int generation = MapWorker.generation();
        MapWorker.submit(() -> {
            NativeImage image = paint(colorsCopy, heightsCopy, slopesCopy, north, west, level, tx, tz);
            MapWorker.toMain(generation, () -> painted(image, version, data), image::close);
        });
    }

    private void painted(NativeImage image, int version, int data) {
        painting = false;
        if (closed) {
            image.close();
            return;
        }
        texture.setPixels(image); // прежняя картинка закрывается
        display = image;
        styleVersion = version;
        paintedData = data;
        markDirty(0, 0, SIZE - 1, SIZE - 1);
        // Что поменялось, пока собиралось, — дорисовать здесь
        if (lateMaxX >= 0) {
            for (int z = lateMinZ; z <= lateMaxZ; z++) {
                for (int x = lateMinX; x <= lateMaxX; x++) {
                    paintPixel(x, z);
                }
            }
            lateMinX = SIZE;
            lateMinZ = SIZE;
            lateMaxX = -1;
            lateMaxZ = -1;
        }
    }

    /**
     * Данные в прямоугольнике поменялись (главный поток). Картинка по нынешнему стилю — дорисовать здесь;
     * собирается в фоне — дорисовать, когда вернётся; не собрана — соберётся целиком.
     * Тень пикселя зависит от соседей с севера и запада — поэтому захватываем и пиксели правее и ниже.
     */
    void dataChanged(int minX, int minZ, int maxX, int maxZ) {
        maxX = Math.min(maxX + 1, SIZE - 1);
        maxZ = Math.min(maxZ + 1, SIZE - 1);
        if (painting) {
            lateMinX = Math.min(lateMinX, minX);
            lateMinZ = Math.min(lateMinZ, minZ);
            lateMaxX = Math.max(lateMaxX, maxX);
            lateMaxZ = Math.max(lateMaxZ, maxZ);
        } else if (styleVersion == MapStyle.version() && paintedData == dataVersion) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    paintPixel(x, z);
                }
            }
        }
    }

    /** Данные заменены целиком (пришли из фонового потока) — картинка соберётся заново, пока видна прежняя. */
    void dataReplaced() {
        dataVersion++;
    }

    private void paintPixel(int x, int z) {
        int index = z * SIZE + x;
        int height = heights[index];
        short[] north = z == 0 ? northEdge() : null;
        short[] west = x == 0 ? westEdge() : null;
        int n = z > 0 ? heights[index - SIZE] : north != null ? north[x] : NO_HEIGHT;
        int w = x > 0 ? heights[index - 1] : west != null ? west[z] : NO_HEIGHT;
        int color = styled(colors[index], height, n, w, slopes, index, level,
                (tx << SHIFT) + x, (tz << SHIFT) + z);
        display.setPixelRGBA(x, z, FastColor.ABGR32.fromArgb32(color));
        markDirty(x, z, x, z);
    }

    private void markDirty(int minX, int minZ, int maxX, int maxZ) {
        dirtyMinX = Math.min(dirtyMinX, minX);
        dirtyMinZ = Math.min(dirtyMinZ, minZ);
        dirtyMaxX = Math.max(dirtyMaxX, maxX);
        dirtyMaxZ = Math.max(dirtyMaxZ, maxZ);
    }

    /** Высоты соседа с севера (его нижний ряд) — для тени верхнего ряда; null — неизвестны. */
    short[] northEdge() {
        return null;
    }

    /** Высоты соседа с запада (его правый столбец). */
    short[] westEdge() {
        return null;
    }

    void close() {
        closed = true;
        Minecraft.getInstance().getTextureManager().release(textureId); // закрывает и картинку
    }

    // ===== Сборка картинки (любой поток) =====

    static NativeImage paint(int[] colors, short[] heights, byte[] slopes, short[] north, short[] west, int level, int tx, int tz) {
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        int originX = tx << SHIFT;
        int originZ = tz << SHIFT;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int index = z * SIZE + x;
                int n = z > 0 ? heights[index - SIZE] : north != null ? north[x] : NO_HEIGHT;
                int w = x > 0 ? heights[index - 1] : west != null ? west[z] : NO_HEIGHT;
                int color = styled(colors[index], heights[index], n, w, slopes, index, level, originX + x, originZ + z);
                if (color != 0) {
                    image.setPixelRGBA(x, z, FastColor.ABGR32.fromArgb32(color));
                }
            }
        }
        return image;
    }

    // Пиксель по стилю. pixelX/pixelZ — номер пикселя на своём уровне (для зерна бумаги).
    // У уровней отдаления склон не считается по соседям (они в 2^L блоках — рельеф сгладился бы), а берётся готовый средний
    private static int styled(int raw, int height, int north, int west, byte[] slopes, int index, int level,
                              int pixelX, int pixelZ) {
        float slope = slopes != null ? slopes[index] / SLOPE_SCALE : MapStyle.slope(height, north, west);
        return MapStyle.applySlope(raw, height, slope, pixelX << level, pixelZ << level);
    }
}
