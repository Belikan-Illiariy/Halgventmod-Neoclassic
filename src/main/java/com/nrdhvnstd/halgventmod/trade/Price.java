package com.nrdhvnstd.halgventmod.trade;

import java.util.Arrays;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Цена — набор монет: сколько гульденов, крон, талеров и марок (по порядку Coin). Платят ровно этими монетами,
 * курса обмена нет. Все нули — «не продаётся» (не скупается).
 */
public final class Price {
    public static final int COINS = Coin.values().length;
    public static final int MAX_PER_COIN = 999;

    private Price() {}

    public static int[] zero() {
        return new int[COINS];
    }

    /** Цена задана: хоть одна монета больше нуля. */
    public static boolean isSet(int[] price) {
        for (int amount : price) {
            if (amount > 0) {
                return true;
            }
        }
        return false;
    }

    /** Ровно COINS чисел от 0 до MAX_PER_COIN (лишнее обрезается, недостающее — нули). */
    public static int[] clean(int[] price) {
        int[] clean = new int[COINS];
        for (int i = 0; i < COINS && i < price.length; i++) {
            clean[i] = Math.clamp(price[i], 0, MAX_PER_COIN);
        }
        return clean;
    }

    /** Цена × сколько (за несколько мер). */
    public static int[] times(int[] price, int count) {
        int[] total = clean(price);
        for (int i = 0; i < total.length; i++) {
            total[i] *= count;
        }
        return total;
    }

    /** Сумма двух цен (мера и залог). */
    public static int[] plus(int[] a, int[] b) {
        int[] total = clean(a);
        int[] other = clean(b);
        for (int i = 0; i < total.length; i++) {
            total[i] += other[i];
        }
        return total;
    }

    public static boolean same(int[] a, int[] b) {
        return Arrays.equals(clean(a), clean(b));
    }

    /** «1 крона, 5 талеров» — для сообщений. */
    public static Component text(int[] price) {
        MutableComponent text = Component.empty();
        boolean first = true;
        for (Coin coin : Coin.values()) {
            int amount = coin.ordinal() < price.length ? price[coin.ordinal()] : 0;
            if (amount <= 0) {
                continue;
            }
            if (!first) {
                text.append(", ");
            }
            text.append(amount + " × ").append(coin.title());
            first = false;
        }
        return text;
    }
}
