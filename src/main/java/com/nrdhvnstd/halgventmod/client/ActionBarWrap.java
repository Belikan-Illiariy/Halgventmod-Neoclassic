package com.nrdhvnstd.halgventmod.client;

import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Сообщения над хотбаром (action bar) переносятся по строкам: ванильная строка одна, и длинное сообщение — состояние
 * станции, подсказка — уходило за край экрана. Сообщение перехватывается и рисуется здесь же, где ванильное, только
 * в несколько строк вверх от обычного места.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class ActionBarWrap {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "action_bar_wrap");
    private static final int MAX_WIDTH = 360;   // шире не бывает, даже на большом экране
    private static final int SIDE_MARGIN = 20;
    private static final long SHOW_MS = 3500;
    private static final long FADE_MS = 1000;
    private static final int BOTTOM = 68;       // как у ванильной строки: столько от низа экрана

    @Nullable
    private static Component message;
    private static long shownAt;

    private ActionBarWrap() {}

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay()) {
            return;
        }
        message = event.getMessage();
        shownAt = System.currentTimeMillis();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.OVERLAY_MESSAGE, LAYER, ActionBarWrap::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (message == null || minecraft.options.hideGui) {
            return;
        }
        long age = System.currentTimeMillis() - shownAt;
        if (age > SHOW_MS + FADE_MS) {
            message = null;
            return;
        }
        float alpha = age < SHOW_MS ? 1.0F : 1.0F - (float) (age - SHOW_MS) / FADE_MS;
        int a = Mth.clamp(Math.round(alpha * 255), 8, 255);
        Font font = minecraft.font;
        int width = Math.min(MAX_WIDTH, graphics.guiWidth() - 2 * SIDE_MARGIN);
        List<FormattedCharSequence> lines = font.split(message, width);
        int step = font.lineHeight + 2;
        int y = graphics.guiHeight() - BOTTOM - (lines.size() - 1) * step;
        int x = graphics.guiWidth() / 2;
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        // Подложка как у ванильной строки при настройке «фон текста»
        int background = minecraft.options.getBackgroundColor(0.0F);
        if (background != 0) {
            int bgAlpha = Math.round((background >>> 24) * alpha);
            graphics.fill(x - widest / 2 - 2, y - 2, x + widest / 2 + 2, y + lines.size() * step,
                    (bgAlpha << 24) | (background & 0xFFFFFF));
        }
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x - font.width(line) / 2, y, (a << 24) | 0xFFFFFF, true);
            y += step;
        }
    }
}
