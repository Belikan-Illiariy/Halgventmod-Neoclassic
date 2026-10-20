package com.nrdhvnstd.halgventmod.datagen;

import java.util.concurrent.CompletableFuture;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.farming.FarmingRules;
import com.nrdhvnstd.halgventmod.registry.ModBlocks;

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

        // Станции кожевника — рубят топором
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.FLESHING_BEAM.get(), ModBlocks.TANNING_VAT.get(),
                        ModBlocks.DRYING_RACK.get(), ModBlocks.LEATHER_TABLE.get());
    }
}
