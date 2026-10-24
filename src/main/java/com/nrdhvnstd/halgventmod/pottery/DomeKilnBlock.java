package com.nrdhvnstd.halgventmod.pottery;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Купольный горн — 2×2×2 ({@link Cube2}): глиняный купол уступами над топкой, спереди устье, наверху продух
 * (модель — tools/dome_kiln_models.sh).
 */
public class DomeKilnBlock extends KilnBlock {
    public static final MapCodec<DomeKilnBlock> DOME_CODEC = simpleCodec(DomeKilnBlock::new);
    public static final IntegerProperty PART = Cube2.PART;
    // Меняйте здесь ⚠️
    public static final int CAPACITY = 10;   // окно горна держит не больше 10
    public static final float SMOKE_HEIGHT = 1.65F; // над продухом купола
    private static final VoxelShape LOWER = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape UPPER = Block.box(0, 0, 0, 16, 10, 16); // верх купола

    public DomeKilnBlock(Properties properties) {
        super(properties, CAPACITY, SMOKE_HEIGHT);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return DOME_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
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
        return state.getValue(PART) >= 4 ? UPPER : LOWER;
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
        return state.getValue(PART) == 0 ? new KilnBlockEntity(pos, state) : null;
    }

    /** Треск и искры у устья, дым — из продуха купола посередине горна (только у главной части). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != 0 || !state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double[] center = Cube2.center(state, pos);
        double cx = center[0];
        double cz = center[1];
        // Устье — у лица, посередине между главной и правой частью
        double mx = cx + facing.getStepX() * 0.95;
        double mz = cz + facing.getStepZ() * 0.95;
        if (random.nextInt(8) == 0) {
            level.playLocalSound(mx, pos.getY() + 0.3, mz, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.6F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, mx + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 0.25,
                    mz + (random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
        }
        boolean damper = !(level.getBlockEntity(pos) instanceof KilnBlockEntity kiln) || kiln.damperOpen();
        if (random.nextInt(2) == 0) {
            level.addParticle(damper ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.LARGE_SMOKE,
                    cx + (random.nextDouble() - 0.5) * 0.2, pos.getY() + SMOKE_HEIGHT, cz + (random.nextDouble() - 0.5) * 0.2,
                    0, 0.05, 0);
        }
    }
}
