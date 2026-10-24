package com.nrdhvnstd.halgventmod.smithing;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.pottery.Cube2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Сыродутный горн — массивная глиняная шахта 2×2 в основании и в три блока высотой ({@link Cube2}, {@link #PARTS}
 * частей; модель — tools/bloomery_models.sh: нижние два яруса рисует главная часть, третий — часть 8 над ней).
 * Щелчки по любой части идут главной; сломать любую — уходит весь горн (добычу даёт главная).
 */
public class BloomeryBlock extends SmithStationBlock.Lit {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 11);
    public static final int PARTS = 12;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public BloomeryBlock(Properties properties) {
        super(properties, SHAPE, BloomeryBlockEntity::new, null);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        int part = state.getValue(PART);
        return part == 0 || part == Cube2.THIRD_TIER ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockPos stationPos(BlockGetter level, BlockState state, BlockPos pos) {
        return Cube2.main(state, pos, PART);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null || !Cube2.fits(context, state.getValue(FACING), PARTS) ? null : state.setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Cube2.place(level, pos, state, PART, PARTS);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        Cube2.removed(this, state, level, pos, newState, PART, PARTS);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Cube2.creativeBreak(this, level, pos, state, player, PART);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? super.newBlockEntity(pos, state) : null;
    }

    /** Дым и искры — из колошника наверху шахты, огоньки — у устья спереди (только у главной части). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != 0 || !state.getValue(LIT)) {
            return;
        }
        double[] center = Cube2.center(state, pos);
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, center[0] + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 3.0,
                center[1] + (random.nextDouble() - 0.5) * 0.4, 0, 0.07, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LAVA, center[0], pos.getY() + 2.95, center[1], 0, 0, 0);
        }
        if (random.nextInt(3) == 0) {
            Direction facing = state.getValue(FACING);
            double x = center[0] + facing.getStepX() * 0.95 + (random.nextDouble() - 0.5) * 0.4;
            double z = center[1] + facing.getStepZ() * 0.95 + (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 0.3, z, 0, 0.01, 0);
        }
    }
}
