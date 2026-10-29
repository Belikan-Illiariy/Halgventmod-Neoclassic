package com.nrdhvnstd.halgventmod.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.nrdhvnstd.halgventmod.storage.Storage;
import com.nrdhvnstd.halgventmod.storage.StorageArea;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Деньги при себе: монеты в карманах, во второй руке и в сумках (подсумки, спина), у администрации и в творческом —
 * ещё в открытых ячейках 9–35. Считает и забирает и на сервере, и на клиенте (сумки приходят клиенту вложением).
 */
public final class Money {
    private Money() {}

    /** Сколько монет этого вида при себе. */
    public static int count(Player player, Coin coin) {
        int total = 0;
        for (ItemStack stack : stacks(player)) {
            if (stack.is(coin.item())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Забрать монеты (сначала карманы, потом сумки); false — не хватает, ничего не тронуто. */
    public static boolean take(Player player, Coin coin, int amount) {
        if (count(player, coin) < amount) {
            return false;
        }
        int left = amount;
        Inventory inventory = player.getInventory();
        left = takeFrom(inventory.items.subList(0, Storage.POCKETS), coin, left);
        left = takeFrom(inventory.offhand, coin, left);
        if (Storage.unrestricted(player)) {
            left = takeFrom(inventory.items.subList(StorageArea.VANILLA_FIRST, inventory.items.size()), coin, left);
        }
        for (StorageArea area : StorageArea.values()) {
            if (left <= 0) {
                break;
            }
            int before = left;
            left = takeFrom(Storage.items(player, area), coin, left);
            if (left != before) {
                Storage.changed(player, area); // сумка записывает новое содержимое
            }
        }
        inventory.setChanged();
        return true;
    }

    /** Хватит ли при себе монет на цену набором монет. */
    public static boolean canPay(Player player, int[] price) {
        for (Coin coin : Coin.values()) {
            if (coin.ordinal() < price.length && count(player, coin) < price[coin.ordinal()]) {
                return false;
            }
        }
        return true;
    }

    /** Заплатить цену набором монет; false — не хватает, ничего не тронуто. */
    public static boolean pay(Player player, int[] price) {
        if (!canPay(player, price)) {
            return false;
        }
        for (Coin coin : Coin.values()) {
            if (coin.ordinal() < price.length && price[coin.ordinal()] > 0) {
                take(player, coin, price[coin.ordinal()]);
            }
        }
        return true;
    }

    /** Дать монеты набором (цена скупки). */
    public static void give(Player player, int[] price) {
        for (Coin coin : Coin.values()) {
            if (coin.ordinal() < price.length && price[coin.ordinal()] > 0) {
                give(player, coin, price[coin.ordinal()]);
            }
        }
    }

    /** Дать монеты: в карманы и сумки, что не влезло — под ноги. */
    public static void give(Player player, Coin coin, int amount) {
        int max = coin.stack(1).getMaxStackSize();
        while (amount > 0) {
            int part = Math.min(max, amount);
            Storage.addOrDrop(player.getInventory(), coin.stack(part));
            amount -= part;
        }
    }

    /** Сколько при себе вещей, подходящих под условие. */
    public static int count(Player player, Predicate<ItemStack> test) {
        int total = 0;
        for (ItemStack stack : stacks(player)) {
            if (!stack.isEmpty() && test.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Забрать одну вещь, подходящую под условие (карманы, вторая рука, сумки); пусто — нет такой. */
    public static ItemStack takeOne(Player player, Predicate<ItemStack> test) {
        Inventory inventory = player.getInventory();
        ItemStack taken = takeOneFrom(inventory.items.subList(0, Storage.POCKETS), test);
        if (taken.isEmpty()) {
            taken = takeOneFrom(inventory.offhand, test);
        }
        if (taken.isEmpty() && Storage.unrestricted(player)) {
            taken = takeOneFrom(inventory.items.subList(StorageArea.VANILLA_FIRST, inventory.items.size()), test);
        }
        for (StorageArea area : StorageArea.values()) {
            if (!taken.isEmpty()) {
                break;
            }
            taken = takeOneFrom(Storage.items(player, area), test);
            if (!taken.isEmpty()) {
                Storage.changed(player, area);
            }
        }
        inventory.setChanged();
        return taken;
    }

    private static ItemStack takeOneFrom(List<ItemStack> stacks, Predicate<ItemStack> test) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && test.test(stack)) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static int takeFrom(List<ItemStack> stacks, Coin coin, int left) {
        for (ItemStack stack : stacks) {
            if (left <= 0) {
                break;
            }
            if (stack.is(coin.item())) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        return left;
    }

    private static List<ItemStack> stacks(Player player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> all = new ArrayList<>(inventory.items.subList(0, Storage.POCKETS));
        all.addAll(inventory.offhand);
        if (Storage.unrestricted(player)) {
            all.addAll(inventory.items.subList(StorageArea.VANILLA_FIRST, inventory.items.size()));
        }
        for (StorageArea area : StorageArea.values()) {
            all.addAll(Storage.items(player, area));
        }
        return all;
    }
}
