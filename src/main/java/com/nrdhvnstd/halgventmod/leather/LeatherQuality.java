package com.nrdhvnstd.halgventmod.leather;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.quality.QualityDisplay;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.world.item.ItemStack;

/**
 * Качество кожи 0–4. Хранится компонентом {@link ModComponents#LEATHER_QUALITY}; в подсказке — плашка качества
 * по общей шкале со сдвигом на ступень (0 — Плохая … 4 — Шедевр), редкость предмета не трогает.
 *
 * <p>Откуда берётся: зверь (у медведя, волка, лисы выше), чистое убийство, ровная работа на колоде, полное
 * дубление, сушка без дождя, отделка колотушкой и жиром, аккуратное шитьё. Сделанная из кожи вещь получает
 * худшее качество из пошедших на неё кусков (и поправку за шитьё).
 */
public final class LeatherQuality {
    public static final int MAX = 4;

    // Меняйте здесь: прибавка к защите кожаной вещи за каждую ступень качества
    public static final double PROTECTION_PER_LEVEL = 0.1; // Редкая (+2) — защита ×1.2
    public static final double DRIED_OUT_FACTOR = 0.8;     // пересохшая — защита ×0.8

    private LeatherQuality() {}

    public static int get(ItemStack stack) {
        Integer quality = stack.get(ModComponents.LEATHER_QUALITY.get());
        return quality == null ? 0 : quality;
    }

    public static boolean has(ItemStack stack) {
        return stack.has(ModComponents.LEATHER_QUALITY.get());
    }

    /** Задать качество кожи. В подсказке — плашка качества по общей шкале (0 — Плохая … 4 — Шедевр). */
    public static ItemStack set(ItemStack stack, int quality) {
        stack.set(ModComponents.LEATHER_QUALITY.get(), Math.clamp(quality, 0, MAX));
        QualityDisplay.restoreRarity(stack); // качество — плашка (QualityBadge), редкость не трогает
        return stack;
    }

    public static ItemStack change(ItemStack stack, int delta) {
        return set(stack, get(stack) + delta);
    }

    /** Перенести шкуру и качество со старой стадии на новую. */
    public static ItemStack carry(ItemStack from, ItemStack to) {
        Hide hide = from.get(ModComponents.HIDE.get());
        if (hide != null) {
            to.set(ModComponents.HIDE.get(), hide);
        }
        return set(to, get(from));
    }

    /** Множитель защиты кожаной вещи; 1 — у вещей, сделанных не на столе кожевника. */
    public static double protectionFactor(ItemStack stack) {
        if (!has(stack)) {
            return 1.0;
        }
        double factor = 1.0 + PROTECTION_PER_LEVEL * get(stack);
        if (Boolean.TRUE.equals(stack.get(ModComponents.DRIED_OUT.get()))) {
            factor *= DRIED_OUT_FACTOR;
        }
        return factor;
    }
}
