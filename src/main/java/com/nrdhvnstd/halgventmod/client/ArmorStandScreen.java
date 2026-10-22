package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.menu.ArmorEquipSlot;
import com.nrdhvnstd.halgventmod.menu.ArmorStandMenu;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Окно стойки для брони: броня стойки 4×3 и её фигурка — там же, где в окне инвентаря броня и фигурка игрока;
 * под ними — сумки, карманы и вторая рука игрока. Название стойки — строкой над окном.
 * Фон — та же заглушка из рамок, что у окна инвентаря.
 */
public class ArmorStandScreen extends AbstractContainerScreen<ArmorStandMenu> {
    private static final int FIGURE_SCALE = 30;
    private static final float FIGURE_Y_OFFSET = 0.0625F;
    private static final int TITLE_GAP = 2; // зазор между названием и верхним краем окна

    public ArmorStandScreen(ArmorStandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = ArmorStandMenu.WIDTH;
        this.imageHeight = ArmorStandMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, font.lineHeight + TITLE_GAP); // название над окном не уходит за край экрана
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos, topPos - font.lineHeight - TITLE_GAP, 0xFFFFFF, true);
        renderTooltip(graphics, mouseX, mouseY);
    }

    // Подсказки вещей — обычные; над пустой ячейкой брони — название ячейки
    // (над пустой ячейкой сумки подсказку рисует StorageSlotOverlay)
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot instanceof ArmorEquipSlot slot && !slot.hasItem()) {
            graphics.renderTooltip(font, Component.translatable(slot.armorSlot().langKey()), mouseX, mouseY);
            return;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    // Подписей внутри окна нет, как в окне инвентаря
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Стиль Conquest Reforged, как в окне инвентаря
        ConquestUi.window(graphics, x, y, x + imageWidth, y + imageHeight);
        ConquestUi.inset(graphics, x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConquestUi.slot(graphics, x + slot.x, y + slot.y, slot instanceof StorageSlot storage && !storage.isOpen());
            }
        }
        StorageSlotOverlay.drawDividers(graphics, this, x, y); // черта между спиной и подсумками
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM,
                FIGURE_SCALE, FIGURE_Y_OFFSET, mouseX, mouseY, menu.stand());
    }
}
