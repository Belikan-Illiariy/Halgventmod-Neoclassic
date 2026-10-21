package com.nrdhvnstd.halgventmod.cooking;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Засолочная бочка (ванильная бочка на вид): мясо и рыба с солью за несколько суток становятся солониной. */
public class BrineBarrelBlock extends StationBlock {
    public static final MapCodec<BrineBarrelBlock> CODEC = simpleCodec(BrineBarrelBlock::new);

    public BrineBarrelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrineBarrelBlockEntity(pos, state);
    }
}
