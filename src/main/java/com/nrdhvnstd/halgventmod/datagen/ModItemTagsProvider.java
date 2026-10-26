package com.nrdhvnstd.halgventmod.datagen;

import java.util.concurrent.CompletableFuture;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.sound.SoundTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Теги предметов мода: data/halgventmod/tags/item/*.json */
public class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                               CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, HalgventMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Как звучит удар и замах оружием ⚠️
        tag(SoundTags.BLADE).addTag(ItemTags.SWORDS).addTag(ItemTags.AXES);
        tag(SoundTags.POINT).add(Items.TRIDENT);
        tag(SoundTags.BLUNT).add(Items.MACE);
        tag(SoundTags.HEAVY_WEAPON).addTag(ItemTags.AXES).add(Items.MACE);
        // Что достаётся из ножен со звуком клинка
        tag(SoundTags.SHEATHED).addTag(ItemTags.SWORDS);
        // Свои лопата и топор — тоже лопата и топор: ил, куча углежога, колода для колки, звуки удара, зачарования
        tag(ItemTags.SHOVELS).add(ModItems.SHOVEL.get());
        tag(ItemTags.AXES).add(ModItems.AXE.get());
    }
}
