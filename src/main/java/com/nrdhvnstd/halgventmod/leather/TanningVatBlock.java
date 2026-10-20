package com.nrdhvnstd.halgventmod.leather;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Дубильный чан: деревянная лохань (модель Conquest Reforged). Над огнём кипит. */
public class TanningVatBlock extends StationBlock {
    public static final MapCodec<TanningVatBlock> CODEC = simpleCodec(TanningVatBlock::new);
    private static final VoxelShape SHAPE = Shapes.join(Block.box(0, 0, 0, 16, 11.5, 16),
            Block.box(1.5, 1.5, 1.5, 14.5, 11.5, 14.5), BooleanOp.ONLY_FIRST);

    public TanningVatBlock(Properties properties) {
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
        return new TanningVatBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.TANNING_VAT.get(), TanningVatBlockEntity::serverTick);
    }

    /** Пар над кипящим чаном. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof TanningVatBlockEntity vat && vat.solution() != Solution.NONE
                && TanningVatBlockEntity.heated(level, pos) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.8,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.03, 0);
        }
    }
}
