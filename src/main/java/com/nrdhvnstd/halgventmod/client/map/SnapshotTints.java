package com.nrdhvnstd.halgventmod.client.map;

import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.map.LocationMapData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Мир для раскраски снимка карты локации (как ChunkTints для чанков): оттенок биома — по биомам самого снимка,
 * а не по загруженным чанкам, которых у игрока вдали нет. Оттенок клетки 4 × 4 — среднее по ней и восьми соседним
 * клеткам с известным биомом; клетки считаются по разу на каждый вид оттенка (трава, листва, вода).
 * Блоков вокруг этот «мир» не знает: для раскраски они не нужны.
 */
final class SnapshotTints implements BlockAndTintGetter {
    private static final int NOT_COUNTED = 0;
    private static final int UNKNOWN = 1; // у клетки нет биома

    private final Map<ColorResolver, int[]> cells = new IdentityHashMap<>();
    private LocationMapData data;
    private Holder<Biome>[] biomes; // по номеру палитры снимка; null — биома нет в игре
    private ClientLevel level;

    /** Подготовиться к раскраске снимка. */
    @SuppressWarnings("unchecked")
    void begin(LocationMapData data) {
        this.data = data;
        this.level = Minecraft.getInstance().level;
        cells.clear();
        biomes = new Holder[data.biomePalette.size()];
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (int i = 1; i < biomes.length; i++) {
            ResourceLocation name = data.biomePalette.get(i);
            if (name != null) {
                biomes[i] = registry.getHolder(ResourceKey.create(Registries.BIOME, name)).orElse(null);
            }
        }
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        int[] colors = cells.computeIfAbsent(resolver, r -> new int[data.biomes.length]);
        int cellX = Math.floorDiv(pos.getX() - data.minX, LocationMapData.CELL);
        int cellZ = Math.floorDiv(pos.getZ() - data.minZ, LocationMapData.CELL);
        int r = 0;
        int g = 0;
        int b = 0;
        int n = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int color = cell(colors, resolver, cellX + dx, cellZ + dz);
                if (color != UNKNOWN) {
                    r += (color >> 16) & 0xFF;
                    g += (color >> 8) & 0xFF;
                    b += color & 0xFF;
                    n++;
                }
            }
        }
        if (n == 0) {
            return level.getBlockTint(pos, resolver); // вокруг ни одного известного биома — как у уровня
        }
        return (r / n) << 16 | (g / n) << 8 | b / n;
    }

    // Оттенок клетки (0xFF000000 | RGB) или UNKNOWN
    private int cell(int[] colors, ColorResolver resolver, int cellX, int cellZ) {
        int cellsX = data.cellsX();
        if (cellX < 0 || cellZ < 0 || cellX >= cellsX || cellZ >= colors.length / cellsX) {
            return UNKNOWN;
        }
        int index = cellZ * cellsX + cellX;
        if (colors[index] == NOT_COUNTED) {
            Holder<Biome> biome = biome(data.biomes[index]);
            colors[index] = biome == null ? UNKNOWN : 0xFF000000 | resolver.getColor(biome.value(),
                    data.minX + cellX * LocationMapData.CELL + 2, data.minZ + cellZ * LocationMapData.CELL + 2);
        }
        return colors[index];
    }

    @Nullable
    private Holder<Biome> biome(int paletteIndex) {
        return paletteIndex > 0 && paletteIndex < biomes.length ? biomes[paletteIndex] : null;
    }

    /** Освободить ссылки после раскраски. */
    void end() {
        cells.clear();
        data = null;
        biomes = null;
    }

    // ===== Остальное: вокруг пусто =====

    @Override
    public float getShade(Direction direction, boolean shade) {
        return level.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
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
