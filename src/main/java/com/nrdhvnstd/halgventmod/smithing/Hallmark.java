package com.nrdhvnstd.halgventmod.smithing;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.name.RpNames;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Клеймо мастера. Кузнец куёт себе клеймо на наковальне — в него выбивается его знак (RP-имя, а нет — имя игрока).
 * В инвентаре ПКМ клеймом по металлической вещи (оружие, инструмент, доспех, литая посуда, кованая мелочь по одной)
 * — на вещи появляется «Клеймо: …». Заклеймённую второй раз не клеймят. Клеймо стачивается, как инструмент.
 */
public final class Hallmark {
    private Hallmark() {}

    /** Чей знак: у клейма — выбитый в нём, у вещи — которым она помечена; null — нет. */
    @Nullable
    public static String mark(ItemStack stack) {
        return stack.get(ModComponents.HALLMARK.get());
    }

    /** Знак мастера: RP-имя, а без него — имя игрока. */
    public static String of(Player player) {
        String rp = RpNames.get(player);
        return rp == null || rp.isBlank() ? player.getGameProfile().getName() : rp;
    }

    /** Выбить в клейме знак того, кто его выковал (зовёт наковальня). */
    public static ItemStack bind(ItemStack stamp, Player smith) {
        if (stamp.is(SmithingItems.SMITH_STAMP.get()) && mark(stamp) == null) {
            stamp.set(ModComponents.HALLMARK.get(), of(smith));
        }
        return stamp;
    }

    /** Можно ли поставить клеймо: металлическая вещь по одной — с прочностью, литая посуда, или с качеством металла. */
    public static boolean markable(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() != 1 || stack.is(SmithingItems.SMITH_STAMP.get())) {
            return false;
        }
        return stack.isDamageableItem() || Pottery.metal(stack) || stack.has(ModComponents.METAL_QUALITY.get())
                || stack.has(ModComponents.GEAR_QUALITY.get());
    }
}
