package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.menu.ArmorEquipSlot;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Окно инвентаря: фигурка, 12 ячеек брони вокруг неё, вторая рука, рюкзак и хотбар.
 * В творческом режиме — кнопка «Каталог» (ванильный творческий инвентарь с вкладками предметов).
 * Фон пока рисуется прямоугольниками; когда художник нарисует окно — заменить в renderBg.
 */
public class ArmorInventoryScreen extends EffectRenderingInventoryScreen<PlayerArmorMenu> {
    // Цвета временного фона — как у ванильных окон
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_INNER = 0xFF8B8B8B;
    private static final int FIGURE_BACKGROUND = 0xFF000000;
    private static final int SEPARATOR_HEIGHT = 18;

    private static final int FIGURE_SCALE = 30;
    private static final float FIGURE_Y_OFFSET = 0.0625F;

    // Кнопка «Каталог» — над окном, правее кнопки календаря
    private static final int CATALOG_X = 22;
    private static final int CATALOG_Y = -22;
    private static final int CATALOG_WIDTH = 64;
    private static final int CATALOG_HEIGHT = 20;

    public ArmorInventoryScreen(PlayerArmorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PlayerArmorMenu.WIDTH;
        this.imageHeight = PlayerArmorMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft.gameMode != null && minecraft.gameMode.hasInfiniteItems()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.catalog"),
                            button -> InventoryOverride.openCatalog())
                    .bounds(leftPos + CATALOG_X, topPos + CATALOG_Y, CATALOG_WIDTH, CATALOG_HEIGHT)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    // Над пустой ячейкой брони — название слота
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot instanceof ArmorEquipSlot slot && !slot.hasItem()) {
            graphics.renderTooltip(font, Component.translatable(slot.armorSlot().langKey()), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    // Подписей нет, как в ванильном инвентаре
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        drawPanel(graphics, x, y, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            drawSlot(graphics, x + slot.x, y + slot.y);
        }
        drawSeparator(graphics, x + PlayerArmorMenu.SEPARATOR_X, y + PlayerArmorMenu.HOTBAR_Y - 1);

        int left = x + PlayerArmorMenu.FIGURE_LEFT;
        int top = y + PlayerArmorMenu.FIGURE_TOP;
        int right = x + PlayerArmorMenu.FIGURE_RIGHT;
        int bottom = y + PlayerArmorMenu.FIGURE_BOTTOM;
        graphics.fill(left, top, right, bottom, FIGURE_BACKGROUND);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left, top, right, bottom,
                FIGURE_SCALE, FIGURE_Y_OFFSET, mouseX, mouseY, minecraft.player);
    }

    // Панель с объёмной рамкой: светлый верх и лево, тёмный низ и право
    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width - 1, y + 1, LIGHT);
        graphics.fill(x, y, x + 1, y + height - 1, LIGHT);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, DARK);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, DARK);
    }

    // Вдавленная вертикальная перегородка высотой в ячейку: тёмная линия и светлая рядом
    private static void drawSeparator(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 1, y + SEPARATOR_HEIGHT, SLOT_SHADOW);
        graphics.fill(x + 1, y, x + 2, y + SEPARATOR_HEIGHT, LIGHT);
    }

    // Ячейка 18×18 вокруг места предмета 16×16: тень сверху и слева, блик снизу и справа
    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x, y, x + 17, y + 17, LIGHT);
        graphics.fill(x, y, x + 16, y + 16, SLOT_INNER);
    }
}
