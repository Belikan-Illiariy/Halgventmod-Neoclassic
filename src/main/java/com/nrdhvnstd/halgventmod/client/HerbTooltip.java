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
        boolean essence = stack.is(com.nrdhvnstd.halgventmod.alchemy.AlchemyItems.HERB_ESSENCE.get());
        boolean powder = stack.is(com.nrdhvnstd.halgventmod.alchemy.AlchemyItems.HERB_POWDER.get());
        if (!tea && !essence && !powder && !stack.is(ModItems.DRIED_HERBS.get())) {
            return; // у свежих трав действие — в описании предмета
        }
        Herb herb = Herb.of(stack);
        if (herb != null) {
            String key = tea ? "tooltip.halgventmod.herb.effect" : essence ? "tooltip.halgventmod.herb.essence_effect"
                    : powder ? "tooltip.halgventmod.herb.in_essence" : "tooltip.halgventmod.herb.in_tea";
            event.getToolTip().add(Component.translatable(key, Component.translatable(herb.effectKey()),
                    com.nrdhvnstd.halgventmod.alchemy.Alchemy.ESSENCE_FACTOR).withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
