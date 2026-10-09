package com.nrdhvnstd.halgventmod.client.map;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * Мир для раскраски одного чанка карты: всё как у уровня, кроме оттенка биома.
 * Ванильное смешивание биомов на месте незагруженного соседа берёт «пустой» биом (равнины),
 * и у края чанка вода и трава окрашивались не тем цветом — на карте выходили полосатые квадраты.
 * Здесь смешиваются только загруженные чанки.
 *
 * Чтобы не тормозить: биомы берутся прямо из чанков клетками 4 × 4 (как их хранит игра) на высоте
 * поверхности, а среднее по окну смешивания считается по таблице сумм — за одно действие на блок.
 */
final class ChunkTints implements BlockAndTintGetter {
    private static final int MAX_RADIUS = 7; // больше 15 × 15 ванильная настройка не даёт

    private final LevelChunk[] chunks = new LevelChunk[9]; // сам чанк и восемь соседей; null — не загружен
    private final Map<ColorResolver, Sums> sums = new IdentityHashMap<>();
    private ClientLevel level;
    private int chunkX;
    private int chunkZ;
    private int radius;
    private int size; // сторона окрестности в блоках: чанк и по radius с каждой стороны

    // Биомы окрестности клетками 4 × 4; считаются, только если в чанке есть окрашиваемые блоки
    private int quartOrigin;
    private int quartCount;
    private Holder<Biome>[] biomes;
    private boolean[] biomeKnown;

    /** Подготовиться к раскраске чанка: запомнить соседей. */
    void begin(ClientLevel level, int chunkX, int chunkZ) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        radius = Mth.clamp(Minecraft.getInstance().options.biomeBlendRadius().get(), 0, MAX_RADIUS);
        size = 16 + 2 * radius;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                chunks[(dz + 1) * 3 + dx + 1] = level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, false);
            }
        }
        sums.clear();
        biomes = null;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        int localX = pos.getX() - (chunkX << 4);
        int localZ = pos.getZ() - (chunkZ << 4);
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            return level.getBlockTint(pos, resolver);
        }
        Sums table = sums.get(resolver);
        if (table == null) {
            table = new Sums(resolver);
            sums.put(resolver, table);
        }
        // Окно смешивания блока — от него на radius в каждую сторону; в координатах окрестности это [local, local + 2r]
        return table.average(localX, localZ, localX + 2 * radius + 1, localZ + 2 * radius + 1);
    }

    /** Биом в клетке, где лежит блок окрестности; null — его чанк не загружен. */
    private Holder<Biome> biome(int localX, int localZ) {
        if (biomes == null) {
            quartOrigin = Math.floorDiv(-radius, 4);
            quartCount = Math.floorDiv(15 + radius, 4) - quartOrigin + 1;
            @SuppressWarnings("unchecked")
            Holder<Biome>[] fresh = new Holder[quartCount * quartCount];
            biomes = fresh;
            biomeKnown = new boolean[quartCount * quartCount];
        }
        int qx = Math.floorDiv(localX, 4) - quartOrigin;
        int qz = Math.floorDiv(localZ, 4) - quartOrigin;
        int index = qz * quartCount + qx;
        if (!biomeKnown[index]) {
            biomeKnown[index] = true;
            // Середина клетки, на высоте поверхности в ней
            int cellX = (qx + quartOrigin) * 4 + 2;
            int cellZ = (qz + quartOrigin) * 4 + 2;
            LevelChunk chunk = chunks[((cellZ >> 4) + 1) * 3 + (cellX >> 4) + 1];
            if (chunk != null) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, cellX & 15, cellZ & 15);
                biomes[index] = chunk.getNoiseBiome(QuartPos.fromBlock((chunkX << 4) + cellX),
                        QuartPos.fromBlock(y), QuartPos.fromBlock((chunkZ << 4) + cellZ));
            }
        }
        return biomes[index];
    }

    /** Суммы цветов одного оттенка по окрестности: сумма по любому прямоугольнику — из четырёх чисел. */
    private final class Sums {
        private final int stride = size + 1;
        private final int[] red = new int[stride * stride];
        private final int[] green = new int[stride * stride];
        private final int[] blue = new int[stride * stride];
        private final int[] count = new int[stride * stride];

        Sums(ColorResolver resolver) {
            for (int gz = 0; gz < size; gz++) {
                for (int gx = 0; gx < size; gx++) {
                    int localX = gx - radius;
                    int localZ = gz - radius;
                    Holder<Biome> biome = biome(localX, localZ);
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    int n = 0;
                    if (biome != null) {
                        int color = resolver.getColor(biome.value(), (chunkX << 4) + localX, (chunkZ << 4) + localZ);
                        r = (color >> 16) & 0xFF;
                        g = (color >> 8) & 0xFF;
                        b = color & 0xFF;
                        n = 1;
                    }
                    int i = (gz + 1) * stride + gx + 1;
                    red[i] = r + red[i - 1] + red[i - stride] - red[i - stride - 1];
                    green[i] = g + green[i - 1] + green[i - stride] - green[i - stride - 1];
                    blue[i] = b + blue[i - 1] + blue[i - stride] - blue[i - stride - 1];
                    count[i] = n + count[i - 1] + count[i - stride] - count[i - stride - 1];
                }
            }
        }

        // Средний цвет прямоугольника [x0, x1) × [z0, z1) окрестности. Сам блок всегда загружен — делим не на ноль
        int average(int x0, int z0, int x1, int z1) {
            int n = sum(count, x0, z0, x1, z1);
            return (sum(red, x0, z0, x1, z1) / n) << 16 | (sum(green, x0, z0, x1, z1) / n) << 8 | sum(blue, x0, z0, x1, z1) / n;
        }

        private int sum(int[] table, int x0, int z0, int x1, int z1) {
            return table[z1 * stride + x1] - table[z0 * stride + x1] - table[z1 * stride + x0] + table[z0 * stride + x0];
        }
    }

    // ===== Остальное — как у уровня =====

    @Override
    public float getShade(Direction direction, boolean shade) {
        return level.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return level.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return level.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return level.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }
}
