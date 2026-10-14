package com.nrdhvnstd.halgventmod.armor;

import javax.annotation.Nullable;

import net.minecraft.world.item.DyeColor;

/**
 * Цвета эмали. Подобраны под окраску «перекрытием» (Overlay), поэтому глуше ванильных красителей.
 * Цвет кладётся в тот же компонент dyed_color, что у ткани. Лаймового и розового нет.
 */
public enum EnamelColor {
    RED(DyeColor.RED, 0x734040),               // красный
    GREEN(DyeColor.GREEN, 0x668553),           // зелёный
    BLUE(DyeColor.BLUE, 0x637097),             // синий
    PURPLE(DyeColor.PURPLE, 0x9F8BAA),         // фиолетовый
    MAGENTA(DyeColor.MAGENTA, 0x8C6586),       // пурпурный
    ORANGE(DyeColor.ORANGE, 0xA0835A),         // оранжевый
    YELLOW(DyeColor.YELLOW, 0xB0A76A),         // жёлтый
    CYAN(DyeColor.CYAN, 0x8DAEAA),             // бирюзовый
    WHITE(DyeColor.WHITE, 0xE6E6E6),           // белый
    GRAY(DyeColor.GRAY, 0x959595),             // серый
    LIGHT_GRAY(DyeColor.LIGHT_GRAY, 0xB9B9B9), // светло-серый
    BLACK(DyeColor.BLACK, 0x636363),           // чёрный
    BROWN(DyeColor.BROWN, 0x7E736C),           // коричневый
    LIGHT_BLUE(DyeColor.LIGHT_BLUE, 0xA1B1C7); // голубой

    private final DyeColor dye;
    private final int rgb;

    EnamelColor(DyeColor dye, int rgb) {
        this.dye = dye;
        this.rgb = rgb;
    }

    public DyeColor dye() {
        return dye;
    }

    /** Цвет эмали, RGB. */
    public int rgb() {
        return rgb;
    }

    /** Эмаль ванильного красителя или null, если такой эмали нет. */
    @Nullable
    public static EnamelColor of(DyeColor dye) {
        for (EnamelColor color : values()) {
            if (color.dye == dye) {
                return color;
            }
        }
        return null;
    }
}
