package com.nrdhvnstd.halgventmod.client.map;

import java.util.Locale;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/**
 * Значки меток из атласа textures/gui/map_icons.png: клетки 32 × 32, по 16 в ряд.
 * Флаг — знаменосец 16 цветов красителей (клетки 10–25, в порядке DyeColor).
 * Отдельно — курсор игрока (клетка 5, остриём вниз).
 */
public enum MapIcon {
    FLAG(-1),
    CASTLE(32), TOWER(33), HOUSE(34), VILLAGE(35), RUINS(36), TEMPLE(37), MILL(38),
    MINE(39), CAVE(40), CAMP(41), PORT(42), TREASURE(43), GRAVE(44), PORTAL(45),
    KNIGHT(0), RIDER(1), CARAVAN(2), GUARD(3),
    MARK(4), GATHERING(6), GOAL(7), BATTLE(26), DRAGON(8), BEAST(9);

    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID,
            "textures/gui/map_icons.png");
    public static final int CELL = 32;
    private static final int ATLAS_SIZE = 512;
    private static final int PER_ROW = ATLAS_SIZE / CELL;
    private static final int FIRST_FLAG = 10;
    /** Курсор игрока: остриём вниз, то есть на юг. */
    public static final int CURSOR = 5;

    private final int cell;

    MapIcon(int cell) {
        this.cell = cell;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("gui.halgventmod.map.icon." + id());
    }

    /** Клетка атласа; у флага — по цвету. */
    public int cell(DyeColor dye) {
        return this == FLAG ? FIRST_FLAG + dye.getId() : cell;
    }

    /** По имени из файла меток; неизвестное — флаг. */
    public static MapIcon byId(String id) {
        if (id != null) {
            for (MapIcon icon : values()) {
                if (icon.id().equals(id)) {
                    return icon;
                }
            }
        }
        return FLAG;
    }

    /** Нарисовать клетку атласа квадратом size × size с левым верхним углом в x, y. */
    public static void draw(GuiGraphics graphics, int cell, int x, int y, int size) {
        graphics.blit(ATLAS, x, y, size, size, (float) (cell % PER_ROW * CELL), (float) (cell / PER_ROW * CELL),
                CELL, CELL, ATLAS_SIZE, ATLAS_SIZE);
    }

    /** Координаты клетки в атласе от 0 до 1: u0, v0, u1, v1. */
    public static float[] uv(int cell) {
        float u = (float) (cell % PER_ROW * CELL) / ATLAS_SIZE;
        float v = (float) (cell / PER_ROW * CELL) / ATLAS_SIZE;
        float size = (float) CELL / ATLAS_SIZE;
        return new float[] {u, v, u + size, v + size};
    }
}
