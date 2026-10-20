package com.nrdhvnstd.halgventmod.client;

import java.util.Locale;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.quality.GearQuality;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Что даёт качество снаряжения: у оружия — урон и пробитие своего типа, у брони — защита и стойкость. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class QualityTooltip {
    private QualityTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!GearQuality.applies(stack)) {
            return;
        }
        GearQuality.Grade grade = GearQuality.get(stack);
        if (GearQuality.isWeapon(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.halgventmod.quality.weapon", times(grade.damage()),
                    Math.round(grade.penetration() * 100), Component.translatable(DamageKind.of(stack).langKey()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            event.getToolTip().add(Component.translatable("tooltip.halgventmod.quality.armor", times(grade.protection()),
                    times(grade.resistance())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String times(float factor) {
        return String.format(Locale.ROOT, "%.2f", factor).replace('.', ',');
    }
}
