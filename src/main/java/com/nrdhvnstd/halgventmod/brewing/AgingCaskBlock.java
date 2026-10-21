package com.nrdhvnstd.halgventmod.brewing;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Бочка для выдержки — лежачая винная бочка Conquest Reforged. */
public class AgingCaskBlock extends StationBlock {
    public static final MapCodec<AgingCaskBlock> CODEC = simpleCodec(AgingCaskBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public AgingCaskBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @javax.annotation.Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                com.nrdhvnstd.halgventmod.registry.ModBlockEntities.AGING_CASK.get(), AgingCaskBlockEntity::serverTick);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AgingCaskBlockEntity(pos, state);
    }
}
