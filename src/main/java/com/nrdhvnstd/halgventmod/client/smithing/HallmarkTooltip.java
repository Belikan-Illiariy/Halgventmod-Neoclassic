package com.nrdhvnstd.halgventmod.client.smithing;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.smithing.Hallmark;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Строка подсказки клейма: у вещи — чьё клеймо на ней, у самого клейма — какой знак выбит. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class HallmarkTooltip {
    private HallmarkTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String mark = Hallmark.mark(stack);
        if (stack.is(SmithingItems.SMITH_STAMP.get())) {
            event.getToolTip().add(mark == null
                    ? Component.translatable("tooltip.halgventmod.hallmark.blank").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("tooltip.halgventmod.hallmark.stamp", mark).withStyle(ChatFormatting.GOLD));
        } else if (mark != null) {
            event.getToolTip().add(Component.translatable("tooltip.halgventmod.hallmark", mark).withStyle(ChatFormatting.GOLD));
        }
    }
}
