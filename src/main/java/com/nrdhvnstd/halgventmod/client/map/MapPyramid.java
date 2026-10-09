package com.nrdhvnstd.halgventmod.client.map;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.level.ChunkPos;

/**
 * Уровни отдаления (LOD-пирамида), только в фоновом потоке (MapWorker).
 * Уровень 0 — регионы. Тайл уровня L+1 — четыре соседних тайла уровня L, сжатые в один того же размера:
 * каждый его пиксель — среднее по квадрату 2 × 2. Поэтому издалека каждый пиксель экрана — честное среднее
 * по всем блокам под ним (без ряби), а тайлов на экране примерно одинаково при любом масштабе.
 *
 * update — после записи регионов: перестраиваются только задетые четверти, уровень за уровнем вверх.
 * Rebuild — постройка с нуля (карта старого формата, смена текстур): маленькими шагами, по тайлу за задачу,
 * чтобы загрузка регионов для окна карты не ждала, пока построится всё.
 */
final class MapPyramid {
    static final int MAX_LEVEL = 6; // тайл верхнего уровня — 32 768 блоков в стороне

    private static final int SIZE = MapTile.SIZE;
    private static final int HALF = SIZE / 2;
    private static final int CELLS = MapTile.CELLS;

    private MapPyramid() {}

    static long parent(long key) {
        return MapStorage.key(ChunkPos.getX(key) >> 1, ChunkPos.getZ(key) >> 1);
    }

    // ===== После записи регионов =====

    static void update(Path dir, int generation, List<MapRegion.Snapshot> regions) {
        Map<Long, MapLod.Data> children = new HashMap<>();
        for (MapRegion.Snapshot snapshot : regions) {
            MapRegion.Data data = snapshot.data();
            children.put(snapshot.key(), MapLod.Data.fromRegion(data.colors(), data.heights(), data.biomes()));
        }
        for (int level = 1; level <= MAX_LEVEL && !children.isEmpty(); level++) {
            Map<Long, MapLod.Data> parents = new HashMap<>();
            for (Map.Entry<Long, MapLod.Data> child : children.entrySet()) {
                long parentKey = parent(child.getKey());
                MapLod.Data parent = parents.get(parentKey);
                if (parent == null) {
                    parent = MapStorage.readLod(dir, level, parentKey);
                    if (parent == null) {
                        parent = MapLod.Data.empty();
                    }
                    parents.put(parentKey, parent);
                }
                shrinkInto(parent, child.getValue(), ChunkPos.getX(child.getKey()) & 1, ChunkPos.getZ(child.getKey()) & 1);
            }
            for (Map.Entry<Long, MapLod.Data> parent : parents.entrySet()) {
                MapStorage.writeLod(dir, level, parent.getKey(), parent.getValue());
                notifyMain(generation, level, parent.getKey(), parent.getValue());
            }
            children = parents;
        }
    }

    private static void notifyMain(int generation, int level, long key, MapLod.Data data) {
        // Копия: дальше этот тайл служит «ребёнком» для уровня выше, а главный поток возьмёт свою
        MapLod.Data copy = new MapLod.Data(data.colors().clone(), data.heights().clone(), data.slopes().clone(),
                data.biomes().clone());
        MapWorker.toMain(generation, () -> WorldMap.lodUpdated(level, key, copy));
    }

    // Четверть родителя (qx, qz) — из ребёнка вдвое мельче: среднее 2 × 2 пикселя, биом — из середины клетки.
    // Склон тоже средний — из склонов блоков, а не из сглаженных высот: так рельеф виден и издалека
    private static void shrinkInto(MapLod.Data parent, MapLod.Data child, int qx, int qz) {
        int[] colors = child.colors();
        short[] heights = child.heights();
        byte[] slopes = child.slopes();
        for (int z = 0; z < HALF; z++) {
            for (int x = 0; x < HALF; x++) {
                int a = (2 * z) * SIZE + 2 * x;
                int b = a + 1;
                int c = a + SIZE;
                int d = c + 1;
                int target = (qz * HALF + z) * SIZE + qx * HALF + x;
                parent.colors()[target] = average(colors[a], colors[b], colors[c], colors[d]);
                parent.heights()[target] = average(heights[a], heights[b], heights[c], heights[d]);
                parent.slopes()[target] = averageSlope(heights, slopes, a, b, c, d);
            }
        }
        int halfCells = CELLS / 2;
        for (int z = 0; z < halfCells; z++) {
            for (int x = 0; x < halfCells; x++) {
                parent.biomes()[(qz * halfCells + z) * CELLS + qx * halfCells + x] =
                        child.biomes()[(2 * z + 1) * CELLS + 2 * x + 1];
            }
        }
    }

