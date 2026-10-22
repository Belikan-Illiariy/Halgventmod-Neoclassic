package com.nrdhvnstd.halgventmod.cooking;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Костёр: тот же очаг (дрова в ячейках, огниво, горшок, жарка на углях), только на земле — модель костра Conquest,
 * горшок на огне рисует CookingRenderers. Поставленный ванильный костёр сам становится таким (CookingEvents).
 * Перегонный куб на костёр не ставят.
 */
public class FirePitBlock extends HearthBlock {
    public static final MapCodec<FirePitBlock> CODEC = simpleCodec(FirePitBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 7, 16);

    public FirePitBlock(Properties properties) {
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
        return HearthBlockEntity.firePit(pos, state);
    }

    @Override
    protected BlockEntityType<HearthBlockEntity> entityType() {
        return ModBlockEntities.FIRE_PIT.get();
    }
}
