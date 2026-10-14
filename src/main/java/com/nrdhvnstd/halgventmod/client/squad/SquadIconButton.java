package com.nrdhvnstd.halgventmod.client.squad;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Квадратная кнопка без текста: ванильная рамка кнопки, внутри — значок-предмет,
 * в углу — пометка состояния (красный ✖ — выключено, золотая ★ — частично). Что она делает — в подсказке.
 */
class SquadIconButton extends Button {
    static final int SIZE = 20;

    enum Badge { NONE, OFF, PARTIAL }

    private final ItemStack icon;
    private final Badge badge;

    SquadIconButton(int x, int y, ItemStack icon, Badge badge, Component tooltip, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.badge = badge;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.renderItem(icon, getX() + 2, getY() + 2);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200); // поверх значка
        @Nullable String mark = switch (badge) {
            case NONE -> null;
            case OFF -> "✖";
            case PARTIAL -> "★";
        };
        if (mark != null) {
            int color = badge == Badge.OFF ? SquadUi.COLOR_DECLINE : SquadUi.COLOR_GOLD;
            graphics.drawString(Minecraft.getInstance().font, mark, getX() + SIZE - 8, getY() + SIZE - 10,
                    color, true);
        }
        if (!active) {
            graphics.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1, 0x60000000);
        }
        graphics.pose().popPose();
    }

    // Нажимается только ЛКМ; ПКМ по значку карты ловит окно отряда
    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0;
    }
}
