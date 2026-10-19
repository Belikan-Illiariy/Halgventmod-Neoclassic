package com.nrdhvnstd.halgventmod.storage;

import com.nrdhvnstd.halgventmod.gear.GearType;

/**
 * Вещи, внутри которых лежат другие вещи. Содержимое хранится в самой вещи (BagContents).
 * Рюкзак и сумка носятся на спине, подсумок и колчан — на поясе.
 */
public enum ContainerType {
    //         куда надевается   наибольшая вместимость
    BACKPACK  (GearType.BACK,    15),
    BAG       (GearType.BACK,    15),
    POUCH     (GearType.POUCH,   6),
    QUIVER    (GearType.QUIVER,  1);   // одна стопка стрел, до Quiver.MAX_ARROWS

    private final GearType gear;
    private final int maxCapacity;

    ContainerType(GearType gear, int maxCapacity) {
        this.gear = gear;
        this.maxCapacity = maxCapacity;
    }

    /** Куда надевается. */
    public GearType gear() {
        return gear;
    }

    public int maxCapacity() {
        return maxCapacity;
    }
}
