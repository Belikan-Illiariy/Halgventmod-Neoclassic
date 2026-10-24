package com.nrdhvnstd.halgventmod.pottery;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Бочка для замеса глины (бочки Conquest Reforged): {@link #FILL} — что в ней видно: пустая, только вода или глина.
 * Бочки, поставленные до этого, — с глиной, пока их не тронут.
 */
public class ClayBarrelBlock extends StationBlock {
    public static final MapCodec<ClayBarrelBlock> CODEC = simpleCodec(ClayBarrelBlock::new);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 2);
    public static final int EMPTY = 0, WATER = 1, CLAY = 2;

    public ClayBarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FILL, CLAY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(FILL, EMPTY);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClayBarrelBlockEntity(pos, state);
    }
}
