package com.nrdhvnstd.halgventmod.farming;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ягодный куст (модели Conquest Reforged): 4 стадии, на последней — ягоды. Растёт на земле, траве, мху, грязи.
 * Собрали — снова зреет со второй стадии.
 */
public class BerryBushBlock extends FruitPlantBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 13, 15);
    // Меняйте здесь
    public static final float GROW_CHANCE = 0.25F;

    private final MapCodec<BerryBushBlock> codec;

    public BerryBushBlock(Properties properties, Supplier<? extends Item> berry) {
        super(properties, berry, 2, 4);
        this.codec = simpleCodec(p -> new BerryBushBlock(p, berry));
        registerDefaultState(stateDefinition.any().setValue(AGE, 3));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected IntegerProperty age() {
        return AGE;
    }

    @Override
    protected int maxAge() {
        return 3;
    }

    @Override
    protected int pickedAge() {
        return 1;
    }

    @Override
    protected float growChance() {
        return GROW_CHANCE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.DIRT) || below.is(BlockTags.SAND);
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighbor,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
}
