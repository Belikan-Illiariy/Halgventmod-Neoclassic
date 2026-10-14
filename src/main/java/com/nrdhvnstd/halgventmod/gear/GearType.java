package com.nrdhvnstd.halgventmod.gear;

/**
 * Вид надеваемого снаряжения: в какую ячейку снаряжения встаёт вещь.
 * Подсумок подходит в обе ячейки подсумков, остальные — в свою единственную.
 */
public enum GearType {
    RING,
    NECK,
    BRACELET,
    LANTERN,
    BELT,
    QUIVER,
    BACK,   // рюкзак или сумка
    POUCH   // подсумок на поясе
}
