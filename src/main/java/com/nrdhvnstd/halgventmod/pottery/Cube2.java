package com.nrdhvnstd.halgventmod.pottery;

import com.nrdhvnstd.halgventmod.leather.StationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Станция 2×2 в основании и N ярусов высотой из частей одного блока (купольный горн, печь, стекловаренная печь —
 * 2×2×2, сыродутный горн — 2×2×3). Главная — часть 0, передняя левая нижняя (если смотреть на лицо — оно к игроку,
 * поставившему её): в ней блок-сущность и модель нижних двух ярусов (32×32×32); выше — модель рисует часть 8
 * (над главной, третий ярус), остальные части пустые, только место. Номер части: бит 1 — правее (по часовой от лица),
 * 2 — дальше от лица, остальное — ярус (part &gt;&gt; 2). Сломать любую — разбирается вся станция (добычу даёт главная:
 * лут — с условием part = 0).
 * <p>Методы без свойства — для 2×2×2 ({@link #PART}, 8 частей); с ним — для своего размера (parts = 4 × ярусов).
 */
public final class Cube2 {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    /** Часть, что рисует третий ярус — над главной. */
    public static final int THIRD_TIER = 8;

    private Cube2() {}

    /** Где часть part стоит относительно главной при лице facing. */
    public static BlockPos offset(Direction facing, int part) {
        return BlockPos.ZERO.relative(facing.getClockWise(), part & 1).relative(facing.getOpposite(), (part >> 1) & 1)
                .above(part >> 2);
    }

    /** Главная часть станции, к которой принадлежит эта. */
    public static BlockPos main(BlockState state, BlockPos pos) {
        return main(state, pos, PART);
    }

    public static BlockPos main(BlockState state, BlockPos pos, IntegerProperty part) {
        return pos.subtract(offset(state.getValue(StationBlock.FACING), state.getValue(part)));
    }

    /** Середина станции по горизонтали (x, z) — между главной и дальней правой частью. */
    public static double[] center(BlockState state, BlockPos main) {
        BlockPos far = main.offset(offset(state.getValue(StationBlock.FACING), 3));
        return new double[] {(main.getX() + far.getX()) / 2.0 + 0.5, (main.getZ() + far.getZ()) / 2.0 + 0.5};
    }

    /** Хватает ли места под остальные части. */
    public static boolean fits(BlockPlaceContext context, Direction facing) {
        return fits(context, facing, 8);
    }

    public static boolean fits(BlockPlaceContext context, Direction facing, int parts) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (int part = 1; part < parts; part++) {
            BlockPos at = pos.offset(offset(facing, part));
            if (at.getY() >= level.getMaxBuildHeight() || !level.getBlockState(at).canBeReplaced(context)) {
                return false;
            }
        }
        return true;
    }

    /** Поставить остальные части (главная уже стоит). */
    public static void place(Level level, BlockPos main, BlockState state) {
        place(level, main, state, PART, 8);
    }

    public static void place(Level level, BlockPos main, BlockState state, IntegerProperty partProperty, int parts) {
        if (level.isClientSide) {
            return;
        }
        for (int part = 1; part < parts; part++) {
            level.setBlock(main.offset(offset(state.getValue(StationBlock.FACING), part)), state.setValue(partProperty, part),
                    Block.UPDATE_ALL);
        }
    }

    /**
     * Часть пропала (вызвать из onRemove после super): сломали не главную — ломается главная с добычей; главную —
     * остальные просто пропадают.
     */
    public static void removed(Block block, BlockState state, Level level, BlockPos pos, BlockState newState) {
        removed(block, state, level, pos, newState, PART, 8);
    }

    public static void removed(Block block, BlockState state, Level level, BlockPos pos, BlockState newState,
                               IntegerProperty partProperty, int parts) {
        if (newState.is(block) || level.isClientSide) {
            return;
        }
        BlockPos main = main(state, pos, partProperty);
        if (state.getValue(partProperty) != 0) {
            if (level.getBlockState(main).is(block)) {
                level.destroyBlock(main, true);
            }
            return;
        }
        for (int part = 1; part < parts; part++) {
            BlockPos at = main.offset(offset(state.getValue(StationBlock.FACING), part));
            BlockState there = level.getBlockState(at);
            if (there.is(block) && there.getValue(partProperty) == part) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    /** В творческом главная ломается без добычи, какую бы часть ни ударили (из playerWillDestroy). */
    public static void creativeBreak(Block block, Level level, BlockPos pos, BlockState state, Player player) {
        creativeBreak(block, level, pos, state, player, PART);
    }

    public static void creativeBreak(Block block, Level level, BlockPos pos, BlockState state, Player player,
                                     IntegerProperty partProperty) {
        if (!level.isClientSide && player.isCreative() && state.getValue(partProperty) != 0) {
            BlockPos main = main(state, pos, partProperty);
            if (level.getBlockState(main).is(block)) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }
}
