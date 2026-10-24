package com.nrdhvnstd.halgventmod.client.alchemy;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.alchemy.Alchemy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Строка подсказки алхимии: клинок намазан сулемой — сколько ударов ещё отравят. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class AlchemyTooltip {
    private AlchemyTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        int hits = Alchemy.coat(event.getItemStack());
        if (hits > 0) {
            event.getToolTip().add(Component.translatable("tooltip.halgventmod.alchemy.coat", hits)
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
