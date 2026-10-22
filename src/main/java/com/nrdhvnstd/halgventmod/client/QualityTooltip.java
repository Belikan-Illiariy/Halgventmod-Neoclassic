package com.nrdhvnstd.halgventmod.client;

import java.util.Locale;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.brewing.Brewing;
import com.nrdhvnstd.halgventmod.brewing.Intoxication;
import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.quality.GearQuality;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Что даёт качество: еде — сытость, хмельному — хмель, сивуха, похмелье; оружию — урон и пробитие
 * своего типа, броне — защита и стойкость.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class QualityTooltip {
    private QualityTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (FoodQuality.has(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.halgventmod.quality.food", times(FoodQuality.factor(stack)))
                    .withStyle(ChatFormatting.DARK_GRAY));
            drink(event, stack);
            return;
        }
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

    /** Что качество даёт хмельному: хмель, сивуха, похмелье (Intoxication). */
    private static void drink(ItemTooltipEvent event, ItemStack stack) {
        if (Brewing.strength(stack) <= 0) {
            return;
        }
        int quality = FoodQuality.get(stack);
        var tip = event.getToolTip();
        tip.add(Component.translatable("tooltip.halgventmod.quality.drink", times(Intoxication.strengthFactor(quality)))
                .withStyle(ChatFormatting.DARK_GRAY));
        float rotgut = Intoxication.rotgutChance(stack);
        if (rotgut > 0) {
            tip.add(Component.translatable("tooltip.halgventmod.quality.rotgut", Math.round(rotgut * 100))
                    .withStyle(ChatFormatting.RED));
        }
        float skip = Intoxication.hangoverSkip(quality);
        float longer = Intoxication.hangoverLength(quality) - 1;
        if (skip > 0) {
            tip.add(Component.translatable("tooltip.halgventmod.quality.hangover_skip", Math.round(skip * 100))
                    .withStyle(ChatFormatting.DARK_GREEN));
        } else if (longer > 0) {
            tip.add(Component.translatable("tooltip.halgventmod.quality.hangover_longer", Math.round(longer * 100))
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    private static String times(float factor) {
        return String.format(Locale.ROOT, "%.2f", factor).replace('.', ',');
    }
}
