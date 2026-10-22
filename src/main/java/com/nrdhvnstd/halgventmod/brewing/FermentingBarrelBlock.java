package com.nrdhvnstd.halgventmod.brewing;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Бродильная бочка (открытая бочка Conquest Reforged: видно, сколько налито). SEALED — запечатана: сверху крышка
 * (models/block/fermenting_barrel_sealed), только так и бродит. Пока бродит — из-под крышки пузырится.
 */
public class FermentingBarrelBlock extends StationBlock {
    public static final MapCodec<FermentingBarrelBlock> CODEC = simpleCodec(FermentingBarrelBlock::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public FermentingBarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0).setValue(SEALED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL, SEALED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FermentingBarrelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.FERMENTING_BARREL.get(), FermentingBarrelBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FermentingBarrelBlockEntity barrel && barrel.fermenting()
                && random.nextInt(3) == 0) {
            double y = state.getValue(SEALED) ? pos.getY() + 1.02 : pos.getY() + 0.25 + 0.22 * state.getValue(LEVEL);
            level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.25 + random.nextDouble() * 0.5, y,
                    pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0, 0.02, 0);
        }
    }
}
