package com.nrdhvnstd.halgventmod.client;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Плашка-вкладка над окном. Вкладка открытого окна подсвечена и не нажимается. */
public class MenuTabButton extends AbstractButton {
    private static final int SELECTED_COLOR = 0xFFE8A0;

    private final MenuTab tab;
    private final boolean selected;

    public MenuTabButton(int x, int y, int width, int height, MenuTab tab, boolean selected) {
        super(x, y, width, height, tab.title());
        this.tab = tab;
        this.selected = selected;
        setTooltip(Tooltip.create(Component.translatable("gui.halgventmod.tab.hint",
                tab.title(), tab.key().getTranslatedKeyMessage())));
        if (selected) {
            setFGColor(SELECTED_COLOR);
        }
    }

    @Override
    public void onPress() {
        if (!selected) {
            MenuTabs.open(tab);
        }
    }

    // Выбранная вкладка всегда нарисована как под курсором
    @Override
    public boolean isHoveredOrFocused() {
        return selected || super.isHoveredOrFocused();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
