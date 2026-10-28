package com.nrdhvnstd.halgventmod.combat;

import javax.annotation.Nullable;

/**
 * Класс оружия (ТЗ боя, 11.2): у каждого свой профиль — data/halgventmod/stats/weapons/&lt;id&gt;.json.
 * Луки, арбалеты и дротики боем ближнего боя не управляются.
 */
public enum WeaponClass {
    KNIFE("knife"), DAGGER("dagger"), SWORD("sword"), AXE("axe"), MACE("mace"), SPEAR("spear"),
    GREATSWORD("greatsword"), GREATAXE("greataxe"), GREATHAMMER("greathammer"), POLEARM("polearm"),
    SHIELD("shield"), FIST("fist");

    private final String id;

    WeaponClass(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Nullable
    public static WeaponClass byId(String id) {
        for (WeaponClass weaponClass : values()) {
            if (weaponClass.id.equals(id)) {
                return weaponClass;
            }
        }
        return null;
    }
}
