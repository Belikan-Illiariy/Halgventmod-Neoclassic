package com.nrdhvnstd.halgventmod.storage;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.gear.GearType;

/**
 * Вещи, внутри которых лежат другие вещи. Содержимое хранится в самой вещи (BagContents).
 * Рюкзак и сумка носятся на спине, подсумок и колчан — на поясе, мешочек не надевается.
 */
public enum ContainerType {
    //         куда надевается   наибольшая вместимость
    BACKPACK  (GearType.BACK,    15),
    BAG       (GearType.BACK,    15),
    POUCH     (GearType.POUCH,   6),
    QUIVER    (GearType.QUIVER,  1),   // одна стопка стрел, до Quiver.MAX_ARROWS
    PURSE     (null,             4);

    @Nullable
    private final GearType gear;
    private final int maxCapacity;

    ContainerType(@Nullable GearType gear, int maxCapacity) {
        this.gear = gear;
        this.maxCapacity = maxCapacity;
    }

    /** Куда надевается; null — не надевается (мешочек). */
    @Nullable
    public GearType gear() {
        return gear;
    }

    public int maxCapacity() {
        return maxCapacity;
    }

    /** Надеваемый контейнер: с вещами внутри его нельзя положить в другой контейнер. */
    public boolean wearable() {
        return gear != null;
    }
}
