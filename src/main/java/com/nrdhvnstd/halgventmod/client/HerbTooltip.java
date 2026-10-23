package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.herbs.Herb;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Что даёт трава в отваре: у сушёной травы с видом — «В отваре: …», у отвара — «Действие: …» (herbs/Herb). */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class HerbTooltip {
    private HerbTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        boolean tea = stack.is(ModItems.HERBAL_TEA.get());
        if (!tea && !stack.is(ModItems.DRIED_HERBS.get())) {
            return; // у свежих трав действие — в описании предмета
        }
        Herb herb = Herb.of(stack);
        if (herb != null) {
            event.getToolTip().add(Component.translatable(tea ? "tooltip.halgventmod.herb.effect" : "tooltip.halgventmod.herb.in_tea",
                    Component.translatable(herb.effectKey())).withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
