package com.nrdhvnstd.halgventmod.pottery;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Бочка для замеса глины (бочка с глиной Conquest Reforged). */
public class ClayBarrelBlock extends StationBlock {
    public static final MapCodec<ClayBarrelBlock> CODEC = simpleCodec(ClayBarrelBlock::new);

    public ClayBarrelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClayBarrelBlockEntity(pos, state);
    }
}
