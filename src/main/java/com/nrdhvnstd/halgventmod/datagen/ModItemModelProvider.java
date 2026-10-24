package com.nrdhvnstd.halgventmod.datagen;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.glass.Glassmaking;
import com.nrdhvnstd.halgventmod.leather.Animals;
import com.nrdhvnstd.halgventmod.leather.HideItem;
import com.nrdhvnstd.halgventmod.pottery.WareItem;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
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
        if (def.item().get() instanceof BlockItem && !(def.item().get() instanceof ItemNameBlockItem)) {
            return; // у предметов-блоков модель будет браться от блока; сажаемое (зерно, лук) — своя иконка
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
        ItemModelBuilder model = withExistingParent(id, parent).texture("layer0", texture);
        if (def.item().get() instanceof HideItem) {
            animalOverrides(model, id, def.category().folder());
        }
        if (def.item().get() instanceof WareItem ware && ware.glass()) {
            tintOverrides(model, id, def.category().folder());
        }
    }

    /** Выдувное стекло: своя иконка для каждого цвета (glass/&lt;предмет&gt;_&lt;цвет&gt;), лесное — основная. */
    private void tintOverrides(ItemModelBuilder model, String id, String folder) {
        for (Glassmaking.Tint tint : Glassmaking.Tint.values()) {
            ResourceLocation texture = modLoc("item/" + folder + "/" + id + "_" + tint.id());
            if (tint.ordinal() == 0 || !files.exists(texture, TEXTURE)) {
                continue;
            }
            ItemModelBuilder variant = withExistingParent("item/" + folder + "/" + id + "_" + tint.id(),
                    mcLoc("item/generated")).texture("layer0", texture);
            model.override().predicate(Glassmaking.TINT, tint.ordinal()).model(variant).end();
        }
    }

    /** Шкура и мех: своя иконка для каждого зверя (materials/hide/<предмет>_<зверь>), если она нарисована. */
    private void animalOverrides(ItemModelBuilder model, String id, String folder) {
        for (Animals.Kind kind : Animals.all()) {
            ResourceLocation texture = modLoc("item/" + folder + "/hide/" + id + "_" + kind.id());
            if (!files.exists(texture, TEXTURE)) {
                continue;
            }
            ItemModelBuilder variant = withExistingParent("item/hide/" + id + "_" + kind.id(), mcLoc("item/generated"))
                    .texture("layer0", texture);
            model.override().predicate(HideItem.ANIMAL, Animals.index(kind.id())).model(variant).end();
        }
    }
}