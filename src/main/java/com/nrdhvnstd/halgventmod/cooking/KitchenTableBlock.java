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

/** Кухонный стол (деревенский стол Conquest Reforged): месят тесто и лепят пироги. */
public class KitchenTableBlock extends StationBlock {
    public static final MapCodec<KitchenTableBlock> CODEC = simpleCodec(KitchenTableBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public KitchenTableBlock(Properties properties) {
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
        return new KitchenTableBlockEntity(pos, state);
    }
}
