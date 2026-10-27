package com.nrdhvnstd.halgventmod.client.social;

import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.menu.ArmorEquipSlot;
import com.nrdhvnstd.halgventmod.menu.GearEquipSlot;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;
import com.nrdhvnstd.halgventmod.menu.SocialSearchMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Окно обыска: броня, фигурка, снаряжение, сумки, карманы и вторая рука жертвы — там же, где в своём окне
 * инвентаря, без панели предмета (подсказки вещей обычные). «Обыск: Имя» — строкой над окном.
 * Клики ничего не делают (SocialSearchMenu). Фон — та же заглушка из рамок, что у окна инвентаря.
 */
public class SocialSearchScreen extends AbstractContainerScreen<SocialSearchMenu> {
    private static final int FIGURE_SCALE = 30;
    private static final float FIGURE_Y_OFFSET = 0.0625F;
    private static final int TITLE_GAP = 2;

    public SocialSearchScreen(SocialSearchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PlayerArmorMenu.PANEL_LEFT; // без панели предмета
        this.imageHeight = PlayerArmorMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, font.lineHeight + TITLE_GAP);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos, topPos - font.lineHeight - TITLE_GAP, 0xFFFFFF, true);
        renderTooltip(graphics, mouseX, mouseY);
    }

    // Над вещью — обычная подсказка; над пустой ячейкой брони и снаряжения — её название
    // (над пустой ячейкой сумки подсказку рисует StorageSlotOverlay)
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            Component hint = null;
            if (hoveredSlot instanceof ArmorEquipSlot slot) {
                hint = Component.translatable(slot.armorSlot().langKey());
            } else if (hoveredSlot instanceof GearEquipSlot slot) {
                hint = Component.translatable(slot.gearSlot().langKey());
            }
            if (hint != null) {
                graphics.renderTooltip(font, hint, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        ConquestUi.window(graphics, x, y, x + imageWidth, y + imageHeight);
        ConquestUi.inset(graphics, x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConquestUi.slot(graphics, x + slot.x, y + slot.y, slot instanceof StorageSlot storage && !storage.isOpen());
            }
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM,
                FIGURE_SCALE, FIGURE_Y_OFFSET, mouseX, mouseY, menu.target());
    }
}
