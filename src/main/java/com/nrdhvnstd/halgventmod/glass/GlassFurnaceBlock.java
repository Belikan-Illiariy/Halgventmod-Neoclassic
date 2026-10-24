package com.nrdhvnstd.halgventmod.glass;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.pottery.Cube2;

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
 * Стекловаренная печь — 2×2×2 ({@link Cube2}): кирпичный колпак на каменном цоколе, по кругу рабочие окна (горящая —
 * светятся), внизу спереди топка, наверху дымник (модель — tools/glass_furnace_models.sh). Горит — светится, дымит,
 * трещит.
 */
public class GlassFurnaceBlock extends StationBlock {
    public static final MapCodec<GlassFurnaceBlock> CODEC = simpleCodec(GlassFurnaceBlock::new);
    public static final IntegerProperty PART = Cube2.PART;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public GlassFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, 0).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART, LIT);
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
        return state.getValue(PART) == 0 ? new GlassFurnaceBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, GlassBlocks.GLASS_FURNACE_ENTITY.get(), GlassFurnaceBlockEntity::serverTick);
    }

    /** Треск у топки, огоньки в рабочих окнах, дым из дымника посередине (только у главной части). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != 0 || !state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double[] center = Cube2.center(state, pos);
        if (random.nextInt(6) == 0) {
            level.playLocalSound(center[0], pos.getY() + 0.3, center[1], SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    1.0F, 0.8F + random.nextFloat() * 0.3F, false);
        }
        if (random.nextInt(3) == 0) {
            // Огонёк в переднем рабочем окне
            double x = center[0] + facing.getStepX() * 0.85 + (random.nextDouble() - 0.5) * 0.3;
            double z = center[1] + facing.getStepZ() * 0.85 + (random.nextDouble() - 0.5) * 0.3;
            level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 1.15, z, 0, 0.01, 0);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, center[0] + (random.nextDouble() - 0.5) * 0.2,
                    pos.getY() + 2.05, center[1] + (random.nextDouble() - 0.5) * 0.2, 0, 0.06, 0);
        }
    }
}
