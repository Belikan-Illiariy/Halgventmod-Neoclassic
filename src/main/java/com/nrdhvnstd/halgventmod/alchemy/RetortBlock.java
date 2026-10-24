package com.nrdhvnstd.halgventmod.alchemy;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.brewing.StillBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlock;

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
 * Реторта — глиняная колба с длинным носом, опущенным в приёмник. Ставится над огнём (как перегонный куб: горящий
 * очаг, костёр, огонь); пока возгоняет (FULL и есть жар) — из носа курится.
 */
public class RetortBlock extends StationBlock {
    public static final MapCodec<RetortBlock> CODEC = simpleCodec(RetortBlock::new);
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public RetortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FULL, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FULL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RetortBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, AlchemyBlocks.RETORT_ENTITY.get(), RetortBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FULL) && StillBlockEntity.heat(level, pos) > 0 && random.nextInt(3) == 0) {
            // Дымок у горла приёмника: нос смотрит туда, куда повёрнута реторта
            Direction facing = state.getValue(FACING);
            double x = pos.getX() + 0.5 + facing.getStepX() * 0.3;
            double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.3;
            level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 0.6, z, 0, 0.02, 0);
        }
    }
}
