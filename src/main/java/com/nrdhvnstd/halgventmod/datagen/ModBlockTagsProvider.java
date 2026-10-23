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
        // Рожь — посев, как пшеница: ванильные правила посевов, серп и коса
        tag(BlockTags.CROPS).add(ModBlocks.RYE.get());
        tag(BlockTags.MAINTAINS_FARMLAND).add(ModBlocks.RYE.get());
        // Остальные грядки — так же
        for (var crop : java.util.List.of(ModBlocks.BARLEY_CROP, ModBlocks.OATS_CROP, ModBlocks.ONION_CROP,
                ModBlocks.GARLIC_CROP, ModBlocks.TURNIP_CROP, ModBlocks.BEANS_CROP, ModBlocks.CABBAGE_CROP, ModBlocks.FLAX_CROP)) {
            tag(BlockTags.CROPS).add(crop.get());
            tag(BlockTags.MAINTAINS_FARMLAND).add(crop.get());
        }

        // Плодовая листва и лоза считаются листвой (ломаются мотыгой, как листва)
        tag(BlockTags.LEAVES).add(ModBlocks.CHERRY_LEAVES.get(), ModBlocks.PLUM_LEAVES.get(), ModBlocks.PEAR_LEAVES.get(),
                ModBlocks.ORANGE_LEAVES.get(), ModBlocks.OLIVE_LEAVES.get(), ModBlocks.GRAPE_VINE.get(),
                ModBlocks.APPLE_LEAVES.get());

        // Траву и папоротник серп больше не косит — травы с них срезают, ничего не ломая (herbs/HerbGathering)
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
        // Ткацкий станок — тоже
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.LOOM.get());

        // Готовка: каменное — киркой, бочка — топором
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.HEARTH.get(), ModBlocks.BAKING_OVEN.get(), ModBlocks.QUERN.get(), ModBlocks.SMOKING_HOOK.get());
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.BRINE_BARREL.get(), ModBlocks.KITCHEN_TABLE.get(), ModBlocks.SKEP.get(), ModBlocks.FIRE_PIT.get(),
                        ModBlocks.WINE_PRESS.get(), ModBlocks.FERMENTING_BARREL.get(), ModBlocks.AGING_CASK.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.STILL.get());

        // Гончар: горны — киркой, деревянное — топором
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.SMALL_KILN.get(), ModBlocks.TALL_KILN.get());
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.CLAY_BARREL.get(), ModBlocks.POTTERY_WHEEL.get(), ModBlocks.DRYING_SHELF.get());

        // Плотник: всё деревянное — топором
        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.CHOPPING_BLOCK.get(), ModBlocks.SAWHORSE.get(), ModBlocks.CARPENTER_BENCH.get());
    }
}
