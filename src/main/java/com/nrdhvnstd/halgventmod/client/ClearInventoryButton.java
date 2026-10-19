package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.network.ClearInventoryPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Кнопка с крестиком — как ванильная ячейка удаления в творческом инвентаре.
 * Вещь на курсоре (принесённая кликом или перетащенная) — удаляется только она;
 * Shift+щелчок с пустым курсором — очищается всё, как в ванили. Простой щелчок с пустым курсором ничего не делает.
 * Рисунок берётся прямо из ванильной текстуры вкладки «Инвентарь» творческого режима.
 */
public class ClearInventoryButton extends AbstractButton {
    public static final int SIZE = 18;

    private static final ResourceLocation CREATIVE_INVENTORY =
            ResourceLocation.withDefaultNamespace("textures/gui/container/creative_inventory/tab_inventory.png");
    // Ячейка с крестиком на этой текстуре (вместе с рамкой)
    private static final int ICON_U = 172;
    private static final int ICON_V = 111;
    private static final int HIGHLIGHT = 0x80FFFFFF; // подсветка при наведении, как у ячеек

    // «Удалить предмет» (ванильная подпись) и под ней — как очистить всё
    private static final Tooltip HINT = Tooltip.create(Component.translatable("inventory.binSlot")
            .append("\n")
            .append(Component.translatable("gui.halgventmod.clear_inventory").withStyle(ChatFormatting.GRAY)));

    private final AbstractContainerMenu menu;

    public ClearInventoryButton(AbstractContainerMenu menu, int x, int y) {
        super(x, y, SIZE, SIZE, Component.translatable("gui.halgventmod.clear_inventory"));
        this.menu = menu;
    }

    @Override
    public void onPress() {
        if (!menu.getCarried().isEmpty()) {
            destroyCarried();
        } else if (Screen.hasShiftDown()) {
            PacketDistributor.sendToServer(ClearInventoryPayload.ALL);
        }
    }

    /** Удалить вещь на курсоре. Убираем её и у себя сразу — иначе отпускание кнопки мыши за окном её выбросит. */
    public void destroyCarried() {
        PacketDistributor.sendToServer(ClearInventoryPayload.CARRIED);
        menu.setCarried(ItemStack.EMPTY);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // С вещью на курсоре подсказка мешала бы — как в ванили, без неё
        setTooltip(menu.getCarried().isEmpty() ? HINT : null);
        graphics.blit(CREATIVE_INVENTORY, getX(), getY(), ICON_U, ICON_V, SIZE, SIZE);
        if (isHoveredOrFocused()) {
            graphics.fill(getX() + 1, getY() + 1, getX() + SIZE - 1, getY() + SIZE - 1, HIGHLIGHT);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
