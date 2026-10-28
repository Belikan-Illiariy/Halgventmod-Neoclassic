package com.nrdhvnstd.halgventmod.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Траектория клинка (ТЗ боя, 6.1): где отрезок клинка в доле удара t ∈ [0, 1]. Удар (взмах, удар сверху) идёт
 * с выбранной стороны (StrikeSide — по движению мыши), укол — прямо. Углы — от текущего взгляда бойца, поэтому поворот
 * камеры во время удара сам двигает клинок (ускорение и затягивание). Общий код сервера и клиента: по нему же идут
 * анимации (client/CombatAnimation) — что видно, то и бьёт.
 *
 * Клинок — отрезок от хвата (плечо + рука) до острия; кроме него трасса проверяет и руку от плеча до хвата, чтобы
 * вплотную стоящий тоже получал удар.
 */
public final class SwingPath {
    // Меняйте здесь ⚠️
    public static final double ARM = 0.6;             // от плеча до хвата
    public static final double CHEST_DROP = 0.4;      // удар сбоку — на высоте груди: глаза − 0.4
    public static final double SHOULDER_DROP = 0.15;  // удар сверху — от плеча
    public static final double HIP_DROP = 0.75;       // удар снизу — от бедра
    public static final double SHOULDER_SIDE = 0.3;   // плечо сбоку от середины
    public static final double STAB_DROP = 0.3;       // укол — чуть ниже глаз
    public static final float SIDE_FROM = 75;         // удар сбоку: от 75° со своей стороны до 75° с другой
    public static final float OVERHEAD_FROM = 70, OVERHEAD_TO = -30; // сверху: от 70° над взглядом до 30° под ним
    public static final float RISING_FROM = -55, RISING_TO = 40;     // снизу: от 55° под взглядом до 40° над ним
    public static final double STAB_START = 0.6;      // остриё укола выходит с 0.6 полной длины

    private SwingPath() {}

    /** Положение руки и клинка: плечо, хват, остриё. */
    public record Blade(Vec3 shoulder, Vec3 hilt, Vec3 tip) {
        /** Точка на руке и клинке: 0 — плечо, 1 — остриё. */
        public Vec3 at(double share) {
            return shoulder.lerp(tip, share);
        }
    }

    /** Угол удара сбоку в доле пути: «+» — слева от взгляда, «−» — справа. */
    public static float sideAngle(StrikeSide side, float t) {
        return side == StrikeSide.LEFT ? Mth.lerp(t, SIDE_FROM, -SIDE_FROM) : Mth.lerp(t, -SIDE_FROM, SIDE_FROM);
    }

    /** Угол удара сверху или снизу в доле пути: «+» — над взглядом. */
    public static float verticalAngle(StrikeSide side, float t) {
        return side == StrikeSide.DOWN ? Mth.lerp(t, RISING_FROM, RISING_TO) : Mth.lerp(t, OVERHEAD_FROM, OVERHEAD_TO);
    }

    /**
     * Клинок бойца в доле удара t. shape — укол или удар (STAB или нет), side — сторона удара, length — длина клинка
     * (с прибавкой особого удара и разбега).
     */
    public static Blade blade(LivingEntity entity, AttackKind shape, StrikeSide side, float t, double length) {
        float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = entity.getViewVector(1.0F);
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)); // по рысканию: при взгляде вверх-вниз не вырождается
        Vec3 up = right.cross(forward).normalize();
        Vec3 eye = entity.getEyePosition();
        float progress = Mth.clamp(t, 0, 1);
        if (shape == AttackKind.STAB) {
            Vec3 shoulder = eye.subtract(0, STAB_DROP, 0);
            double reach = (ARM + length) * Mth.lerp(progress, STAB_START, 1.0);
            Vec3 tip = shoulder.add(forward.scale(reach));
            return new Blade(shoulder, tip.subtract(forward.scale(length)), tip);
        }
        Vec3 shoulder;
        Vec3 dir;
        switch (side) {
            case UP, DOWN -> {
                // Сверху — от правого плеча, снизу — от правого бедра
                shoulder = eye.add(right.scale(SHOULDER_SIDE)).subtract(0, side == StrikeSide.UP ? SHOULDER_DROP : HIP_DROP, 0);
                float angle = verticalAngle(side, progress) * Mth.DEG_TO_RAD;
                dir = forward.scale(Mth.cos(angle)).add(up.scale(Mth.sin(angle)));
            }
            default -> {
                shoulder = eye.subtract(0, CHEST_DROP, 0);
                float angle = sideAngle(side, progress) * Mth.DEG_TO_RAD;
                dir = forward.scale(Mth.cos(angle)).subtract(right.scale(Mth.sin(angle)));
            }
        }
        return new Blade(shoulder, shoulder.add(dir.scale(ARM)), shoulder.add(dir.scale(ARM + length)));
    }
}
