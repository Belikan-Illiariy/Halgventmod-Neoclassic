package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.food.ModFoods;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Срок годности в подсказке: над строкой веса. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class FoodTooltip {
    private FoodTooltip() {}

    // LOW — раньше строки веса (у неё LOWEST), поэтому срок встаёт прямо над весом
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int shelfLife = ModFoods.shelfLife(stack);
        if (shelfLife <= 0) {
            return;
        }

        Level level = Minecraft.getInstance().level;
        Integer left = level != null ? Freshness.daysLeft(stack, level) : null;
        if (left == null) {
            left = shelfLife; // ещё без отметки (например, во вкладке креатива) — свежий
        }

        event.getToolTip().add(left > 0
                ? Component.translatable("tooltip.halgventmod.spoils_in", left).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.halgventmod.spoiled").withStyle(ChatFormatting.RED));
    }
}