package com.nrdhvnstd.halgventmod.client.downed;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.ModKeys;
import com.nrdhvnstd.halgventmod.client.Interaction;
import com.nrdhvnstd.halgventmod.client.social.SocialInput;
import com.nrdhvnstd.halgventmod.downed.Downed;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Надписи у прицела (docs/TZ_downed_and_hp.md, 9): у поверженного над прицелом «Повержен 1:20», под ним
 * «Удерживайте [E] — сдаться» и полоска удержания; у несущего — «[E] Бросить»; с повязкой в руке без цели —
 * «[E] Перевязаться». Полоска «Повержен» над головой у других — в SocialBars.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class DownedHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "downed_hud");

    private static final int TIMER_OFFSET = 14;     // над прицелом
    private static final int HINT_OFFSET = 12;      // под прицелом, как подсказка подбора
    private static final int HOLD_WIDTH = 60;
    private static final int HOLD_HEIGHT = 3;

    private static final int COLOR_TIMER = 0xFFE04040;
    private static final int COLOR_ACTION = 0xFFFFE8A0;   // как действие в UseHint
    private static final int COLOR_FRAME = 0xFF8A8A8A;
    private static final int COLOR_EMPTY = 0xFF2A2A2A;
    private static final int COLOR_FILL = 0xFFC03030;

    private DownedHud() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, DownedHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        Font font = minecraft.font;
        Component key = ModKeys.PICKUP.getTranslatedKeyMessage();
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        Interaction.Kind kind = Interaction.current().kind();
        if (kind == Interaction.Kind.SURRENDER) {
            int seconds = (Downed.ticksLeft(player) + 19) / 20;
            graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.downed.timer",
                    String.format("%d:%02d", seconds / 60, seconds % 60)), x, y - TIMER_OFFSET - font.lineHeight, COLOR_TIMER);
            int hintY = y + HINT_OFFSET;
            graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.downed.surrender", key), x, hintY, COLOR_ACTION);
            float progress = DownedInput.surrenderProgress();
            if (progress > 0) {
                int left = x - HOLD_WIDTH / 2;
                int top = hintY + font.lineHeight + 2;
                graphics.fill(left, top, left + HOLD_WIDTH, top + HOLD_HEIGHT, COLOR_FRAME);
                graphics.fill(left + 1, top + 1, left + HOLD_WIDTH - 1, top + HOLD_HEIGHT - 1, COLOR_EMPTY);
                graphics.fill(left + 1, top + 1, left + 1 + Math.round((HOLD_WIDTH - 2) * progress), top + HOLD_HEIGHT - 1, COLOR_FILL);
            }
        } else if (kind == Interaction.Kind.DROP) {
            graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.downed.drop", key), x, y + HINT_OFFSET, COLOR_ACTION);
        } else if (kind == Interaction.Kind.HEAL_SELF && SocialInput.menu() == null) {
            graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.heal_self", key), x, y + HINT_OFFSET, COLOR_ACTION);
        }
    }
}
