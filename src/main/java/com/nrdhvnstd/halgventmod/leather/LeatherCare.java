package com.nrdhvnstd.halgventmod.leather;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Уход за кожей: вещь со стола кожевника, которую не смазывали больше {@link #DRY_AFTER_DAYS} суток,
 * пересыхает — хуже защищает ({@link LeatherQuality#DRIED_OUT_FACTOR}). Смазать — на шитейном столе
 * (вещь в руке, сало или воск в инвентаре). Проверяются инвентарь и надетая броня.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class LeatherCare {
    // Меняйте здесь
    public static final int DRY_AFTER_DAYS = 16;
    private static final int CHECK_INTERVAL = 200; // раз в 10 секунд

    private LeatherCare() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        int today = Freshness.currentDay(player.level());
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            update(inventory.getItem(i), today);
        }
        // Надетую броню меняем через set — так броня пересчитает защиту
        ArmorInventory armor = player.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return;
        }
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack worn = armor.get(slot);
            if (needsUpdate(worn, today)) {
                ItemStack copy = worn.copy();
                update(copy, today);
                armor.set(slot, copy);
            }
        }
    }

    public static boolean isDry(ItemStack stack, int today) {
        Integer oiled = stack.get(ModComponents.OILED_DAY.get());
        return oiled != null && today - oiled > DRY_AFTER_DAYS;
    }

    private static boolean needsUpdate(ItemStack stack, int today) {
        if (!stack.has(ModComponents.OILED_DAY.get())) {
            return false;
        }
        return isDry(stack, today) != Boolean.TRUE.equals(stack.get(ModComponents.DRIED_OUT.get()));
    }

    private static void update(ItemStack stack, int today) {
        if (!needsUpdate(stack, today)) {
            return;
        }
        if (isDry(stack, today)) {
            stack.set(ModComponents.DRIED_OUT.get(), true);
        } else {
            stack.remove(ModComponents.DRIED_OUT.get());
        }
    }
}
