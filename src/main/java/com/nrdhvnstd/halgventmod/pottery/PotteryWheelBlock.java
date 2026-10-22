package com.nrdhvnstd.halgventmod.pottery;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Гончарный круг в два блока (модель Conquest Reforged): в ногах — сам круг с маховиком, в головах — скамья.
 * Круг стоит на стыке частей. Главная — часть «в ногах»: в ней блок-сущность. Сломать любую — уходит весь круг.
 */
public class PotteryWheelBlock extends StationBlock {
    public static final MapCodec<PotteryWheelBlock> CODEC = simpleCodec(PotteryWheelBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    public enum Part implements StringRepresentable {
        FOOT, HEAD;

        @Override
        public String getSerializedName() {
            return this == FOOT ? "foot" : "head";
        }
    }

    public PotteryWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, Part.FOOT));
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Направление от этой части к другой: голова — в сторону FACING от ног. */
    private static Direction toOther(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == Part.FOOT ? facing : facing.getOpposite();
    }

    @Override
    protected BlockPos stationPos(BlockGetter level, BlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.FOOT ? pos : pos.relative(toOther(state));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos head = context.getClickedPos().relative(facing);
        Level level = context.getLevel();
        if (!level.getBlockState(head).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(head)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.FOOT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(PART, Part.HEAD), Block.UPDATE_ALL);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    // Пропала вторая часть — пропадает и эта (добычу даёт сломанная)
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
        return state.getValue(PART) == Part.FOOT ? new PotteryWheelBlockEntity(pos, state) : null;
    }
}
