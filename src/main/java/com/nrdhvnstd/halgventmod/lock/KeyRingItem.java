package com.nrdhvnstd.halgventmod.lock;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Связка ключей. В инвентаре работает как ванильный мешочек, только для ключей:
 * ПКМ ключом по связке (или связкой по ключу) — надеть ключ, ПКМ пустой рукой по связке — снять последний.
 * Shift + ПКМ связкой по замку отпирает его, если подходит хоть один ключ на ней.
 */
public class KeyRingItem extends Item {
    public static final int CAPACITY = 8;

    public KeyRingItem(Properties properties) {
        super(properties);
    }

    public static List<ItemStack> keys(ItemStack ring) {
        return ring.getOrDefault(ModComponents.KEY_RING.get(), List.of());
    }

    public static boolean canHold(ItemStack stack) {
        return stack.is(ModItems.KEY.get());
    }

    private static boolean isFull(ItemStack ring) {
        return keys(ring).size() >= CAPACITY;
    }

    private static void add(ItemStack ring, ItemStack key) {
        List<ItemStack> updated = new ArrayList<>(keys(ring));
        updated.add(key.copyWithCount(1));
        ring.set(ModComponents.KEY_RING.get(), List.copyOf(updated));
    }

    @Nullable
    private static ItemStack removeLast(ItemStack ring) {
        List<ItemStack> keys = keys(ring);
        if (keys.isEmpty()) {
            return null;
        }
        List<ItemStack> updated = new ArrayList<>(keys);
        ItemStack removed = updated.remove(updated.size() - 1).copy();
        if (updated.isEmpty()) {
            ring.remove(ModComponents.KEY_RING.get());
        } else {
            ring.set(ModComponents.KEY_RING.get(), List.copyOf(updated));
        }
        return removed;
    }

    // Связка на курсоре, ПКМ по ячейке
    @Override
    public boolean overrideStackedOnOther(ItemStack ring, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }
        ItemStack other = slot.getItem();
        if (other.isEmpty()) {
            ItemStack removed = removeLast(ring);
            if (removed != null) {
                ItemStack rest = slot.safeInsert(removed);
                if (!rest.isEmpty()) {
                    add(ring, rest);
                }
                playRemove(player);
            }
        } else if (canHold(other) && !isFull(ring)) {
            ItemStack taken = slot.safeTake(other.getCount(), 1, player);
            if (!taken.isEmpty()) {
                add(ring, taken);
                playInsert(player);
            }
        }
        return true;
    }

    // Что-то на курсоре, ПКМ по связке
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack ring, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        if (other.isEmpty()) {
            ItemStack removed = removeLast(ring);
            if (removed != null) {
                access.set(removed);
                playRemove(player);
            }
        } else if (canHold(other) && !isFull(ring)) {
            add(ring, other.split(1));
            playInsert(player);
        }
        return true;
    }

    private static void playInsert(Player player) {
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playRemove(Player player) {
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }
}