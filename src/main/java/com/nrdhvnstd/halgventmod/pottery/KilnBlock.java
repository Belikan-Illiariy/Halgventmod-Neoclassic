package com.nrdhvnstd.halgventmod.pottery;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
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

/**
 * Гончарный горн: на {@link #capacity()} вещей (сейчас один — купольный, DomeKilnBlock).
 * Горит — светится и дымит (при закрытой заслонке — чёрным дымом). Износился — разваливается (BROKEN), чинят кирпичом.
 */
public class KilnBlock extends StationBlock {
    public static final MapCodec<KilnBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.INT.fieldOf("capacity").forGetter(KilnBlock::capacity),
            Codec.FLOAT.fieldOf("height").forGetter(KilnBlock::height)
    ).apply(instance, KilnBlock::new));
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    private final int capacity;
    private final float height;

    /** @param height высота модели в блоках: оттуда идёт дым */
    public KilnBlock(Properties properties, int capacity, float height) {
        super(properties);
        this.capacity = capacity;
        this.height = height;
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(BROKEN, false));
    }

    public int capacity() {
        return capacity;
    }

    public float height() {
        return height;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, BROKEN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KilnBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.KILN.get(), KilnBlockEntity::serverTick);
    }

    /** Треск огня, искры у топки, дым из трубы: белый — заслонка открыта, чёрный — закрыта (томят в дыму). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(8) == 0) {
            level.playLocalSound(x, pos.getY() + 0.3, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.6F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.2,
                    z + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
        }
        boolean damper = !(level.getBlockEntity(pos) instanceof KilnBlockEntity kiln) || kiln.damperOpen();
        if (random.nextInt(2) == 0) {
            level.addParticle(damper ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE,
                    x + (random.nextDouble() - 0.5) * 0.2, pos.getY() + height, z + (random.nextDouble() - 0.5) * 0.2,
                    0, 0.05, 0);
        }
    }
}
