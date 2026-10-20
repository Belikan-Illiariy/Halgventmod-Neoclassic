package com.nrdhvnstd.halgventmod.brewing;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.cooking.HearthBlock;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
 * Перегонный куб — медный куб со шлемом и змеевиком в бочке-холодильнике. Ставится над огнём; над очагом
 * (ON_HEARTH) модель опущена на 10 px и стоит прямо на углях — низ куба тогда в блоке очага, и очаг отдаёт
 * щелчки по нему кубу. Парит, пока гонит.
 */
public class StillBlock extends StationBlock {
    public static final MapCodec<StillBlock> CODEC = simpleCodec(StillBlock::new);
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    public static final BooleanProperty ON_HEARTH = BooleanProperty.create("on_hearth");
    private static final VoxelShape SHAPE = Block.box(0.5, 0, 0.5, 15.5, 13, 15.5);
    private static final VoxelShape SHAPE_LOW = Block.box(0.5, 0, 0.5, 15.5, 3, 15.5); // остальное — в блоке очага

    public StillBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FULL, false).setValue(ON_HEARTH, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FULL, ON_HEARTH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ON_HEARTH) ? SHAPE_LOW : SHAPE;
    }

    private static boolean hearthBelow(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof HearthBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(ON_HEARTH, hearthBelow(context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN ? state.setValue(ON_HEARTH, hearthBelow(level, pos)) : state;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StillBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.STILL.get(), StillBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FULL) && StillBlockEntity.heat(level, pos) > 0 && random.nextInt(2) == 0) {
            // Пар из шлема (над очагом шлем на 10 px ниже)
            double y = pos.getY() + (state.getValue(ON_HEARTH) ? 0.2 : 0.85);
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.35 + random.nextDouble() * 0.15, y,
                    pos.getZ() + 0.4 + random.nextDouble() * 0.15, 0, 0.04, 0);
        }
    }
}
