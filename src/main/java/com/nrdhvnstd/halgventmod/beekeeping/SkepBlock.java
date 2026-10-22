package com.nrdhvnstd.halgventmod.beekeeping;

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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Улей — плетёная колода (модель Conquest Reforged). Жужжит и роится, когда в нём живут пчёлы. */
public class SkepBlock extends StationBlock {
    public static final MapCodec<SkepBlock> CODEC = simpleCodec(SkepBlock::new);
    private static final VoxelShape SHAPE = Block.box(3.5, 0, 3.5, 12.5, 8, 12.5);

    public SkepBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkepBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.SKEP.get(), SkepBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof SkepBlockEntity skep) || !skep.hasBees() || level.isNight()) {
            return;
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FALLING_NECTAR, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.3 + random.nextDouble() * 0.4, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0, 0);
        }
        if (random.nextInt(25) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, SoundEvents.BEEHIVE_WORK,
                    SoundSource.BLOCKS, 0.5F, 1.0F, false);
        }
    }
}
