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
    /** Выступающая деталь: модель .geo.json, её текстура и поднято ли забрало. */
    static final class Detail {
        private final ResourceLocation model;
        private final ResourceLocation texture;
        @Nullable private final ArmorCompositor.PieceRecipe recipe; // вещь с покрытием: текстура склеивается
        @Nullable private ArmorCompositor.Composite composite;
        private final boolean visorRaised;

        private Detail(ResourceLocation model, ResourceLocation texture, @Nullable ArmorCompositor.PieceRecipe recipe,
                       boolean visorRaised) {
            this.model = model;
            this.texture = texture;
            this.recipe = recipe;
            this.visorRaised = visorRaised;
        }

        ResourceLocation model() {
            return model;
        }

        /** Забрало поднято: кость visor модели поворачивается (ArmorLayer). */
        boolean visorRaised() {
            return visorRaised;
        }

        /** Текстура как нарисована или склейка «основа → покрытие → overlay». */
        ResourceLocation texture() {
            if (recipe == null) {
                return texture;
            }
            composite = fresh(composite, recipe);
            return composite.texture();
        }
    }

    private static final String CAPE_SUFFIX = "_cape";

    private final int version;
    @Nullable private final ArmorCompositor.Recipe legsRecipe;
    @Nullable private final ArmorCompositor.Recipe outerRecipe;
    @Nullable private ArmorCompositor.Composite legs;
    @Nullable private ArmorCompositor.Composite outer;
    final List<Detail> details;
    @Nullable final ResourceLocation capeTexture; // null — плаща нет
    @Nullable private final ArmorCompositor.PieceRecipe capeRecipe; // окрашенный плащ: текстура склеивается
    @Nullable private ArmorCompositor.Composite cape;

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
            boolean visorRaised = ModArmor.visorRaised(stack);
            for (String name : info.models()) {
                ResourceLocation texture = path(itemId, slot, "textures/armor/", ".png", name);
                // Деталь окрашиваемой вещи — та же основа, что и плоская текстура: у ткани (серая) красится тем же цветом,
                // у вещи с покрытием — своё покрытие; неокрашиваемое — в <деталь>_overlay.png (файла может не быть).
                // Непокрашенной ткани склейка не нужна (как у плаща); деталь неокрашиваемой вещи — как нарисована
                boolean painted = info.dye() == ModArmor.Dye.COATED
                        || info.dye() == ModArmor.Dye.CLOTH && ArmorCompositor.color(stack) != ArmorCompositor.Layer.NO_COLOR;
                ArmorCompositor.PieceRecipe recipe = painted
                        ? new ArmorCompositor.PieceRecipe(ArmorCompositor.layer(stack, texture))
                        : null;
                found.add(new Detail(path(itemId, slot, "models/armor/", ".geo.json", name), texture, recipe, visorRaised));
            }
        }
        details = List.copyOf(found);

        // Плащ: textures/armor/cloak/<id>_cape.png; окрашенный красится склейкой, как сама накидка
        ItemStack cloak = armor.get(ArmorSlot.CLOAK);
        ModArmor.ArmorInfo info = ModArmor.get(cloak);
        if (info != null && info.cloak() != null && info.cloak().hasCape()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(cloak.getItem());
            capeTexture = path(itemId, ArmorSlot.CLOAK, "textures/armor/", ".png", itemId.getPath() + CAPE_SUFFIX);
            capeRecipe = info.dyeable() && ArmorCompositor.color(cloak) != ArmorCompositor.Layer.NO_COLOR
                    ? new ArmorCompositor.PieceRecipe(ArmorCompositor.layer(cloak, capeTexture))
                    : null;
        } else {
            capeTexture = null;
            capeRecipe = null;
        }
    }

    /** Текстура плаща или null, если плаща нет. */
    @Nullable
    ResourceLocation cape() {
        if (capeRecipe == null) {
            return capeTexture;
        }
        cape = fresh(cape, capeRecipe);
        return cape.texture();
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
                                                   @Nullable ArmorCompositor.Key recipe) {
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
