package com.nrdhvnstd.halgventmod.client.npc;

import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.client.StorageSlotOverlay;
import com.nrdhvnstd.halgventmod.menu.ArmorEquipSlot;
import com.nrdhvnstd.halgventmod.menu.NpcGearMenu;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.network.NpcEditPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Снаряжение НПС как инвентарь: броня 4×3, под ней две руки НПС, фигурка НПС, ниже — сумки, карманы и вторая рука
 * администратора. Вещи тянутся из инвентаря в ячейки и обратно. «Настройки» возвращает в окно настройки НПС.
 * Раскладка и фон — как у окна стойки для брони (ArmorStandScreen).
 */
public class NpcGearScreen extends AbstractContainerScreen<NpcGearMenu> {
    private static final int FIGURE_SCALE = 30;
    private static final float FIGURE_Y_OFFSET = 0.0625F;
    private static final int TITLE_GAP = 2;
    private static final int BUTTON_WIDTH = PlayerArmorMenu.PANEL_RIGHT - PlayerArmorMenu.PANEL_LEFT;
    private static final int BUTTON_HEIGHT = 20;

    public NpcGearScreen(NpcGearMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = NpcGearMenu.WIDTH;
        this.imageHeight = NpcGearMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, font.lineHeight + TITLE_GAP);
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.npc.gear.back"),
                button -> PacketDistributor.sendToServer(
                        NpcEditPayload.of(menu.npc().getId(), NpcEditPayload.Action.OPEN_SETTINGS)))
                .bounds(leftPos + PlayerArmorMenu.PANEL_LEFT, topPos + PlayerArmorMenu.PANEL_TOP, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos, topPos - font.lineHeight - TITLE_GAP, 0xFFFFFF, true);
        renderTooltip(graphics, mouseX, mouseY);
    }

    // Над пустой ячейкой — её название: слот брони или рука
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()) {
            if (hoveredSlot instanceof ArmorEquipSlot slot) {
                graphics.renderTooltip(font, Component.translatable(slot.armorSlot().langKey()), mouseX, mouseY);
                return;
            }
            int hand = hoveredSlot.index - NpcGearMenu.HANDS_START;
            if (hand == 0 || hand == 1) {
                graphics.renderTooltip(font, Component.translatable(hand == 0
                        ? "gui.halgventmod.npc.edit.gear.main_hand" : "gui.halgventmod.npc.edit.gear.off_hand"), mouseX, mouseY);
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
        StorageSlotOverlay.drawDividers(graphics, this, x, y);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM,
                FIGURE_SCALE, FIGURE_Y_OFFSET, mouseX, mouseY, menu.npc());
    }
}
