package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.smithing.Crucible;
import com.nrdhvnstd.halgventmod.smithing.ForgeBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.Melt;
import com.nrdhvnstd.halgventmod.smithing.Metal;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Что в тигле: состав шихты или расплав, сколько слитков, жидкий ли. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CrucibleTooltip {
    private CrucibleTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!Crucible.is(stack)) {
            return;
        }
        Melt melt = Crucible.melt(stack);
        int total = Crucible.total(melt);
        var tip = event.getToolTip();
        if (total <= 0) {
            if (melt != null && melt.lid()) {
                tip.add(Component.translatable("tooltip.halgventmod.crucible.lid").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        Crucible.Alloy alloy = Crucible.resolve(melt);
        if (melt.molten() && alloy != null) {
            tip.add(Component.translatable("tooltip.halgventmod.crucible.melt", Component.translatable(alloy.metal().langKey()),
                    ForgeBlockEntity.ingots(total)).withStyle(ChatFormatting.GOLD));
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && Crucible.liquid(stack, minecraft.level)) {
                tip.add(Component.translatable("tooltip.halgventmod.crucible.liquid", Crucible.liquidSeconds(stack, minecraft.level))
                        .withStyle(ChatFormatting.RED));
            } else {
                tip.add(Component.translatable("tooltip.halgventmod.crucible.solid").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        MutableComponent parts = Component.empty();
        boolean first = true;
        for (Map.Entry<Metal, Integer> entry : Crucible.parts(melt).entrySet()) {
            if (!first) {
                parts.append(", ");
            }
            parts.append(Component.translatable(entry.getKey().langKey())).append(" " + ForgeBlockEntity.ingots(entry.getValue()));
            first = false;
        }
        tip.add(Component.translatable("tooltip.halgventmod.crucible.charge", parts).withStyle(ChatFormatting.GRAY));
        if (melt.lid()) {
            tip.add(Component.translatable("tooltip.halgventmod.crucible.lid").withStyle(ChatFormatting.GRAY));
        }
    }
}
