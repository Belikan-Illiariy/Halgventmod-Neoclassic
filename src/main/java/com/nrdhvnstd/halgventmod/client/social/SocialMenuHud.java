package com.nrdhvnstd.halgventmod.client.social;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.ModKeys;
import com.nrdhvnstd.halgventmod.social.Social;
import com.nrdhvnstd.halgventmod.social.SocialAction;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Подсказка на игрока и круговое меню (SocialInput) посередине экрана. Подсказка — как у блоков (UseHint):
 * сверху имя (RP-имя, в хундсгугеле — «Неизвестный в шлеме»), под ним «[E] Взаимодействовать».
 * Меню: кольцо секторов, пункт под курсором выдвинут наружу и ярче, в центре — «Отмена».
 * Цвета — временные, до рисунка художника.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class SocialMenuHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "social_menu");

    private static final float RING_INNER = SocialInput.MENU_CENTER_RADIUS + 4;
    private static final float RING_OUTER = 84;
    private static final float LABEL_RADIUS = (RING_INNER + RING_OUTER) / 2;
    private static final float POP_OUT = 12;              // пункт под курсором выдвигается наружу ⚠️
    private static final float GAP = 0.04F;               // просвет между секторами, радианы
    private static final int ARC_STEPS = 16;              // отрезков дуги на сектор
    private static final int CIRCLE_STEPS = 32;
    private static final int LABEL_WIDTH = 64;            // длинные подписи — в две строки
    private static final int CURSOR_SIZE = 2;

    // Цвета (ARGB)
    private static final int COLOR_NAME = 0xFFFFFFFF;
    private static final int COLOR_ACTION = 0xFFFFE8A0;   // как действие в UseHint
    private static final int COLOR_SECTOR = 0xA0181410;
    private static final int COLOR_SECTOR_HOVER = 0xD0604A28;
    private static final int COLOR_CENTER = 0xA0181410;
    private static final int COLOR_CENTER_HOVER = 0xD0502020;
    private static final int COLOR_TEXT = 0xFFC8C0B0;
    private static final int COLOR_TEXT_HOVER = 0xFFFFFFFF;
    private static final int COLOR_CURSOR = 0xFFFFFFFF;

    private SocialMenuHud() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, SocialMenuHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        SocialInput.Menu menu = SocialInput.menu();
        if (menu != null) {
            renderMenu(graphics, minecraft.font, menu);
            return;
        }
        Player target = SocialInput.target();
        if (target != null) {
            Font font = minecraft.font;
            int x = graphics.guiWidth() / 2;
            int y = graphics.guiHeight() / 2;
            graphics.drawCenteredString(font, Social.shownName(target), x, y - font.lineHeight, COLOR_NAME);
            graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.social.interact",
                    ModKeys.PICKUP.getTranslatedKeyMessage()), x, y + 1, COLOR_ACTION);
        }
    }

    private static void renderMenu(GuiGraphics graphics, Font font, SocialInput.Menu menu) {
        float cx = graphics.guiWidth() / 2.0F;
        float cy = graphics.guiHeight() / 2.0F;
        List<SocialAction> options = menu.options();
        int hovered = menu.hovered();
        float step = (float) (2 * Math.PI / options.size());

        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull(); // обход вершин у секторов не выверяли
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < options.size(); i++) {
            float middle = (float) (-Math.PI / 2 + i * step);
            float pop = i == hovered ? POP_OUT : 0;
            float ox = cx + (float) Math.cos(middle) * pop;
            float oy = cy + (float) Math.sin(middle) * pop;
            sector(buffer, matrix, ox, oy, middle - step / 2 + GAP, middle + step / 2 - GAP,
                    i == hovered ? COLOR_SECTOR_HOVER : COLOR_SECTOR);
        }
        disc(buffer, matrix, cx, cy, SocialInput.MENU_CENTER_RADIUS, hovered < 0 ? COLOR_CENTER_HOVER : COLOR_CENTER);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        for (int i = 0; i < options.size(); i++) {
            float middle = (float) (-Math.PI / 2 + i * step);
            float radius = LABEL_RADIUS + (i == hovered ? POP_OUT : 0);
            label(graphics, font, Component.translatable(options.get(i).menuKey()),
                    cx + (float) Math.cos(middle) * radius, cy + (float) Math.sin(middle) * radius,
                    i == hovered ? COLOR_TEXT_HOVER : COLOR_TEXT);
        }
        label(graphics, font, Component.translatable("social.halgventmod.action.cancel"), cx, cy,
                hovered < 0 ? COLOR_TEXT_HOVER : COLOR_TEXT);

        int px = Math.round(cx + menu.cursorX());
        int py = Math.round(cy + menu.cursorY());
        graphics.fill(px - CURSOR_SIZE, py - CURSOR_SIZE, px + CURSOR_SIZE, py + CURSOR_SIZE, COLOR_CURSOR);
    }

    // Подпись по центру точки, в одну-две строки
    private static void label(GuiGraphics graphics, Font font, Component text, float x, float y, int color) {
        List<FormattedCharSequence> lines = font.split(text, LABEL_WIDTH);
        int top = Math.round(y - lines.size() * font.lineHeight / 2.0F);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, Math.round(x - font.width(line) / 2.0F), top, color, true);
            top += font.lineHeight;
        }
    }

    // Кусок кольца от угла from до to
    private static void sector(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, float from, float to, int color) {
        for (int s = 0; s < ARC_STEPS; s++) {
            float a0 = from + (to - from) * s / ARC_STEPS;
            float a1 = from + (to - from) * (s + 1) / ARC_STEPS;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            vertex(buffer, matrix, cx + c0 * RING_INNER, cy + s0 * RING_INNER, color);
            vertex(buffer, matrix, cx + c1 * RING_OUTER, cy + s1 * RING_OUTER, color);
            vertex(buffer, matrix, cx + c0 * RING_OUTER, cy + s0 * RING_OUTER, color);
            vertex(buffer, matrix, cx + c0 * RING_INNER, cy + s0 * RING_INNER, color);
            vertex(buffer, matrix, cx + c1 * RING_INNER, cy + s1 * RING_INNER, color);
            vertex(buffer, matrix, cx + c1 * RING_OUTER, cy + s1 * RING_OUTER, color);
        }
    }

    private static void disc(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, float radius, int color) {
        for (int s = 0; s < CIRCLE_STEPS; s++) {
            double a0 = 2 * Math.PI * s / CIRCLE_STEPS;
            double a1 = 2 * Math.PI * (s + 1) / CIRCLE_STEPS;
            vertex(buffer, matrix, cx, cy, color);
            vertex(buffer, matrix, cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius, color);
            vertex(buffer, matrix, cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius, color);
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, int color) {
        buffer.addVertex(matrix, x, y, 0).setColor(color);
    }
}
