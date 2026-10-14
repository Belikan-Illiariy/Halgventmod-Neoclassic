package com.nrdhvnstd.halgventmod.client;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.ArmorEquipSlot;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Окно инвентаря: фигурка в рамке, 12 ячеек брони слева от неё, рюкзак, хотбар и вторая рука левее хотбара.
 * Вкладки (MenuTabs) — у верхнего края экрана; над окном — календарь и, в творческом режиме, кнопка «Каталог»
 * (ванильный творческий инвентарь с вкладками предметов).
 * Фон — textures/gui/inventory.png, собран из концепта docs/concept_gui/inventory.png.
 */
public class ArmorInventoryScreen extends EffectRenderingInventoryScreen<PlayerArmorMenu> implements MenuTabScreen {
    // Текстура 512×512 с рисунком в двойном разрешении — рисуется как ванильная 256×256
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "textures/gui/inventory.png");

    private static final int FIGURE_SCALE = 30;
    private static final float FIGURE_Y_OFFSET = 0.0625F;

    // Кнопка «Каталог» — над окном, правее кнопки календаря
    private static final int CATALOG_X = 22;
    private static final int CATALOG_Y = -22;
    private static final int CATALOG_WIDTH = 64;
    private static final int CATALOG_HEIGHT = 20;

    // Кнопка очистки инвентаря (только в творческом) — справа от хотбара, с тем же зазором,
    // что у второй руки слева: рамка ячейки начинается на 1 пиксель левее и выше предмета
    private static final int CLEAR_X = PlayerArmorMenu.INVENTORY_X + 9 * PlayerArmorMenu.SLOT_STEP
            + (PlayerArmorMenu.INVENTORY_X - PlayerArmorMenu.OFFHAND_X - PlayerArmorMenu.SLOT_STEP) - 1;
    private static final int CLEAR_Y = PlayerArmorMenu.HOTBAR_Y - 1;

    @Nullable
    private ClearInventoryButton clearButton; // только в творческом

    public ArmorInventoryScreen(PlayerArmorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PlayerArmorMenu.WIDTH;
        this.imageHeight = PlayerArmorMenu.HEIGHT;
    }

    @Override
    public MenuTab tab() {
        return MenuTab.INVENTORY;
    }

    @Override
    protected void init() {
        super.init();
        for (MenuTabButton button : MenuTabs.createButtons(font, leftPos, imageWidth, MenuTab.INVENTORY)) {
            addRenderableWidget(button);
        }
        if (minecraft.gameMode != null && minecraft.gameMode.hasInfiniteItems()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.catalog"),
                            button -> InventoryOverride.openCatalog())
                    .bounds(leftPos + CATALOG_X, topPos + CATALOG_Y, CATALOG_WIDTH, CATALOG_HEIGHT)
                    .build());
            clearButton = addRenderableWidget(new ClearInventoryButton(menu, leftPos + CLEAR_X, topPos + CLEAR_Y));
        } else {
            clearButton = null;
        }
    }

    // Вещь перетащили на крестик и отпустили — удаляем её. Без этого игра выбросила бы её:
    // кнопка лежит за краем окна. Начатую раскладку по ячейкам (перетаскивание через ячейки) отменяем
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (clearButton != null && !menu.getCarried().isEmpty() && clearButton.isMouseOver(mouseX, mouseY)) {
            isQuickCrafting = false;
            quickCraftSlots.clear();
            clearButton.destroyCarried();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
        graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, imageHeight);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM,
                FIGURE_SCALE, FIGURE_Y_OFFSET, mouseX, mouseY, minecraft.player);
    }
}
