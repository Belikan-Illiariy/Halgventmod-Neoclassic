package com.nrdhvnstd.halgventmod.cooking;

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

/** Ручной жёрнов (модель Conquest Reforged): мелет зерно в муку. */
public class QuernBlock extends StationBlock {
    public static final MapCodec<QuernBlock> CODEC = simpleCodec(QuernBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 9, 14);

    public QuernBlock(Properties properties) {
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
        return new QuernBlockEntity(pos, state);
    }
}
