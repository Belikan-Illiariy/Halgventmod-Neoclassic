package com.nrdhvnstd.halgventmod.trade;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Кружки торговца для розлива: глиняная и пьютеровая. Торговец наливает в свою кружку и берёт залог (у каждого вида
 * свой), покупатель сдаёт пустую кружку обратно — и получает залог назад. Порядок — номер в пакетах, новые в конец.
 */
public enum Mug {
    CLAY(ModItems.CLAY_CUP),
    PEWTER(SmithingItems.PEWTER_TANKARD);

    private final Supplier<? extends Item> item;

    Mug(Supplier<? extends Item> item) {
        this.item = item;
    }

    public Item item() {
        return item.get();
    }

    /** Вид кружки или null — это не кружка торговца. */
    @Nullable
    public static Mug of(ItemStack stack) {
        for (Mug mug : values()) {
            if (stack.is(mug.item())) {
                return mug;
            }
        }
        return null;
    }

    /** Пустая целая кружка: без питья и воды, не треснувшая. */
    public static boolean empty(ItemStack stack) {
        return of(stack) != null && !stack.has(ModComponents.STORED.get()) && Pottery.water(stack) == 0
                && !Pottery.cracked(stack);
    }
}
