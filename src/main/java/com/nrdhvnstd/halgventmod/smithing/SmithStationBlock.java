package com.nrdhvnstd.halgventmod.smithing;

import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Станция кузнечества: всё на E, как у остальных станций (StationBlock). Своя форма, своя блок-сущность;
 * у горящих ({@link Lit}) — состояние «горит» с дымом и искрами, у тикающих — свой тик на сервере.
 */
public class SmithStationBlock extends StationBlock {
    private final VoxelShape shape;
    private final BiFunction<BlockPos, BlockState, BlockEntity> factory;
    @Nullable
    private final java.util.function.Supplier<? extends BlockEntityType<?>> ticking;

    public SmithStationBlock(Properties properties, VoxelShape shape, BiFunction<BlockPos, BlockState, BlockEntity> factory,
                             @Nullable java.util.function.Supplier<? extends BlockEntityType<?>> ticking) {
        super(properties);
        this.shape = shape;
        this.factory = factory;
        this.ticking = ticking;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> this); // станции не создаются из данных: кодек только для порядка
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || ticking == null || type != ticking.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof Ticking tick) {
                tick.serverTick(lvl, pos, st);
            }
        };
    }

    /** Блок-сущность, которой нужен тик на сервере. */
    public interface Ticking {
        void serverTick(Level level, BlockPos pos, BlockState state);
    }

    /** Станция, которая горит: горн, купель, угольная куча, цементационный ящик. */
    public static class Lit extends SmithStationBlock {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public Lit(Properties properties, VoxelShape shape, BiFunction<BlockPos, BlockState, BlockEntity> factory,
                   @Nullable java.util.function.Supplier<? extends BlockEntityType<?>> ticking) {
            super(properties, shape, factory, ticking);
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(LIT);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(LIT)) {
                return;
            }
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + shapeTop(state, level, pos);
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.04, 0);
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            }
        }

        private double shapeTop(BlockState state, Level level, BlockPos pos) {
            return getShape(state, level, pos, CollisionContext.empty()).max(net.minecraft.core.Direction.Axis.Y);
        }
    }
}
