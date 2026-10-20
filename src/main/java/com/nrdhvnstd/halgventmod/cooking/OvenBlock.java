package com.nrdhvnstd.halgventmod.cooking;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Хлебная печь: песчаниковая печь Conquest Reforged, топится дровами, печёт хлеб и пироги. */
public class OvenBlock extends StationBlock {
    public static final MapCodec<OvenBlock> CODEC = simpleCodec(OvenBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public OvenBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OvenBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.OVEN.get(), OvenBlockEntity::serverTick);
    }

    /** Дым из устья; пахнет хлебом — пар, подгорает — чёрный дым. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction front = state.getValue(FACING);
        double x = pos.getX() + 0.5 + front.getStepX() * 0.55;
        double y = pos.getY() + 0.6;
        double z = pos.getZ() + 0.5 + front.getStepZ() * 0.55;
        if (state.getValue(LIT)) {
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0, 0.02, 0);
            }
            if (random.nextInt(12) == 0) {
                level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F, false);
            }
        }
        if (!(level.getBlockEntity(pos) instanceof OvenBlockEntity oven)) {
            return;
        }
        int band = oven.worstBand();
        if (band >= OvenBlockEntity.BAND_OVER) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.1, z, 0, 0.03, 0);
        } else if (band == OvenBlockEntity.BAND_GOOD && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.POOF, x, y + 0.1, z, 0, 0.03, 0);
        }
    }
}
