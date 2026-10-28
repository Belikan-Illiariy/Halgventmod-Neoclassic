package com.nrdhvnstd.halgventmod.combat;

import net.minecraft.world.entity.LivingEntity;

/**
 * Бонусы боевых сегментов гобелена (ТЗ боя, 13). Сегментов в коде ещё нет — всё 0; когда появятся,
 * считать здесь по уровню сегмента класса оружия.
 */
public final class CombatBonuses {
    private CombatBonuses() {}

    /** Доля, на которую быстрее взмах (0.05 = на 5 % короче фазы). */
    public static float swingSpeed(LivingEntity entity, WeaponClass weaponClass) {
        return 0;
    }

    /** Доля прибавки к урону. */
    public static float damage(LivingEntity entity, WeaponClass weaponClass) {
        return 0;
    }
}
