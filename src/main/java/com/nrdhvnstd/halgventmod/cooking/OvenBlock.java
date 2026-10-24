package com.nrdhvnstd.halgventmod.cooking;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.pottery.Cube2;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Печь — большая средневековая хлебная печь 2×2×2 ({@link Cube2}; модель — tools/oven_models.sh): каменный под с
 * нишей для дров, кирпичный свод, спереди устье, над ним дымоход. Топится дровами, печёт хлеб, пироги, сушит солод.
 * Щелчки по любой части идут главной; сломать любую — уходит вся печь.
 */
public class OvenBlock extends StationBlock {
    public static final MapCodec<OvenBlock> CODEC = simpleCodec(OvenBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final IntegerProperty PART = Cube2.PART;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public OvenBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(PART, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, PART);
    }

    @Override
    protected BlockPos stationPos(BlockGetter level, BlockState state, BlockPos pos) {
        return Cube2.main(state, pos);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == 0 ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null || !Cube2.fits(context, state.getValue(FACING)) ? null : state.setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Cube2.place(level, pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        Cube2.removed(this, state, level, pos, newState);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Cube2.creativeBreak(this, level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new OvenBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.OVEN.get(), OvenBlockEntity::serverTick);
    }

    /** Дым из дымохода; пахнет хлебом — пар из устья, подгорает — чёрный дым (только у главной части). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != 0) {
            return;
        }
        Direction front = state.getValue(FACING);
        double[] center = Cube2.center(state, pos);
        // Устье — посередине лица, дымоход — над ним
        double x = center[0] + front.getStepX() * 0.9;
        double z = center[1] + front.getStepZ() * 0.9;
        double mouth = pos.getY() + 1.05;
        if (state.getValue(LIT)) {
            double cx = center[0] + front.getStepX() * 0.55;
            double cz = center[1] + front.getStepZ() * 0.55;
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, pos.getY() + 2.0, cz, 0, 0.05, 0);
            }
            if (random.nextInt(12) == 0) {
                level.playLocalSound(x, mouth, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F, false);
            }
        }
        if (!(level.getBlockEntity(pos) instanceof OvenBlockEntity oven)) {
            return;
        }
        int band = oven.worstBand();
        if (band >= OvenBlockEntity.BAND_OVER) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, mouth + 0.1, z, 0, 0.03, 0);
        } else if (band == OvenBlockEntity.BAND_GOOD && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.POOF, x, mouth + 0.1, z, 0, 0.03, 0);
        }
    }
}
