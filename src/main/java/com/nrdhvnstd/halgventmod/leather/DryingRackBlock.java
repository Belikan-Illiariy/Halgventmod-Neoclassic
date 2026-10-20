package com.nrdhvnstd.halgventmod.leather;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Рама для сушки: стойка для дубления из Conquest Reforged. Пустая — только рама, с работой —
 * натянутая на шнуровку шкура (свойство loaded переключает модель).
 */
public class DryingRackBlock extends StationBlock {
    public static final MapCodec<DryingRackBlock> CODEC = simpleCodec(DryingRackBlock::new);
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");
    private static final VoxelShape SHAPE_NS = Block.box(0, 0, 5, 16, 24, 11);
    private static final VoxelShape SHAPE_EW = Block.box(5, 0, 0, 11, 24, 16);

    public DryingRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LOADED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOADED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DryingRackBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.DRYING_RACK.get(), DryingRackBlockEntity::serverTick);
    }

    /** С мокрой кожи капает. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LOADED) && level.getBlockEntity(pos) instanceof DryingRackBlockEntity rack
                && !rack.isDry() && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.15 + random.nextDouble() * 0.7,
                    pos.getY() + 0.3, pos.getZ() + 0.5, 0, 0, 0);
        }
    }
}
