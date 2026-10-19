package com.nrdhvnstd.halgventmod.client.squad;

import com.mojang.blaze3d.platform.Lighting;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Общее для окон отряда: оформление (тёмные панели с каймой и свитки с названием — как в окне «Персонаж»),
 * цвета текста и рисование знамени.
 */
final class SquadUi {
    static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    static final ResourceLocation TITLE = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/title");

    static final int COLOR_TITLE = 0xFF3B2A1A;   // тёмный на свитке
    static final int COLOR_TEXT = 0xFFE8DCC0;
    static final int COLOR_DIM = 0xFFB8A888;
    static final int COLOR_OFFLINE = 0xFF8A8070;
    static final int COLOR_GOLD = 0xFFFFAA00;    // как ChatFormatting.GOLD — название отряда
    static final int COLOR_ERROR = 0xFFFF6B5B;
    static final int COLOR_HOVER_ROW = 0x30FFFFFF;
    static final int COLOR_ICON = 0xFF9A9A9A;    // серая галочка / крестик
    static final int COLOR_ACCEPT = 0xFF55FF55;
    static final int COLOR_DECLINE = 0xFFFF5555;

    static final int INNER = 8;          // внутри панели, за каймой
    static final int TITLE_H = 12;       // от верха панели до первой строки
    private static final int TITLE_RISE = 4;
    private static final int TITLE_HEIGHT = 11;
    private static final int TITLE_PADDING = 12;

    // Знамя: модель флага 20 × 40 «пикселей» модели
    static final int BANNER_W = 20;
    static final int BANNER_H = 40;

    private static ModelPart flag;

    private SquadUi() {}

    /** Тёмная панель с каймой и свиток с названием посередине её верхнего края. */
    static void panel(GuiGraphics graphics, Font font, int x, int y, int width, int height, Component title) {
        graphics.blitSprite(PANEL, x, y, width, height);
        if (title != null) {
            int titleWidth = font.width(title) + 2 * TITLE_PADDING;
            int titleX = x + (width - titleWidth) / 2;
            int titleY = y - TITLE_RISE;
            graphics.blitSprite(TITLE, titleX, titleY, titleWidth, TITLE_HEIGHT);
            graphics.drawString(font, title, titleX + TITLE_PADDING, titleY + 2, COLOR_TITLE, false);
        }
    }

    /**
     * Знамя, как превью ткацкого станка: левый верхний угол полотна — (left, top),
     * scale — экранных пикселей на пиксель модели (полотно BANNER_W × BANNER_H пикселей модели).
     */
    static void banner(GuiGraphics graphics, float left, float top, float scale, DyeColor base, BannerPatternLayers patterns) {
        if (flag == null) {
            flag = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
        }
        Lighting.setupForFlatItems();
        graphics.pose().pushPose();
        graphics.pose().translate(left - 2 * scale, top + 44 * scale, 0.0F);
        graphics.pose().scale(24 * scale, 24 * scale, 1.0F);
        graphics.pose().translate(0.5F, -0.5F, 0.5F);
        graphics.pose().scale(2.0F / 3.0F, 2.0F / 3.0F, -2.0F / 3.0F);
        flag.xRot = 0.0F;
        flag.y = -32.0F;
        BannerRenderer.renderPatterns(graphics.pose(), graphics.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY,
                flag, ModelBakery.BANNER_BASE, true, base, patterns);
        graphics.pose().popPose();
        graphics.flush();
        Lighting.setupFor3DItems();
    }

    /** Строка, обрезанная по ширине с многоточием. */
    static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
