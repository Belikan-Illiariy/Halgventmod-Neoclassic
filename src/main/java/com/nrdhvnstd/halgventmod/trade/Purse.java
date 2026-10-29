package com.nrdhvnstd.halgventmod.trade;

import java.util.Arrays;

/**
 * Кошелёк НПС-торговца — касса лавки: сюда идёт выручка с продаж, отсюда платится скупка. Монеты по видам (Coin).
 * Хозяин вносит и забирает деньги в лавке торговца (вкладка «Касса»).
 */
public final class Purse {
    private final int[] coins = new int[Price.COINS];

    public int get(Coin coin) {
        return coins[coin.ordinal()];
    }

    public int[] all() {
        return Arrays.copyOf(coins, coins.length);
    }

    public void add(Coin coin, int amount) {
        coins[coin.ordinal()] += Math.max(0, amount);
    }

    /** Положить цену набором монет (продажа). */
    public void add(int[] price) {
        for (int i = 0; i < Price.COINS && i < price.length; i++) {
            coins[i] += Math.max(0, price[i]);
        }
    }

    /** Хватит ли на цену (скупка). */
    public boolean canPay(int[] price) {
        for (int i = 0; i < Price.COINS && i < price.length; i++) {
            if (coins[i] < price[i]) {
                return false;
            }
        }
        return true;
    }

    /** Заплатить цену набором монет; false — не хватает, ничего не тронуто. */
    public boolean pay(int[] price) {
        if (!canPay(price)) {
            return false;
        }
        for (int i = 0; i < Price.COINS && i < price.length; i++) {
            coins[i] -= price[i];
        }
        return true;
    }

    /** Вынуть всё: сколько монет каждого вида было. */
    public int[] takeAll() {
        int[] all = all();
        Arrays.fill(coins, 0);
        return all;
    }

    public int[] save() {
        return all();
    }

    public void load(int[] saved) {
        Arrays.fill(coins, 0);
        System.arraycopy(saved, 0, coins, 0, Math.min(saved.length, coins.length));
    }
}
