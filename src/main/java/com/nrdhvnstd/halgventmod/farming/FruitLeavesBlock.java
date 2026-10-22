package com.nrdhvnstd.halgventmod.farming;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Плодовая листва (листва фруктовых деревьев и виноградная лоза Conquest Reforged): 8 стадий, на последней — плоды.
 * Не опадает: сад строят строители из стволов и этой листвы. Собрали — плоды завязываются заново.
 */
public class FruitLeavesBlock extends FruitPlantBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    // Меняйте здесь
    public static final float GROW_CHANCE = 0.3F;

    private final MapCodec<FruitLeavesBlock> codec;

    public FruitLeavesBlock(Properties properties, Supplier<? extends Item> fruit) {
        super(properties, fruit, 1, 3);
        this.codec = simpleCodec(p -> new FruitLeavesBlock(p, fruit));
        registerDefaultState(stateDefinition.any().setValue(AGE, 7));
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
        return 7;
    }

    @Override
    protected int pickedAge() {
        return 0;
    }

    @Override
    protected float growChance() {
        return GROW_CHANCE;
    }
}
