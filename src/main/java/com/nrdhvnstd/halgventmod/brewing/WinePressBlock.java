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

/** Давильня (винный пресс Conquest Reforged): из яблок и ягод — сусло. */
public class WinePressBlock extends StationBlock {
    public static final MapCodec<WinePressBlock> CODEC = simpleCodec(WinePressBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public WinePressBlock(Properties properties) {
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
        return new WinePressBlockEntity(pos, state);
    }
}
