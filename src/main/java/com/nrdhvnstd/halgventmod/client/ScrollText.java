package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Текст в прямоугольнике: перенос по словам, \n — новая строка, прокрутка колёсиком, ползунок — только если не влез.
 * С keys = true строка вида «Слово: текст» рисует часть до двоеточия тусклым цветом (блок «Сведения»).
 * Окно само решает, когда звать scroll: обычно — когда курсор над текстом (contains).
 */
public final class ScrollText {
    private static final int LINE = 10;
    private static final int SCROLL_STEP = 2 * LINE;
    private static final int SCROLLBAR_GAP = 5; // справа под ползунок
    private static final int KEY_MAX = 32;      // длиннее — это уже не «Слово:», а просто фраза с двоеточием

    private final boolean keys;
    private final int color;
    private final int keyColor;
    private final int barColor;
    private int x;
    private int y;
    private int width;
    private int height;
    private String text = "";
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;

    public ScrollText(boolean keys, int color, int keyColor) {
        this(keys, color, keyColor, 0x80E8DCC0);
    }

    /** barColor — цвет ползунка (на светлом пергаменте — тёмный). */
    public ScrollText(boolean keys, int color, int keyColor, int barColor) {
        this.keys = keys;
        this.color = color;
        this.keyColor = keyColor;
        this.barColor = barColor;
    }

    /** Место и текст; тот же текст на том же месте — прокрутка сохраняется. */
    public void set(Font font, String newText, int newX, int newY, int newWidth, int newHeight) {
        boolean same = newText.equals(text) && newWidth == width;
        x = newX;
        y = newY;
        width = newWidth;
        height = Math.max(0, newHeight);
        if (!same) {
            text = newText;
            lines = wrap(font);
        }
        scroll = Mth.clamp(same ? scroll : 0, 0, maxScroll());
    }

    private List<FormattedCharSequence> wrap(Font font) {
        List<FormattedCharSequence> result = new ArrayList<>();
        int inner = Math.max(1, width - SCROLLBAR_GAP);
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                result.add(FormattedCharSequence.EMPTY);
                continue;
            }
            result.addAll(font.split(paragraph(paragraph), inner));
        }
        return result;
    }

    private Component paragraph(String paragraph) {
        int colon = paragraph.indexOf(':');
        if (!keys || colon <= 0 || colon > KEY_MAX) {
            return Component.literal(paragraph).withColor(color);
        }
        MutableComponent line = Component.literal(paragraph.substring(0, colon + 1)).withColor(keyColor);
        return line.append(Component.literal(paragraph.substring(colon + 1)).withColor(color));
    }

    private int maxScroll() {
        return Math.max(0, lines.size() * LINE - height);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void scroll(double amount) {
        scroll = Mth.clamp(scroll - (int) Math.round(amount * SCROLL_STEP), 0, maxScroll());
    }

    public void render(GuiGraphics graphics, Font font) {
        if (height <= 0) {
            return;
        }
        graphics.enableScissor(x, y, x + width, y + height);
        int first = scroll / LINE;
        for (int i = first; i < lines.size(); i++) {
            int lineY = y + i * LINE - scroll;
            if (lineY >= y + height) {
                break;
            }
            graphics.drawString(font, lines.get(i), x, lineY, color, false);
        }
        graphics.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int content = lines.size() * LINE;
            int thumb = Math.max(8, height * height / content);
            int thumbY = y + (height - thumb) * scroll / max;
            int barX = x + width - 2;
            graphics.fill(barX, thumbY, barX + 2, thumbY + thumb, barColor);
        }
    }
}