    // Средний цвет известных пикселей; вид (окрашен биомом или нет) — какого больше
    private static int average(int... pixels) {
        int r = 0;
        int g = 0;
        int b = 0;
        int known = 0;
        int tinted = 0;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha == 0) {
                continue;
            }
            r += (pixel >> 16) & 0xFF;
            g += (pixel >> 8) & 0xFF;
            b += pixel & 0xFF;
            known++;
            if (alpha == MapStyle.ALPHA_TINTED) {
                tinted++;
            }
        }
        if (known == 0) {
            return 0;
        }
        int alpha = tinted * 2 > known ? MapStyle.ALPHA_TINTED : MapStyle.ALPHA_PLAIN;
        return alpha << 24 | (r / known) << 16 | (g / known) << 8 | b / known;
    }

    // Средний склон пикселей с известной высотой
    private static byte averageSlope(short[] heights, byte[] slopes, int... indices) {
        int sum = 0;
        int known = 0;
        for (int index : indices) {
            if (heights[index] != MapTile.NO_HEIGHT) {
                sum += slopes[index];
                known++;
            }
        }
        return known == 0 ? 0 : (byte) Math.round((float) sum / known);
    }

    private static short average(short a, short b, short c, short d) {
        int sum = 0;
        int known = 0;
        for (short height : new short[] {a, b, c, d}) {
            if (height != MapTile.NO_HEIGHT) {
                sum += height;
                known++;
            }
        }
        return known == 0 ? MapTile.NO_HEIGHT : (short) (sum / known);
    }

    // ===== Постройка с нуля =====

    /**
     * Постройка всех уровней заново, шаг за шагом. Заодно переводит регионы старого формата в новый
     * и пересчитывает цвета под нынешние текстуры. Прервалась (сменили мир, вышли из игры) — в следующий раз
     * начнётся сначала: пометка «построено» ставится только в конце.
     */
    static final class Rebuild implements Runnable {
        private final Path dir;
        private final int generation;
        private int level;
        private Set<Long> below;                        // что есть на уровне ниже — по нему выбираются тайлы для постройки
        private final Set<Long> built = new HashSet<>(); // что построено на этом уровне
        private final ArrayDeque<Long> todo = new ArrayDeque<>();

        Rebuild(Path dir, int generation) {
            this.dir = dir;
            this.generation = generation;
        }

        void start() {
            MapWorker.submit(() -> {
                MapStorage.setPyramidVersion(dir, 0);
                MapStorage.clearLods(dir);
                MapWorker.toMain(generation, WorldMap::lodsCleared);
                below = MapStorage.listRegions(dir);
                nextLevel();
                MapWorker.submit(this);
            });
        }

        private void nextLevel() {
            level++;
            todo.clear();
            Set<Long> parents = new HashSet<>();
            for (long key : below) {
                parents.add(parent(key));
            }
            todo.addAll(parents);
        }

        @Override
        public void run() {
            if (generation != MapWorker.generation()) {
                return; // мир сменился — бросаем, пометки «построено» нет
            }
            Long parentKey = todo.poll();
            if (parentKey != null) {
                buildTile(parentKey);
            }
            if (todo.isEmpty()) {
                if (level >= MAX_LEVEL || built.isEmpty()) {
                    MapStorage.setPyramidVersion(dir, MapStorage.PYRAMID_VERSION);
                    return;
                }
                below = new HashSet<>(built);
                built.clear();
                nextLevel();
            }
            MapWorker.submit(this); // следующий шаг — в конец очереди, после загрузок для окна карты
        }

        private void buildTile(long parentKey) {
            MapLod.Data parent = MapLod.Data.empty();
            int px = ChunkPos.getX(parentKey);
            int pz = ChunkPos.getZ(parentKey);
            boolean any = false;
            for (int q = 0; q < 4; q++) {
                long childKey = MapStorage.key(2 * px + (q & 1), 2 * pz + (q >> 1));
                MapLod.Data child = level == 1 ? region(childKey) : MapStorage.readLod(dir, level - 1, childKey);
                if (child != null) {
                    shrinkInto(parent, child, q & 1, q >> 1);
                    any = true;
                }
            }
            if (any) {
                MapStorage.writeLod(dir, level, parentKey, parent);
                notifyMain(generation, level, parentKey, parent);
                built.add(parentKey);
            }
        }

        // Регион для первого уровня; заодно переписать его в новом формате с нынешними цветами
        private MapLod.Data region(long key) {
            MapRegion.Data data = MapStorage.readRegion(dir, key);
            if (data == null) {
                return null;
            }
            MapStorage.writeRegion(dir, key, data);
            return MapLod.Data.fromRegion(data.colors(), data.heights(), data.biomes());
        }
    }
}
