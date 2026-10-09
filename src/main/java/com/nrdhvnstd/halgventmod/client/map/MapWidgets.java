package com.nrdhvnstd.halgventmod.client.map;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Мелкие виджеты окна карты. */
final class MapWidgets {
    private static final int COLOR_FRAME = 0xFF000000;
    private static final int COLOR_SELECTED = 0xFFFFE8A0;
    private static final int COLOR_HOVER = 0xFFB8A888;
    private static final int COLOR_TOOL_BACK = 0xC0181410;

    private MapWidgets() {}

    /** Кнопка инструмента справа: значок-предмет, выбранная — в светлой рамке. */
    static final class ToolButton extends AbstractButton {
        private final ItemStack icon;
        private final boolean selected;
        private final Runnable action;

        ToolButton(int x, int y, int size, ItemStack icon, Component title, boolean selected, Runnable action) {
            super(x, y, size, size, title);
            this.icon = icon;
            this.selected = selected;
            this.action = action;
            setTooltip(Tooltip.create(title));
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int frame = selected ? COLOR_SELECTED : isHoveredOrFocused() ? COLOR_HOVER : COLOR_FRAME;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, frame);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, COLOR_TOOL_BACK);
            graphics.renderItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Квадратик цвета; выбранный — в светлой рамке. */
    static final class SwatchButton extends AbstractButton {
        private final int color;
        private final BooleanSupplier selected;
        private final Runnable action;

        SwatchButton(int x, int y, int size, int color, BooleanSupplier selected, Runnable action) {
            super(x, y, size, size, Component.empty());
            this.color = color;
            this.selected = selected;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int frame = selected.getAsBoolean() ? COLOR_SELECTED : isHoveredOrFocused() ? COLOR_HOVER : COLOR_FRAME;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, frame);
            graphics.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0xFF000000 | color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Ползунок целых значений от min до max. */
    static final class IntSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final IntConsumer onChange;
        private final IntFunction<Component> label;

        IntSlider(int x, int y, int width, int height, int min, int max, int current,
                IntConsumer onChange, IntFunction<Component> label) {
            super(x, y, width, height, Component.empty(), (double) (current - min) / (max - min));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.label = label;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(Mth.clamp(value, 0.0, 1.0) * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(current()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
