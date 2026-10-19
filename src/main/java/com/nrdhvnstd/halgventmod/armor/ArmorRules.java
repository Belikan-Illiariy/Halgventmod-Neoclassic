package com.nrdhvnstd.halgventmod.armor;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Правила надевания поверх «своего слота».
 * Капюшон лежит поверх шлема и совместим только со шлемами, отмеченными .hoodCompatible().
 * Плащ без капюшона совместим с любым шлемом.
 * Любой шлем надевается только поверх подшлемника; пока шлем на голове, подшлемник не снять.
 */
public final class ArmorRules {
    private ArmorRules() {}

    /** Почему вещь нельзя положить в слот, или null, если можно. Слот самой вещи уже проверен. */
    @Nullable
    public static Component refusal(ArmorInventory armor, ArmorSlot slot, ItemStack stack) {
        ModArmor.ArmorInfo info = ModArmor.get(stack);
        if (info == null) {
            return null;
        }
        if (slot == ArmorSlot.CLOAK && hasHood(info)) {
            ModArmor.ArmorInfo helmet = ModArmor.get(armor.get(ArmorSlot.HELMET));
            if (helmet != null && !helmet.hoodCompatible()) {
                return Component.translatable("message.halgventmod.hood_over_helmet");
            }
        }
        if (slot == ArmorSlot.HELMET && armor.get(ArmorSlot.ARMING_CAP).isEmpty()) {
            return Component.translatable("message.halgventmod.arming_cap_first");
        }
        if (slot == ArmorSlot.HELMET && !info.hoodCompatible()) {
            ModArmor.ArmorInfo cloak = ModArmor.get(armor.get(ArmorSlot.CLOAK));
            if (cloak != null && hasHood(cloak)) {
                return Component.translatable("message.halgventmod.remove_hood_first");
            }
        }
        return null;
    }

    /** Почему вещь нельзя вынуть из слота, или null, если можно. */
    @Nullable
    public static Component takeOffRefusal(ArmorInventory armor, ArmorSlot slot) {
        if (slot == ArmorSlot.ARMING_CAP && !armor.get(ArmorSlot.HELMET).isEmpty()) {
            return Component.translatable("message.halgventmod.remove_helmet_first");
        }
        return null;
    }

    /** Сообщить игроку над хотбаром. Только на сервере — чтобы сообщение не дублировалось. */
    public static void tell(Player player, Component message) {
        if (player instanceof ServerPlayer) {
            player.displayClientMessage(message, true);
        }
    }

    private static boolean hasHood(ModArmor.ArmorInfo info) {
        return info.cloak() != null && info.cloak().hasHood();
    }
}
