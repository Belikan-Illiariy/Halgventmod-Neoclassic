package com.nrdhvnstd.halgventmod.datagen;

import java.util.concurrent.CompletableFuture;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModPaintings;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Пишет data/minecraft/tags/painting_variant/placeable.json (без замены ванильного списка). */
public class ModPaintingTagsProvider extends TagsProvider<PaintingVariant> {
    public ModPaintingTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                   ExistingFileHelper existingFileHelper) {
        super(output, Registries.PAINTING_VARIANT, lookupProvider, HalgventMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        var placeable = tag(PaintingVariantTags.PLACEABLE);
        for (ModPaintings.Definition def : ModPaintings.definitions()) {
            placeable.add(def.variant());
        }
    }
}