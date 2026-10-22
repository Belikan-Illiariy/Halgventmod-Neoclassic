package com.nrdhvnstd.halgventmod.pottery;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Полка для сырца — свой еловый стеллаж в два яруса (models/block/drying_shelf.json), лицом к ставившему;
 * на ярусах сохнут до {@link DryingShelfBlockEntity#SLOTS} сырцов.
 */
public class DryingShelfBlock extends StationBlock {
    public static final MapCodec<DryingShelfBlock> CODEC = simpleCodec(DryingShelfBlock::new);
    // Стеллаж неглубокий: вдоль лица — во всю ширину, вглубь — 10 пикселей посередине
    private static final VoxelShape NORTH = Block.box(0, 0, 3, 16, 16, 13);
    private static final VoxelShape EAST = Block.box(3, 0, 0, 13, 16, 16);
    private static final VoxelShape SOUTH = NORTH;
    private static final VoxelShape WEST = EAST;

    public DryingShelfBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DryingShelfBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.DRYING_SHELF.get(), DryingShelfBlockEntity::serverTick);
    }
}
