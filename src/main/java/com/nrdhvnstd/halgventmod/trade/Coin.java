package com.nrdhvnstd.halgventmod.trade;

import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Монеты, в которых торговец ставит цену. Цена — «столько-то монет такого-то вида», и платят именно этими монетами;
 * поменять одни на другие можно у НПС-торговца (вкладка «Обмен»): по весу металла, 1 г золота = 10 г серебра.
 * Порядок — номер в пакетах и сохранениях, новые только в конец.
 */
public enum Coin {
    //                         граммов  золото
    GULDEN(ModItems.COIN_GULDEN, 2,     true),   // большая золотая
    CRONA(ModItems.COIN_CRONA,   1,     true),   // малая золотая
    TALER(ModItems.COIN_TALER,   5,     false),  // большая серебряная
    MARKA(ModItems.COIN_MARKA,   2,     false);  // малая серебряная

    /** Сколько граммов серебра стоит грамм золота. Меняйте здесь. */
    public static final int GOLD_TO_SILVER = 10;

    private final Supplier<? extends Item> item;
    private final int grams;
    private final boolean gold;

    Coin(Supplier<? extends Item> item, int grams, boolean gold) {
        this.item = item;
        this.grams = grams;
        this.gold = gold;
    }

    public Item item() {
        return item.get();
    }

    public ItemStack stack(int count) {
        return new ItemStack(item(), count);
    }

    public Component title() {
        return item().getDescription();
    }

    /** Ценность в граммах серебра: гульден 20, крона 10, талер 5, марка 2. Вес монеты — в ModItems (.grams). */
    public int value() {
        return gold ? grams * GOLD_TO_SILVER : grams;
    }

    /** Обмен монет from на to без сдачи: сколько отдать и сколько получить за один раз. */
    public static int[] exchange(Coin from, Coin to) {
        int gcd = gcd(from.value(), to.value());
        return new int[] {to.value() / gcd, from.value() / gcd};
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    public static Coin byOrdinal(int ordinal) {
        Coin[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : TALER;
    }
}
