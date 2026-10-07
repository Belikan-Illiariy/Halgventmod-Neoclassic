package com.nrdhvnstd.halgventmod.datagen;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModPaintings;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class DataGenerators {
    private DataGenerators() {}

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // Картины: data/halgventmod/painting_variant/*.json
        DatapackBuiltinEntriesProvider datapackEntries = generator.addProvider(event.includeServer(),
                new DatapackBuiltinEntriesProvider(output, lookupProvider,
                        new RegistrySetBuilder().add(Registries.PAINTING_VARIANT, ModPaintings::bootstrap),
                        Set.of(HalgventMod.MODID)));

        // Тег placeable: картины мода могут выпасть при установке обычной картины.
        // Берёт реестр вместе с только что описанными картинами.
        generator.addProvider(event.includeServer(),
                new ModPaintingTagsProvider(output, datapackEntries.getRegistryProvider(), existingFileHelper));

        // Модели предметов и переводы
        generator.addProvider(event.includeClient(), new ModItemModelProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(output, "ru_ru"));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(output, "en_us"));
    }
}