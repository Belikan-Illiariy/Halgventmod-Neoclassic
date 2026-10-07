package com.nrdhvnstd.halgventmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Кнопка календаря над окном инвентаря. Пока с ванильными часами вместо своей иконки. */
public class CalendarButton extends AbstractButton {
    private static final ItemStack ICON = new ItemStack(Items.CLOCK);

    public CalendarButton(int x, int y) {
        super(x, y, 20, 20, Component.translatable("calendar.halgventmod.screen.title"));
        setTooltip(Tooltip.create(Component.translatable("calendar.halgventmod.button",
                ModKeys.CALENDAR.getTranslatedKeyMessage())));
    }

    @Override
    public void onPress() {
        CalendarInput.openFromContainer();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Ванильная кнопка без надписи и поверх неё значок часов
        Component message = getMessage();
        setMessage(Component.empty());
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        setMessage(message);
        graphics.renderItem(ICON, getX() + 2, getY() + 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}