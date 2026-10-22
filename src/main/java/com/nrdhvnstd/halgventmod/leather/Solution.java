package com.nrdhvnstd.halgventmod.leather;

/**
 * Что налито в чан. Цвет — для отрисовки жидкости (0xRRGGBB).
 * WATER — просто вода: над огнём выпаривается в соль, варит мездру в клей и дублёную кожу в варёную.
 */
public enum Solution {
    NONE(0x000000),
    WATER(0x3f76e4),
    LYE(0x9a9a8e),   // зольный щёлок: золка, волос слезает
    BARK(0x6b3a1e),  // корьевой раствор: дублёная кожа
    ALUM(0xd8d8cf),  // квасцы с солью: мягкая кожа
    FAT(0xc9a646);   // жир: выделка меха

    private final int color;

    Solution(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public String langKey() {
        return "solution.halgventmod." + name().toLowerCase();
    }
}
