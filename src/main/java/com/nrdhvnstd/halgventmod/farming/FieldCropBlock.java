package com.nrdhvnstd.halgventmod.farming;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/**
 * Грядка: растёт на пашне, как пшеница (8 стадий). Сажают сам урожай — зерно или овощ (ПКМ по пашне, в режиме
 * приключения тоже), срезают серпом или косой: зрелая — урожай, недозрелая — только посаженное (таблица добычи).
 */
public class FieldCropBlock extends CropBlock {
    private final Supplier<? extends Item> seed;
    private final MapCodec<FieldCropBlock> codec;

    public FieldCropBlock(Properties properties, Supplier<? extends Item> seed) {
        super(properties);
        this.seed = seed;
        this.codec = simpleCodec(p -> new FieldCropBlock(p, seed));
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return codec;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }
}
