package com.nrdhvnstd.halgventmod.datagen;

import java.util.concurrent.CompletableFuture;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.farming.FarmingRules;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Теги блоков мода: data/halgventmod/tags/block/*.json */
public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, HalgventMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Что срезают серп и коса: все ванильные посевы (с их стеблями), а также тыквы и арбузы
        tag(FarmingRules.HARVESTABLE)
                .addTag(BlockTags.CROPS)
                .add(Blocks.PUMPKIN, Blocks.MELON);

        // По чему кликают семенами, чтобы посадить их в режиме приключения
        tag(FarmingRules.PLANTABLE_SOIL)
                .add(Blocks.FARMLAND);
    }
}
