package com.nrdhvnstd.halgventmod.leather;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Кожевенная колода: большой пень (модель Conquest Reforged), на нём лежит шкура. */
public class FleshingBeamBlock extends StationBlock {
    public static final MapCodec<FleshingBeamBlock> CODEC = simpleCodec(FleshingBeamBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.5, 0, 0.5, 15.5, 16, 15.5);

    public FleshingBeamBlock(Properties properties) {
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FleshingBeamBlockEntity(pos, state);
    }
}
