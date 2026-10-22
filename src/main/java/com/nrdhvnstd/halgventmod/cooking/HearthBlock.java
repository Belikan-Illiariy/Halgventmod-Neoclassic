package com.nrdhvnstd.halgventmod.cooking;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Очаг: жаровня Conquest Reforged. Горит на дровах; на нём — горшок (модель с котлом) или жарка прямо на углях.
 * Над очагом дымно: коптильный крюк выше него коптит.
 */
public class HearthBlock extends StationBlock {
    public static final MapCodec<HearthBlock> CODEC = simpleCodec(HearthBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty POT = BooleanProperty.create("pot");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);
    private static final VoxelShape SHAPE_POT = Block.box(2, 0, 2, 14, 11, 14);
    private static final VoxelShape SHAPE_STILL = Block.box(1, 0, 1, 15, 16, 15); // низ перегонного куба стоит в огне

    public HearthBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(POT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, POT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockState(pos.above()).getBlock() instanceof com.nrdhvnstd.halgventmod.brewing.StillBlock) {
            return SHAPE_STILL;
        }
        return state.getValue(POT) ? SHAPE_POT : SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HearthBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.HEARTH.get(), HearthBlockEntity::serverTick);
    }

    /** Дым и треск огня; пар и чад над котлом; над жаркой — по готовности куска (подсказки, когда переворачивать). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(10) == 0) {
            level.playLocalSound(x, pos.getY() + 0.3, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.4,
                    z + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
        }
        if (!(level.getBlockEntity(pos) instanceof HearthBlockEntity hearth)) {
            return;
        }
        if (state.getValue(POT)) {
            if (hearth.stirDue()) {
                // Пригорает: густой чад и бульканье — пора мешать
                level.addParticle(ParticleTypes.LARGE_SMOKE, x, pos.getY() + 0.75, z, 0, 0.03, 0);
                if (random.nextInt(4) == 0) {
                    level.playLocalSound(x, pos.getY() + 0.6, z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
                            SoundSource.BLOCKS, 0.8F, 0.7F, false);
                }
            } else if (hearth.cooking() && random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.CLOUD, x + (random.nextDouble() - 0.5) * 0.2, pos.getY() + 0.75,
                        z + (random.nextDouble() - 0.5) * 0.2, 0, 0.04, 0);
            }
            return;
        }
        for (int cell = 0; cell < HearthBlockEntity.CELLS; cell++) {
            float done = hearth.downSide(cell);
            if (done < 0) {
                continue;
            }
            double px = pos.getX() + 0.5 + HearthBlockEntity.cellOffset(cell % 2);
            double pz = pos.getZ() + 0.5 + HearthBlockEntity.cellOffset(cell / 2);
            if (done >= HearthBlockEntity.OVER) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, px, pos.getY() + 0.45, pz, 0, 0.02, 0);
            } else if (done >= HearthBlockEntity.READY && random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.POOF, px, pos.getY() + 0.45, pz, 0, 0.03, 0);
            } else if (random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.SMOKE, px, pos.getY() + 0.45, pz, 0, 0.01, 0);
            }
            if (random.nextInt(12) == 0) {
                level.playLocalSound(px, pos.getY() + 0.4, pz, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                        0.08F, 1.6F + random.nextFloat() * 0.3F, false);
            }
        }
    }
}
