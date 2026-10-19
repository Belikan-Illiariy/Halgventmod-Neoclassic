package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.storage.StorageArea;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Ячейки сумок в чужих окнах (сундук, печь, окна других модов). Раскладка там уже наша:
 * сетка 9×3 бывшего рюкзака = спина 3×5, подсумок 1 3×2, подсумок 2 3×2 (AbstractContainerMenuMixin).
 * Здесь — как в нашем окне: закрытые ячейки тёмные, между областями черта,
 * над пустой ячейкой подсказка «Спина» / «Подсумок 1» / чем она открывается.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class StorageSlotOverlay {
    private static final int LOCKED = 0xC0101010;
    public static final int DIVIDER = 0xFF6B4A2B; // тёмно-коричневая черта между областями
    private static final int DIVIDER_WIDTH = 2;   // обе рамки между соседними ячейками

    private StorageSlotOverlay() {}

    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (screen instanceof ArmorInventoryScreen) {
            return; // наше окно рисует всё само
        }
        GuiGraphics graphics = event.getGuiGraphics();
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof StorageSlot storage && storage.isActive() && !storage.isOpen()) {
                int x = screen.getGuiLeft() + slot.x;
                int y = screen.getGuiTop() + slot.y;
                graphics.fill(x, y, x + 16, y + 16, LOCKED);
            }
        }
        drawDividers(graphics, screen, screen.getGuiLeft(), screen.getGuiTop());
    }

    /**
     * Черта слева от каждой области, кроме первой: по рамке между ячейками, на всю высоту сетки.
     * x, y — левый верхний угол окна. Зовёт и наш инвентарь.
     */
    public static void drawDividers(GuiGraphics graphics, AbstractContainerScreen<?> screen, int x, int y) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof StorageSlot storage && storage.isActive() && storage.area() != StorageArea.BACK
                    && storage.local() % storage.area().columns() == 0) {
                int left = x + slot.x - 2;
                graphics.fill(left, y + slot.y - 1, left + DIVIDER_WIDTH, y + slot.y + 17, DIVIDER);
            }
        }
    }

    // Подсказка над пустой ячейкой сумки
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || screen instanceof ArmorInventoryScreen) {
            return;
        }
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered instanceof StorageSlot storage && !storage.hasItem() && screen.getMenu().getCarried().isEmpty()) {
            event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, storage.hint(),
                    event.getMouseX(), event.getMouseY());
        }
    }
}
