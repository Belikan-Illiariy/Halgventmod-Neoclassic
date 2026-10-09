package com.nrdhvnstd.halgventmod.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Боковая панель окна карты (карандаш, стиль): надписи и виджеты столбиком, с прокруткой колёсиком,
 * если не влезают. Координаты y — от верха содержимого; виджеты, вылезающие за край, прячутся.
 */
final class MapSidePanel {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int PADDING = 10;
    private static final int SCROLL_STEP = 14;
    private static final int COLOR_SCROLLBAR = 0x80E8DCC0;

    private record Placed(AbstractWidget widget, int y) {}

    private record Label(Component text, int y, int color) {}

    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final Consumer<AbstractWidget> adder;
    private final List<Placed> widgets = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private int contentHeight;
    private int scroll;

    /** adder — как окно добавляет себе виджет; scroll — прокрутка, сохранённая с прошлого раза. */
    MapSidePanel(int left, int top, int width, int height, int scroll, Consumer<AbstractWidget> adder) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.scroll = scroll;
        this.adder = adder;
    }

    int innerLeft() {
        return left + PADDING;
    }

    int innerWidth() {
        return width - 2 * PADDING;
    }

    int scroll() {
        return scroll;
    }

    void label(Component text, int y, int color) {
        labels.add(new Label(text, y, color));
        contentHeight = Math.max(contentHeight, y + 9);
    }

    /** Виджет с уже заданными x, шириной и высотой; y — от верха содержимого. */
    <T extends AbstractWidget> T widget(T widget, int y) {
        widgets.add(new Placed(widget, y));
        contentHeight = Math.max(contentHeight, y + widget.getHeight());
        adder.accept(widget);
        return widget;
    }

    /** Всё добавлено: расставить по местам с учётом прокрутки. */
    void finish() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
        layout();
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    void scrollBy(double amount) {
        scroll = Mth.clamp(scroll - (int) Math.round(amount * SCROLL_STEP), 0, maxScroll());
        layout();
    }

    private int viewTop() {
        return top + PADDING;
    }

    private int viewBottom() {
        return top + height - PADDING;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (viewBottom() - viewTop()));
    }

    private void layout() {
        for (Placed placed : widgets) {
            int y = viewTop() + placed.y - scroll;
            placed.widget.setY(y);
            placed.widget.visible = y >= viewTop() && y + placed.widget.getHeight() <= viewBottom();
        }
    }

    /** Фон и надписи; виджеты рисует само окно, поверх. */
    void render(GuiGraphics graphics, Font font) {
        graphics.blitSprite(PANEL, left, top, width, height);
        graphics.enableScissor(left, viewTop(), left + width, viewBottom());
        for (Label label : labels) {
            graphics.drawString(font, label.text, innerLeft(), viewTop() + label.y - scroll, label.color, false);
        }
        graphics.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int view = viewBottom() - viewTop();
            int thumb = Math.max(12, view * view / contentHeight);
            int y = viewTop() + (view - thumb) * scroll / max;
            int x = left + width - PADDING / 2 - 1;
            graphics.fill(x, y, x + 2, y + thumb, COLOR_SCROLLBAR);
        }
    }
}
