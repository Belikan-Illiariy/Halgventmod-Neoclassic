package com.nrdhvnstd.halgventmod.map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Выборка столбца для карты: верхний видимый блок и его высота, дно под водой и глубина воды, биом.
 * Общая для клиента (WorldMap раскрашивает полученные чанки) и сервера (LocationMaps снимает карту локации).
 * Цвет не считает: сервер не знает текстур. В измерениях с потолком (Незер) берётся первый пол под потолком.
 * Поля переписываются при каждом sample — один объект на поток.
 */
public final class ColumnSampler {
    /** Глубже — всё равно 255: столько вода закрывает дно полностью. */
    public static final int MAX_DEPTH = 255;
    private static final int CEILING_SCAN_BELOW_TOP = 9; // Незер: начинаем чуть ниже верха
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    /** Верхний видимый блок; у воды (и водорослей в ней) — вода. null — столбец пуст. */
    @Nullable
    public BlockState state;
    /** Его высота; Integer.MIN_VALUE — столбец пуст. */
    public int height;
    /** Дно под водой; null — воды нет или под ней до самого низа вода. */
    @Nullable
    public BlockState floor;
    /** Глубина воды в блоках, не больше MAX_DEPTH; 0 — воды нет. Дно — на height - depth. */
    public int depth;

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private Level level;

    /** Взять столбец (x; z) — координаты мира, в этом чанке. false — столбец пуст. */
    public boolean sample(LevelChunk chunk, int x, int z) {
        level = chunk.getLevel();
        state = null;
        floor = null;
        depth = 0;
        height = Integer.MIN_VALUE;
        int min = level.getMinBuildHeight();
        int y;
        if (level.dimensionType().hasCeiling()) {
            y = Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15),
                    min + level.dimensionType().logicalHeight() - CEILING_SCAN_BELOW_TOP);
            while (y >= min && !chunk.getBlockState(pos.set(x, y, z)).isAir()) {
                y--; // сквозь потолок
            }
        } else {
            y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        }
        for (; y >= min; y--) {
            BlockState found = chunk.getBlockState(pos.set(x, y, z));
            if (visible(found)) {
                state = found;
                height = y;
                break;
            }
        }
        if (state == null) {
            return false;
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            state = WATER;
            int floorY = height - 1;
            while (floorY >= min && chunk.getBlockState(pos.set(x, floorY, z)).getFluidState().is(FluidTags.WATER)) {
                floorY--;
            }
            depth = Math.min(height - floorY, MAX_DEPTH);
            if (floorY >= min) {
                floor = chunk.getBlockState(pos.set(x, floorY, z));
            }
        }
        return true;
    }

    /** Биом последнего взятого столбца — на высоте его поверхности; null — неизвестен. */
    @Nullable
    public ResourceLocation biome(int x, int z) {
        if (state == null) {
            return null;
        }
        return level.getBiome(pos.set(x, height, z)).unwrapKey().map(key -> key.location()).orElse(null);
    }

    private static boolean visible(BlockState state) {
        return state.getRenderShape() != RenderShape.INVISIBLE || !state.getFluidState().isEmpty();
    }
}
