package com.nrdhvnstd.halgventmod.client.map;

/**
 * Тайл уровня отдаления L ≥ 1: 512 × 512 пикселей, пиксель — среднее по квадрату 2^L × 2^L блоков,
 * всего 512·2^L блоков в стороне. Строится и обновляется в фоне (MapPyramid), хранится в lod/ (MapStorage).
 * Благодаря ему издалека карта без ряби, а тайлов на экране примерно столько же, сколько вблизи.
 */
final class MapLod extends MapTile {
    /** Данные тайла: цвет, высота, средний склон (× SLOPE_SCALE), биомы клетками 4 × 4 пикселя. */
    record Data(int[] colors, short[] heights, byte[] slopes, short[] biomes) {
        static Data empty() {
            return new Data(new int[SIZE * SIZE], emptyHeights(), new byte[SIZE * SIZE], new short[CELLS * CELLS]);
        }

        /** Регион как «ребёнок» первого уровня: склоны — по его полным высотам, блок за блоком. */
        static Data fromRegion(int[] colors, short[] heights, short[] biomes) {
            byte[] slopes = new byte[SIZE * SIZE];
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    int index = z * SIZE + x;
                    int north = z > 0 ? heights[index - SIZE] : NO_HEIGHT;
                    int west = x > 0 ? heights[index - 1] : NO_HEIGHT;
                    slopes[index] = (byte) Math.round(MapStyle.slope(heights[index], north, west) * SLOPE_SCALE);
                }
            }
            return new Data(colors, heights, slopes, biomes);
        }
    }

    MapLod(int level, int tx, int tz, Data data) {
        super(level, tx, tz, data.colors(), data.heights(), data.biomes(), data.slopes());
    }

    /** Фоновый поток перестроил тайл — взять новые данные, картинка соберётся заново. */
    void replace(Data data) {
        System.arraycopy(data.colors(), 0, colors, 0, colors.length);
        System.arraycopy(data.heights(), 0, heights, 0, heights.length);
        System.arraycopy(data.slopes(), 0, slopes, 0, slopes.length);
        System.arraycopy(data.biomes(), 0, biomes, 0, biomes.length);
        dataReplaced();
    }
}
