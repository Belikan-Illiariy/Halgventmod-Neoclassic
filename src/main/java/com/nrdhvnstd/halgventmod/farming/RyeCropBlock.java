package com.nrdhvnstd.halgventmod.farming;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/** Рожь: растёт на пашне, как пшеница (8 стадий; модели — ячмень Conquest Reforged). Из неё — ржаная мука и чёрный хлеб. */
public class RyeCropBlock extends CropBlock {
    public static final MapCodec<RyeCropBlock> CODEC = simpleCodec(RyeCropBlock::new);

    public RyeCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.RYE_SEEDS.get();
    }
}
