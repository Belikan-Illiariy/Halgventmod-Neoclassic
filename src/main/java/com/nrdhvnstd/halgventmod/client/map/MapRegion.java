package com.nrdhvnstd.halgventmod.client.map;

/**
 * Регион карты — 512 × 512 блоков (32 × 32 чанка), уровень 0: пиксель на блок.
 * Кроме цвета, высоты и биома (MapTile) знает, какой блок дал цвет пикселя (номер в MapPalettes):
 * по нему цвета пересчитываются, если сменились текстуры. Пишется сканированием чанков (WorldMap),
 * на диск уходит снимком (Snapshot) в фоновом потоке.
 */
public final class MapRegion extends MapTile {
    public static final int SIZE = MapTile.SIZE;
    public static final int SHIFT = MapTile.SHIFT;
    public static final int CELL_SHIFT = MapTile.CELL_SHIFT;
    public static final int CELLS = MapTile.CELLS;
    public static final short NO_HEIGHT = MapTile.NO_HEIGHT;

    /** Данные региона: в памяти и для записи. Номера блоков и биомов — общие, из MapPalettes. */
    record Data(int[] colors, short[] blocks, short[] heights, short[] biomes) {
        static Data empty() {
            return new Data(new int[SIZE * SIZE], new short[SIZE * SIZE], emptyHeights(), new short[CELLS * CELLS]);
        }
    }

    /** Копия для записи на диск и для уровней отдаления. */
    record Snapshot(long key, Data data) {}

    final short[] blocks;
    private boolean needsSave;

    MapRegion(int rx, int rz, Data data) {
        super(0, rx, rz, data.colors(), data.heights(), data.biomes(), null);
        this.blocks = data.blocks();
    }

    /** Пиксель от сканирования: цвет (ARGB, вид в альфе), блок, давший цвет, и высота. true — что-то поменялось. */
    boolean set(int x, int z, int argb, short block, int height) {
        int index = z * SIZE + x;
        if (colors[index] == argb && blocks[index] == block && heights[index] == height) {
            return false;
        }
        colors[index] = argb;
        blocks[index] = block;
        heights[index] = (short) height;
        needsSave = true;
        return true;
    }

    /** Биом клетки 4 × 4. true — поменялся. */
    boolean setBiome(int cellX, int cellZ, short biome) {
        int index = cellZ * CELLS + cellX;
        if (biomes[index] == biome) {
            return false;
        }
        biomes[index] = biome;
        needsSave = true;
        return true;
    }

    boolean needsSave() {
        return needsSave;
    }

    /** Копия данных для записи; после неё регион считается сохранённым. */
    Snapshot snapshot(long key) {
        needsSave = false;
        return new Snapshot(key, new Data(colors.clone(), blocks.clone(), heights.clone(), biomes.clone()));
    }

    /** Текстуры сменились: пересчитать цвета пикселей, чьи блоки поменяли базу. old/now — базы по номеру блока. */
    void recolor(int[] oldBases, int[] newBases) {
        boolean changed = false;
        for (int i = 0; i < colors.length; i++) {
            int block = blocks[i];
            if (block > 0 && block < oldBases.length && oldBases[block] != newBases[block]) {
                colors[i] = MapPalettes.recolor(colors[i], oldBases[block], newBases[block]);
                changed = true;
            }
        }
        if (changed) {
            needsSave = true;
            dataReplaced();
        }
    }

    @Override
    short[] northEdge() {
        return WorldMap.edge(tx, tz - 1, true);
    }

    @Override
    short[] westEdge() {
        return WorldMap.edge(tx - 1, tz, false);
    }
}
