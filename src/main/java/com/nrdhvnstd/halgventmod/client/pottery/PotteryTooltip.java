package com.nrdhvnstd.halgventmod.client.pottery;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.pottery.Stored;
import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.WareItem;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Строки подсказки гончара: какая глина, как высох и обжёгся сосуд, вода и что внутри. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class PotteryTooltip {
    private PotteryTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = event.getToolTip();
        Integer crack = stack.get(ModComponents.CLAY_CRACK.get());
        if (crack != null && crack != 0) {
            lines.add(Component.translatable(crack > 0 ? "tooltip.halgventmod.pottery.fat_clay" : "tooltip.halgventmod.pottery.grog_clay")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        int flags = Pottery.flags(stack);
        if (Pottery.isGreenware(stack) || Pottery.isRawClay(stack)) {
            line(lines, flags, Pottery.CRACKED, "cracked", ChatFormatting.RED);
            line(lines, flags, Pottery.DRIED, "dried", ChatFormatting.GREEN);
            if ((flags & (Pottery.CRACKED | Pottery.DRIED)) == 0) {
                lines.add(Component.translatable("tooltip.halgventmod.pottery.wet").withStyle(ChatFormatting.AQUA));
            }
        }
        line(lines, flags, Pottery.BLACK, "black", ChatFormatting.GRAY);
        if ((flags & Pottery.BLACK) == 0) {
            line(lines, flags, Pottery.BURNISHED, "burnished", ChatFormatting.GRAY);
        }
        line(lines, flags, Pottery.UNDERFIRED, "underfired", ChatFormatting.GOLD);
        line(lines, flags, Pottery.OVERFIRED, "overfired", ChatFormatting.GOLD);
        if (!(stack.getItem() instanceof WareItem ware)) {
            servedIn(stack, lines);
            return;
        }
        if (Pottery.cracked(stack)) {
            lines.add(Component.translatable("tooltip.halgventmod.pottery.ware_cracked").withStyle(ChatFormatting.RED));
        } else {
            // Шанс в тысячных — процентом, с десятыми, если они есть
            int permille = Pottery.wearChance(stack);
            String percent = permille % 10 == 0 ? String.valueOf(permille / 10)
                    : permille / 10 + net.minecraft.client.resources.language.I18n.get("weight.halgventmod.decimal") + permille % 10;
            lines.add(Component.translatable("tooltip.halgventmod.pottery.wear_chance", percent)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        int capacity = Pottery.waterCapacity(stack);
        if (capacity > 0 && Pottery.water(stack) > 0) {
            lines.add(Component.translatable("tooltip.halgventmod.pottery.water", Pottery.water(stack), capacity)
                    .withStyle(ChatFormatting.AQUA));
        }
        Stored stored = stack.get(ModComponents.STORED.get());
        Level level = Minecraft.getInstance().level;
        if (stored != null && level != null) {
            ItemStack inside = Pottery.unpack(stored, stack, level);
            lines.add(Component.translatable(stored.sealed() ? "tooltip.halgventmod.pottery.inside_sealed"
                    : "tooltip.halgventmod.pottery.inside", inside.getHoverName(), inside.getCount()).withStyle(ChatFormatting.GRAY));
            Integer left = Freshness.daysLeft(inside, level);
            if (left != null) {
                lines.add(Component.translatable("tooltip.halgventmod.pottery.keeps", Math.max(0, left),
                        Math.round(1.0 / Pottery.rate(stack, stored.sealed()))).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (ware.form() == VesselForm.POT && stack.has(ModComponents.POT_USES.get())) {
            lines.add(Component.translatable("tooltip.halgventmod.pottery.pot_uses", Pottery.potUsed(stack),
                    Pottery.potUses(stack)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (ware.form() == VesselForm.LAMP) {
            int fuel = WareItem.lampFuel(stack);
            lines.add(Component.translatable(WareItem.lit(stack) ? "tooltip.halgventmod.pottery.lamp_lit"
                    : "tooltip.halgventmod.pottery.lamp_fuel", Math.round(fuel / 1200.0F)).withStyle(ChatFormatting.GOLD));
        }
    }

    private static void line(List<Component> lines, int flags, int flag, String key, ChatFormatting style) {
        if ((flags & flag) != 0) {
            lines.add(Component.translatable("tooltip.halgventmod.pottery." + key).withStyle(style));
        }
    }

    private static void servedIn(ItemStack stack, List<Component> lines) {
        if (stack.has(ModComponents.SERVED_IN.get())) {
            lines.add(Component.translatable("tooltip.halgventmod.pottery.served_in").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
