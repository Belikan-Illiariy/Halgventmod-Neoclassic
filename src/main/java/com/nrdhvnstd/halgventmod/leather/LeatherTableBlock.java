package com.nrdhvnstd.halgventmod.leather;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Стол кожевника в два блока (столярный верстак Conquest Reforged, левый и правый край).
 * Левая часть — главная: в ней блок-сущность, на ней видна выкройка; на правой лежат кожа и фурнитура.
 * «Левая» — если смотреть на стол спереди. Сломать любую половину — уходит весь стол.
 */
public class LeatherTableBlock extends StationBlock {
    public static final MapCodec<LeatherTableBlock> CODEC = simpleCodec(LeatherTableBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public enum Part implements StringRepresentable {
        LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return this == LEFT ? "left" : "right";
        }
    }

    public LeatherTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, Part.LEFT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** С какой стороны от левой части стоит правая (если смотреть на стол спереди). */
    public static Direction toRight(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    /** Направление от этой части к другой. */
    private static Direction toOther(BlockState state) {
        Direction right = toRight(state);
        return state.getValue(PART) == Part.LEFT ? right : right.getOpposite();
    }

    @Override
    protected BlockPos stationPos(BlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.LEFT ? pos : pos.relative(toOther(state));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        BlockPos right = context.getClickedPos().relative(toRight(state));
        Level level = context.getLevel();
        if (!level.getBlockState(right).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(right)) {
            return null;
        }
        return state.setValue(PART, Part.LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(pos.relative(toRight(state)), state.setValue(PART, Part.RIGHT), Block.UPDATE_ALL);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    // Пропала вторая половина — пропадает и эта (без добычи: её даёт сломанная половина)
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == toOther(state)) {
            boolean paired = neighbor.is(this) && neighbor.getValue(PART) != state.getValue(PART)
                    && neighbor.getValue(FACING) == state.getValue(FACING);
            if (!paired) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.LEFT ? new LeatherTableBlockEntity(pos, state) : null;
    }
}
