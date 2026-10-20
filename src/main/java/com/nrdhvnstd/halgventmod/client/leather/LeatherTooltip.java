package com.nrdhvnstd.halgventmod.client.leather;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.Animals;
import com.nrdhvnstd.halgventmod.leather.Hide;
import com.nrdhvnstd.halgventmod.leather.LeatherCare;
import com.nrdhvnstd.halgventmod.leather.Tannage;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Строки подсказки кожевника: чья шкура и сколько кусков, во что высохнет, когда смазана. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class LeatherTooltip {
    private LeatherTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = event.getToolTip();
        Hide hide = stack.get(ModComponents.HIDE.get());
        if (hide != null) {
            Component animal = Component.translatable(Animals.langKey(hide.animal()));
            lines.add(Component.translatable("tooltip.halgventmod.hide", animal, hide.pieces())
                    .withStyle(ChatFormatting.GRAY));
            boolean raw = stack.is(ModItems.RAW_HIDE.get()) || stack.is(ModItems.SALTED_HIDE.get());
            if (raw) {
                lines.add(Component.translatable(hide.fleshed() ? "tooltip.halgventmod.fleshed"
                        : "tooltip.halgventmod.not_fleshed").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (raw && hide.fur()) {
                lines.add(Component.translatable("tooltip.halgventmod.has_fur").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        Tannage tannage = Tannage.of(stack);
        if (tannage != null) {
            lines.add(Component.translatable("tooltip.halgventmod.dries_into",
                    Component.translatable(tannage.langKey())).withStyle(ChatFormatting.GRAY));
        }
        Integer oiled = stack.get(ModComponents.OILED_DAY.get());
        if (oiled != null && Minecraft.getInstance().level != null) {
            int today = (int) Calendar.today(Minecraft.getInstance().level);
            if (LeatherCare.isDry(stack, today)) {
                lines.add(Component.translatable("tooltip.halgventmod.dried_out").withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.translatable("tooltip.halgventmod.oiled",
                        Math.max(0, LeatherCare.DRY_AFTER_DAYS - (today - oiled))).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
