package com.nrdhvnstd.halgventmod.client;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.gear.GearType;
import com.nrdhvnstd.halgventmod.gear.ModGear;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.storage.ContainerType;
import com.nrdhvnstd.halgventmod.storage.Quiver;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Характеристики снаряжения в подсказке (и в панели предмета):
 *   Носится: Пояс
 *   Вместимость: 6           — у пустой сумки; у сумки с вещами — «Внутри: 2 из 6»; у колчана — «Стрел: 12 из 20»
 *   Грузоподъёмность: +20 кг, пока надето
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class GearTooltip {
    public static final String POUCH_KEY = "gear_slot." + HalgventMod.MODID + ".pouch";

    private GearTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ModGear.GearInfo info = ModGear.get(stack);
        if (info == null) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        if (info.gear() != null) {
            // Подсумков две ячейки («Подсумок 1», «Подсумок 2») — в подсказке просто «Подсумок»
            String key = info.gear() == GearType.POUCH ? POUCH_KEY : null;
            for (GearSlot slot : GearSlot.values()) {
                if (key == null && slot.type() == info.gear()) {
                    key = slot.langKey();
                }
            }
            if (key != null) {
                tooltip.add(Component.translatable("tooltip.halgventmod.gear_slot", Component.translatable(key))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (info.container() == ContainerType.QUIVER) {
            tooltip.add(Component.translatable("tooltip.halgventmod.arrows", QuiverDecorator.arrows(stack), Quiver.MAX_ARROWS)
                    .withStyle(ChatFormatting.GRAY));
        } else if (info.container() != null) {
            ItemContainerContents contents = stack.get(ModComponents.BAG_CONTENTS.get());
            long used = contents == null ? 0 : contents.nonEmptyStream().count();
            tooltip.add((used > 0
                    ? Component.translatable("tooltip.halgventmod.contents", used, info.capacity())
                    : Component.translatable("tooltip.halgventmod.capacity", info.capacity()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (info.carryBonusKg() != 0) {
            tooltip.add(Component.translatable("tooltip.halgventmod.carry_bonus", WeightText.kg(info.carryBonusKg()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
