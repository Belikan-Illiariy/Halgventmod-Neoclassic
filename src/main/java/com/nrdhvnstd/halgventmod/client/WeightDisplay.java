package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.weight.Encumbrance;
import com.nrdhvnstd.halgventmod.weight.ModWeights;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Вес в подсказке предмета и счётчик «22,2 кг / 40 кг» над инвентарём. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class WeightDisplay {
    private WeightDisplay() {}

    // LOWEST — чтобы строка веса оказалась в самом низу, после всех остальных
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        double grams = ModWeights.stackGrams(event.getItemStack());
        if (grams <= 0) {
            return;
        }
        event.getToolTip().add(Component.translatable("tooltip.halgventmod.weight", WeightText.format(grams))
                .withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ArmorInventoryScreen screen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        double kg = ModWeights.inventoryGrams(player) / 1000.0;
        double capacity = Encumbrance.capacity(player);
        Component text = Component.translatable("hud.halgventmod.carry", WeightText.kg(kg), WeightText.kg(capacity));

        int color = switch (Encumbrance.tier(kg, capacity)) {
            case NONE -> 0xFFFFFF;       // белый
            case BURDEN -> 0xFFFF55;     // жёлтый
            case ENCUMBERED -> 0xFFAA00; // оранжевый
            case OVERLOADED -> 0xFF5555; // красный
        };

        // Над окном инвентаря, прижато к правому краю
        Font font = minecraft.font;
        int x = screen.getGuiLeft() + screen.getXSize() - font.width(text);
        int y = screen.getGuiTop() - font.lineHeight - 2;
        event.getGuiGraphics().drawString(font, text, x, y, color, true);
    }
}