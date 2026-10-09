package com.nrdhvnstd.halgventmod.client.armor;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Собранный вид надетой брони: рецепты склеек, пути деталей и плаща.
 * Игроки рисуются каждый кадр, а одеваются редко — поэтому всё это считается один раз
 * после изменения брони (ArmorInventory.version) и хранится прямо на ней.
 */
final class ArmorLook {
    /** Выступающая деталь: модель .geo.json и её текстура. */
    record Detail(ResourceLocation model, ResourceLocation texture) {}

    private static final String CAPE_SUFFIX = "_cape";

    private final int version;
    @Nullable private final ArmorCompositor.Recipe legsRecipe;
    @Nullable private final ArmorCompositor.Recipe outerRecipe;
    @Nullable private ArmorCompositor.Composite legs;
    @Nullable private ArmorCompositor.Composite outer;
    final List<Detail> details;
    @Nullable final ResourceLocation capeTexture; // null — плаща нет
    final int capeColor;

    static ArmorLook of(ArmorInventory armor) {
        if (armor.renderCache() instanceof ArmorLook look && look.version == armor.version()) {
            return look;
        }
        ArmorLook look = new ArmorLook(armor);
        armor.setRenderCache(look);
        return look;
    }

    private ArmorLook(ArmorInventory armor) {
        version = armor.version();
        legsRecipe = ArmorCompositor.recipe(armor, ArmorSlot.Plane.LEGS);
        outerRecipe = ArmorCompositor.recipe(armor, ArmorSlot.Plane.OUTER);

        List<Detail> found = new ArrayList<>();
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = armor.get(slot);
            ModArmor.ArmorInfo info = ModArmor.get(stack);
            if (info == null) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            for (String name : info.models()) {
                found.add(new Detail(path(itemId, slot, "models/armor/", ".geo.json", name),
                        path(itemId, slot, "textures/armor/", ".png", name)));
            }
        }
        details = List.copyOf(found);

        // Плащ: textures/armor/cloak/<id>_cape.png, цвет — как у окрашенной накидки
        ItemStack cloak = armor.get(ArmorSlot.CLOAK);
        ModArmor.ArmorInfo info = ModArmor.get(cloak);
        if (info != null && info.cloak() != null && info.cloak().hasCape()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(cloak.getItem());
            capeTexture = path(itemId, ArmorSlot.CLOAK, "textures/armor/", ".png", itemId.getPath() + CAPE_SUFFIX);
            capeColor = info.dyeable() ? ArmorCompositor.color(cloak) : ArmorCompositor.Layer.NO_COLOR;
        } else {
            capeTexture = null;
            capeColor = ArmorCompositor.Layer.NO_COLOR;
        }
    }

    /** Склейка плоскости или null, если на ней ничего не надето. */
    @Nullable
    ArmorCompositor.Composite composite(ArmorSlot.Plane plane) {
        if (plane == ArmorSlot.Plane.LEGS) {
            legs = fresh(legs, legsRecipe);
            return legs;
        }
        outer = fresh(outer, outerRecipe);
        return outer;
    }

    // Склейку могли освободить (давно не рисовалась, F3+T) — тогда берём из кэша заново
    @Nullable
    private static ArmorCompositor.Composite fresh(@Nullable ArmorCompositor.Composite composite,
                                                   @Nullable ArmorCompositor.Recipe recipe) {
        if (recipe == null) {
            return null;
        }
        if (composite == null || composite.released()) {
            return ArmorCompositor.get(recipe);
        }
        composite.touch();
        return composite;
    }

    // models/armor/<папка слота>/<имя>.geo.json и textures/armor/<папка слота>/<имя>.png
    private static ResourceLocation path(ResourceLocation itemId, ArmorSlot slot, String folder, String extension, String name) {
        return ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), folder + slot.folder() + "/" + name + extension);
    }
}
