package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.quality.QualityDisplay;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.world.item.ItemStack;

/**
 * Качество приготовленной еды 0–5 по общей шкале: Ужасное, Плохое, Посредственное, Добротное, Отменное, Шедевр.
 * Хранится компонентом {@link ModComponents#FOOD_QUALITY}, в подсказке — плашка рядом с признаками; редкость не трогает.
 *
 * <p>Сытость и насыщение еды умножаются на {@link #factor}: Ужасное — ×0,8, Посредственное — ×1,0, Шедевр — ×1,3.
 * Еда без компонента (сырая, чужих модов) — ×1,0.
 */
public final class FoodQuality {
    public static final int MAX = 5;
    public static final int NORMAL = 2;

    // Меняйте здесь
    public static final float FACTOR_PER_LEVEL = 0.1F;

    private FoodQuality() {}

    public static boolean has(ItemStack stack) {
        return stack.has(ModComponents.FOOD_QUALITY.get());
    }

    public static int get(ItemStack stack) {
        Integer quality = stack.get(ModComponents.FOOD_QUALITY.get());
        return quality == null ? NORMAL : quality;
    }

    /** Задать качество — только компонент: редкость не трогается, в подсказке — плашка качества (QualityBadge). */
    public static ItemStack set(ItemStack stack, int quality) {
        stack.set(ModComponents.FOOD_QUALITY.get(), Math.clamp(quality, 0, MAX));
        QualityDisplay.restoreRarity(stack);
        return stack;
    }

    /** Множитель сытости и насыщения. */
    public static float factor(ItemStack stack) {
        return has(stack) ? 1.0F + FACTOR_PER_LEVEL * (get(stack) - NORMAL) : 1.0F;
    }

    /** Пометить еду сырой (недожаренный кусок) или снять пометку. */
    public static ItemStack markRaw(ItemStack stack, boolean raw) {
        List<String> traits = new ArrayList<>(stack.getOrDefault(ModComponents.TRAITS.get(), List.of()));
        traits.remove(ModTraits.RAW.id());
        if (raw) {
            traits.add(ModTraits.RAW.id());
        }
        stack.set(ModComponents.TRAITS.get(), List.copyOf(traits));
        return stack;
    }

    public static boolean isMarkedRaw(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TRAITS.get(), List.of()).contains(ModTraits.RAW.id());
    }
}
