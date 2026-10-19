package com.nrdhvnstd.halgventmod.gear;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Правила надевания снаряжения поверх «своей ячейки».
 * Пояс держит фонарь, колчан и подсумки: без пояса их не надеть, пока они висят — пояс не снять.
 */
public final class GearRules {
    private GearRules() {}

    /** Почему вещь нельзя надеть в ячейку, или null, если можно. Вид вещи уже проверен. */
    @Nullable
    public static Component refusal(GearInventory gear, GearSlot slot, ItemStack stack) {
        if (slot.onBelt() && gear.get(GearSlot.BELT).isEmpty()) {
            return Component.translatable("message.halgventmod.belt_first");
        }
        return null;
    }

    /** Почему вещь нельзя снять, или null, если можно. */
    @Nullable
    public static Component takeOffRefusal(GearInventory gear, GearSlot slot) {
        if (slot == GearSlot.BELT) {
            for (GearSlot other : GearSlot.values()) {
                if (other.onBelt() && !gear.get(other).isEmpty()) {
                    return Component.translatable("message.halgventmod.remove_belt_items_first");
                }
            }
        }
        return null;
    }

    /** Первая ячейка, куда подходит вещь: свободная, если есть, иначе первая своего вида. null — не снаряжение. */
    @Nullable
    public static GearSlot slotFor(GearInventory gear, ItemStack stack) {
        GearType type = ModGear.typeOf(stack);
        if (type == null) {
            return null;
        }
        GearSlot first = null;
        for (GearSlot slot : GearSlot.values()) {
            if (slot.type() != type) {
                continue;
            }
            if (gear.get(slot).isEmpty()) {
                return slot;
            }
            if (first == null) {
                first = slot;
            }
        }
        return first;
    }
}
