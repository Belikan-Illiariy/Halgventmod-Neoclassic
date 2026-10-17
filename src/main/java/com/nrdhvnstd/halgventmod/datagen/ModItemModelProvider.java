package com.nrdhvnstd.halgventmod.datagen;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Модели предметов мода. */
public class ModItemModelProvider extends ItemModelProvider {
    private static final ExistingFileHelper.ResourceType TEXTURE =
            new ExistingFileHelper.ResourceType(PackType.CLIENT_RESOURCES, ".png", "textures");

    private final ExistingFileHelper files;

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, HalgventMod.MODID, existingFileHelper);
        this.files = existingFileHelper;
    }

    @Override
    protected void registerModels() {
        // Предметы: иконка берётся из папки своей категории
        for (ModItems.Definition def : ModItems.definitions()) {
            categoryItem(def);
        }
    }

    private void categoryItem(ModItems.Definition def) {
        if (def.item().get() instanceof BlockItem) {
            return; // у предметов-блоков модель будет браться от блока
        }
        String id = def.item().getId().getPath();
        ResourceLocation texture = modLoc("item/" + def.category().folder() + "/" + id);
        if (!files.exists(texture, TEXTURE)) {
            // Иконка ещё не нарисована или лежит не в той папке: не падаем, просто пропускаем
            HalgventMod.LOGGER.warn("Нет иконки {}, модель предмета пропущена", texture);
            return;
        }
        // Оружие держится в руке наискосок, как меч; остальное — как обычный предмет
        ResourceLocation parent = mcLoc(def.category().handheld() ? "item/handheld" : "item/generated");
        withExistingParent(id, parent).texture("layer0", texture);
    }
}