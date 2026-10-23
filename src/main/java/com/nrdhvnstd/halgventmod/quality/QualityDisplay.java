package com.nrdhvnstd.halgventmod.quality;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.item.ItemStack;

/**
 * Качество для плашки рядом с признаками — одна шкала для всего: Ужасный … Шедевр (GearQuality.Grade).
 * Снаряжение — своё качество, еда — качество приготовления (0–5, Посредственное — 2), кожа — качество кожи
 * (0–4, сдвиг на одну ступень, как у вещей со стола кожевника), гончарное и деревянное с верстака плотника — 0–5,
 * как у еды. Редкость предмета
 * качество не трогает.
 */
public final class QualityDisplay {
    private static final List<String> RARITY = List.of(
            ModTraits.COMMON.id(), ModTraits.UNCOMMON.id(), ModTraits.RARE.id(), ModTraits.UNIQUE.id(),
            ModTraits.LEGENDARY.id(), ModTraits.MYTHICAL.id());

    private QualityDisplay() {}

    /** Качество для плашки или null — у вещи его нет. */
    @Nullable
    public static GearQuality.Grade of(ItemStack stack) {
        if (GearQuality.has(stack)) {
            return GearQuality.get(stack);
        }
        GearQuality.Grade[] grades = GearQuality.Grade.values();
        Integer food = stack.get(ModComponents.FOOD_QUALITY.get());
        if (food != null) {
            return grades[Math.clamp(food, 0, grades.length - 1)];
        }
        Integer leather = stack.get(ModComponents.LEATHER_QUALITY.get());
        if (leather != null) {
            return grades[Math.clamp(leather + 1, 0, grades.length - 1)];
        }
        Integer pottery = stack.get(ModComponents.POTTERY_QUALITY.get());
        if (pottery != null) {
            return grades[Math.clamp(pottery, 0, grades.length - 1)];
        }
        Integer wood = stack.get(ModComponents.WOOD_QUALITY.get());
        if (wood != null) {
            return grades[Math.clamp(wood, 0, grades.length - 1)];
        }
        Integer metal = stack.get(ModComponents.METAL_QUALITY.get());
        if (metal != null) {
            return grades[Math.clamp(metal, 0, grades.length - 1)];
        }
        return null;
    }

    /** Признаки для показа: редкость — собственная (из ModItems), а не записанная туда раньше качеством. */
    public static List<String> ownRarity(ItemStack stack, List<String> traits) {
        List<String> fixed = new ArrayList<>(traits);
        fixed.removeIf(RARITY::contains);
        ModItems.Definition def = ModItems.definition(stack.getItem());
        if (def != null) {
            for (String id : def.traits()) {
                if (RARITY.contains(id)) {
                    fixed.add(0, id);
                    break;
                }
            }
        }
        return fixed;
    }

    /**
     * Вернуть предмету его собственную редкость (из описания в ModItems): раньше качество еды и кожи
     * записывалось в редкость — у таких вещей она исправляется при следующей смене качества.
     */
    public static void restoreRarity(ItemStack stack) {
        List<String> traits = stack.get(ModComponents.TRAITS.get());
        if (traits == null) {
            return;
        }
        List<String> fixed = new ArrayList<>(traits);
        fixed.removeIf(RARITY::contains);
        ModItems.Definition def = ModItems.definition(stack.getItem());
        if (def != null) {
            for (String id : def.traits()) {
                if (RARITY.contains(id)) {
                    fixed.add(0, id);
                    break;
                }
            }
        }
        if (!fixed.equals(traits)) {
            stack.set(ModComponents.TRAITS.get(), List.copyOf(fixed));
        }
    }
}
