package com.nrdhvnstd.halgventmod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Строка, которая не лезет в ширину, рисуется мельче — ровно настолько, чтобы влезть (не мельче {@link #MIN_SCALE}). */
public final class TextFit {
    public static final float MIN_SCALE = 0.6F;

    private TextFit() {}

    public static void draw(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (width <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = Math.max(MIN_SCALE, maxWidth / (float) width);
        g.pose().pushPose();
        g.pose().translate(x, y + font.lineHeight * (1 - scale) / 2, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** То же по центру. */
    public static void drawCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int maxWidth, int color) {
        int width = Math.min(maxWidth, font.width(text));
        draw(g, font, text, centerX - width / 2, y, maxWidth, color);
    }
}
