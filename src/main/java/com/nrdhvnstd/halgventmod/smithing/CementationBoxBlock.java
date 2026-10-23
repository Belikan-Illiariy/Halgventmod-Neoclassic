package com.nrdhvnstd.halgventmod.smithing;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Цементационная печь в два блока: внизу топка (горит — видно жар), вверху камера с ящиком и трубой. Блок-сущность —
 * в нижней части; щелчок по верхней уходит ей ({@link #stationPos}). Сломали одну часть — пропадает и другая.
 */
public class CementationBoxBlock extends SmithStationBlock.Lit {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape UPPER = Block.box(1, 0, 1, 15, 16, 15);

    public CementationBoxBlock(Properties properties) {
        super(properties, LOWER, CementationBoxBlockEntity::new, () -> SmithingBlocks.CEMENTATION_ENTITY.get());
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    @Override
    protected BlockPos stationPos(BlockGetter level, BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        Level level = context.getLevel();
        if (above.getY() >= level.getMaxBuildHeight() || !level.getBlockState(above).canBeReplaced(context)) {
            return null;
        }
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER).setValue(LIT, false), Block.UPDATE_ALL);
        }
    }

    // Пропала вторая половина — пропадает и эта (добычу даёт нижняя)
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (direction == (lower ? Direction.UP : Direction.DOWN)) {
            boolean paired = neighbor.is(this) && neighbor.getValue(HALF) != state.getValue(HALF);
            if (!paired) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new CementationBoxBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.getTicker(level, state, type) : null;
    }
}
