package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Временное окно для вкладок, у которых ещё нет своего окна (сейчас — «Кодекс»): рамка размером с инвентарь
 * (чтобы вкладки не прыгали при переключении), название и «в разработке».
 */
public class TabPlaceholderScreen extends Screen implements MenuTabScreen {
    private static final int WIDTH = PlayerArmorMenu.WIDTH;
    private static final int HEIGHT = PlayerArmorMenu.HEIGHT;

    // ===== Цвета (ARGB) =====
    private static final int COLOR_EDGE = 0xFF1E1610;
    private static final int COLOR_RIM = 0xFF5A4632;
    private static final int COLOR_FILL = 0xFF3A2C20;
    private static final int COLOR_TITLE = 0xFFE8DCC0;
    private static final int COLOR_TEXT = 0xFFB8A888;

    private final MenuTab tab;
    private int left;
    private int top;

    public TabPlaceholderScreen(MenuTab tab) {
        super(tab.title());
        this.tab = tab;
    }

    @Override
    public MenuTab tab() {
        return tab;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        for (MenuTabButton button : MenuTabs.createButtons(font, left, WIDTH, tab)) {
            addRenderableWidget(button);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawWindow(graphics, left, top, WIDTH, HEIGHT);
    }

    /** Рамка окна, пока у окон-вкладок нет своих текстур. */
    public static void drawWindow(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOR_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOR_RIM);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, COLOR_FILL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = left + WIDTH / 2;
        graphics.drawCenteredString(font, title, centerX, top + 10, COLOR_TITLE);
        graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.tab.wip"),
                centerX, top + HEIGHT / 2 - font.lineHeight / 2, COLOR_TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
