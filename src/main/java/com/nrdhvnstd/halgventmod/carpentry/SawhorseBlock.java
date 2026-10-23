package com.nrdhvnstd.halgventmod.carpentry;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Пильные козлы (своя модель из ванильной ели): брус на двух парах ног крест-накрест. Бревно ложится вдоль бруса —
 * поперёк взгляда того, кто поставил козлы.
 */
public class SawhorseBlock extends StationBlock {
    public static final MapCodec<SawhorseBlock> CODEC = simpleCodec(SawhorseBlock::new);
    private static final VoxelShape ALONG_X = Block.box(0, 0, 2, 16, 14, 14);
    private static final VoxelShape ALONG_Z = Block.box(2, 0, 0, 14, 14, 16);

    public SawhorseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Вдоль какой оси лежит брус (и бревно на нём). */
    public static Direction.Axis beamAxis(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return beamAxis(state) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SawhorseBlockEntity(pos, state);
    }
}
