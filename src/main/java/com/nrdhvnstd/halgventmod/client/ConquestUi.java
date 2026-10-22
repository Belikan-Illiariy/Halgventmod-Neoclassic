package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Окна инвентаря в стиле Conquest Reforged: железная плашка (character/panel — из social_interactions),
 * мшистые ячейки с железной рамкой (inventory/slot — ячейка сундука generic_54), тёмные вставки (character/cell).
 */
public final class ConquestUi {
    private static final ResourceLocation PANEL = sprite("character/panel");
    private static final ResourceLocation INSET = sprite("character/cell");
    private static final ResourceLocation SLOT = sprite("inventory/slot");
    private static final ResourceLocation SLOT_LOCKED = sprite("inventory/slot_locked");

    private ConquestUi() {}

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, path);
    }

    /** Окно целиком. */
    public static void window(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.blitSprite(PANEL, left, top, right - left, bottom - top);
    }

    /** Тёмная утопленная вставка: под фигурку, панель предмета. */
    public static void inset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.blitSprite(INSET, left, top, right - left, bottom - top);
    }

    /** Ячейка; x, y — место предмета (рамка на 1 больше с каждой стороны). Закрытая — тёмная. */
    public static void slot(GuiGraphics graphics, int x, int y, boolean locked) {
        graphics.blitSprite(locked ? SLOT_LOCKED : SLOT, x - 1, y - 1, 18, 18);
    }
}
