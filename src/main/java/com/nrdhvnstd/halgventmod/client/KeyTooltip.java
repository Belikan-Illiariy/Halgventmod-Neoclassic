package com.nrdhvnstd.halgventmod.client;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.lock.KeyRingItem;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Подсказка ключей, замков и связки: название «Амбар у мельницы», список ключей на связке,
 * пометка у заготовок, а при F3+H — номер секрета (для администрации).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class KeyTooltip {
    private KeyTooltip() {}

    // HIGH — раньше признаков и описания
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        boolean key = stack.is(ModItems.KEY.get());
        boolean padlock = stack.is(ModItems.PADLOCK.get());
        boolean ring = stack.is(ModItems.KEY_RING.get());
        if (!key && !padlock && !ring) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        int index = Math.min(1, tooltip.size());

        String label = stack.get(ModComponents.KEY_LABEL.get());
        if (label != null && !label.isEmpty()) {
            tooltip.add(index++, Component.literal("«" + label + "»").withStyle(ChatFormatting.GOLD));
        }
        if (key && !stack.has(ModComponents.LOCK_SECRET.get())) {
            tooltip.add(index++, Component.translatable("lock.halgventmod.blank_key").withStyle(ChatFormatting.DARK_GRAY));
        }

        // Связка: сколько ключей и какие
        if (ring) {
            List<ItemStack> keys = KeyRingItem.keys(stack);
            tooltip.add(index++, Component.translatable("lock.halgventmod.ring_count", keys.size(), KeyRingItem.CAPACITY)
                    .withStyle(ChatFormatting.GRAY));
            for (ItemStack inner : keys) {
                String innerLabel = inner.get(ModComponents.KEY_LABEL.get());
                Component name = innerLabel != null && !innerLabel.isEmpty()
                        ? Component.literal("«" + innerLabel + "»")
                        : Component.translatable("lock.halgventmod.ring_unnamed_key");
                tooltip.add(index++, Component.literal("  ").append(name).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (event.getFlags().isAdvanced()) {
            Long secret = stack.get(ModComponents.LOCK_SECRET.get());
            if (secret != null) {
                tooltip.add(Component.translatable("lock.halgventmod.advanced.secret", secret).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}