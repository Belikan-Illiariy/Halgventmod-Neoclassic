package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Окно у курсора в стиле меню карты вместо ванильной подсказки: заголовок, текст с переносом по словам,
 * ниже тусклые строки. Стоит справа-снизу от курсора, у края экрана переворачивается внутрь.
 * Им пользуются неоткрытые точки карты и знамёна в книге-гербовнике.
 */
public final class MapTooltipBox {
    private static final int MAX_WIDTH = 200; // ⚠️ с полями
    private static final int PAD = 5;
    private static final int OFFSET = 12;     // от курсора
    private static final int EDGE = 2;        // от края экрана
    private static final int LINE = 10;
    private static final int GAP = 3;         // между заголовком, текстом и тусклыми строками
    private static final float Z = 400.0F;    // поверх значков и виджетов

    private static final int COLOR_PANEL = 0xF0241C14;
    private static final int COLOR_FRAME = 0xFF6E6250;
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_DIM = 0xFFB8A888;

    private MapTooltipBox() {}

    /** title может быть null; text — с переносом (пустой — без блока); dim — тусклые строки внизу. */
    public static void render(GuiGraphics graphics, Font font, @Nullable Component title, @Nullable Component text,
                              List<Component> dim, int mouseX, int mouseY) {
        int inner = MAX_WIDTH - 2 * PAD;
        List<FormattedCharSequence> titleLines = title != null ? font.split(title, inner) : List.of();
        List<FormattedCharSequence> textLines = text != null && !text.getString().isBlank() ? font.split(text, inner) : List.of();
        List<FormattedCharSequence> dimLines = new ArrayList<>();
        for (Component line : dim) {
            dimLines.addAll(font.split(line, inner));
        }

        int width = 0;
        for (List<FormattedCharSequence> block : List.of(titleLines, textLines, dimLines)) {
            for (FormattedCharSequence line : block) {
                width = Math.max(width, font.width(line));
            }
        }
        int blocks = (titleLines.isEmpty() ? 0 : 1) + (textLines.isEmpty() ? 0 : 1) + (dimLines.isEmpty() ? 0 : 1);
        if (blocks == 0) {
            return;
        }
        width += 2 * PAD;
        int height = (titleLines.size() + textLines.size() + dimLines.size()) * LINE - 1 + (blocks - 1) * GAP + 2 * PAD;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int x = mouseX + OFFSET;
        int y = mouseY + OFFSET;
        if (x + width > screenWidth - EDGE) {
            x = mouseX - OFFSET - width;
        }
        if (y + height > screenHeight - EDGE) {
            y = mouseY - OFFSET - height;
        }
        x = Math.max(EDGE, x);
        y = Math.max(EDGE, y);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, Z);
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
        graphics.renderOutline(x, y, width, height, COLOR_FRAME);
        int lineY = y + PAD;
        lineY = lines(graphics, font, titleLines, x + PAD, lineY, COLOR_TITLE);
        lineY = lines(graphics, font, textLines, x + PAD, lineY, COLOR_TEXT);
        lines(graphics, font, dimLines, x + PAD, lineY, COLOR_DIM);
        graphics.pose().popPose();
    }

    private static int lines(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int x, int y, int color) {
        if (lines.isEmpty()) {
            return y;
        }
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += LINE;
        }
        return y + GAP;
    }
}
