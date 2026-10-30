package com.nrdhvnstd.halgventmod.combat;

import java.util.function.IntFunction;

import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.FaceCover;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Часть тела, куда пришёлся удар (ТЗ боя, 6.3; подробные части — по образцу Kingdom Come Combat). Определяется по
 * точке попадания в хитбоксе цели: высота, сторона от середины (руки), перед или зад (лицо). Каждая часть —
 * множитель урона, доля, которую закрывает броня своей зоны (шея и лицо закрыты хуже, суставы — щели), и зона брони
 * (голова, корпус, ноги), слои которой гасят удар (combat/DamagePipeline).
 */
public enum HitZone {
    //        урон ⚠️  покрытие бронёй ⚠️  зона брони
    CROWN    (1.5F,  1.0F,  ArmorSlot.BodyPart.HEAD),   // темя и затылок
    FACE     (1.7F,  0.25F, ArmorSlot.BodyPart.HEAD),   // лицо: открыто, кроме забрала и глухого шлема
    NECK     (1.8F,  0.55F, ArmorSlot.BodyPart.HEAD),   // шея: под шлемом щель
    CHEST    (1.0F,  1.0F,  ArmorSlot.BodyPart.TORSO),
    ABDOMEN  (1.1F,  0.95F, ArmorSlot.BodyPart.TORSO),
    ARM      (0.75F, 0.85F, ArmorSlot.BodyPart.TORSO),  // плечо и предплечье: сочленения
    HAND     (0.5F,  0.7F,  ArmorSlot.BodyPart.TORSO),  // кисть: латная перчатка гнётся
    THIGH    (0.85F, 0.95F, ArmorSlot.BodyPart.LEGS),
    KNEE     (0.9F,  0.8F,  ArmorSlot.BodyPart.LEGS),   // колено: под наколенником щель
    SHIN     (0.7F,  1.0F,  ArmorSlot.BodyPart.LEGS),
    FOOT     (0.5F,  0.9F,  ArmorSlot.BodyPart.LEGS);

    // Меняйте здесь ⚠️ (доли высоты хитбокса цели, от ступней)
    public static final double HEAD_BELOW_EYES = 0.25;  // голова — выше «глаза − 0.25»
    public static final double NECK_BELOW_EYES = 0.17;  // шея — нижняя полоса головы
    public static final double CROWN_ABOVE_EYES = 0.08; // темя — выше «глаза + 0.08»
    public static final double LEGS_SHARE = 0.45;       // ноги — ниже 45 % роста
    public static final double KNEE_FROM = 0.24, KNEE_TO = 0.33;
    public static final double FOOT_BELOW = 0.08;
    public static final double SHIN_BELOW = 0.24;
    public static final double CHEST_ABOVE = 0.66;      // грудь — выше 66 % роста, ниже — живот
    public static final double ARM_SIDE = 0.2;          // дальше от середины — рука
    public static final double HAND_BELOW = 0.6;        // рука ниже 60 % роста — кисть

    private static final IntFunction<HitZone> BY_ID =
            ByIdMap.continuous(HitZone::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, HitZone> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, HitZone::ordinal);

    private final float damage;
    private final float coverage;
    private final ArmorSlot.BodyPart bodyPart;

    HitZone(float damage, float coverage, ArmorSlot.BodyPart bodyPart) {
        this.damage = damage;
        this.coverage = coverage;
        this.bodyPart = bodyPart;
    }

    public float damage() {
        return damage;
    }

    /** Какую долю этой части закрывает броня её зоны (множитель покрытия слоя). У лица — по шлему цели. */
    public float coverage(LivingEntity target) {
        return this == FACE && FaceCover.armored(target) ? 0.95F : coverage;
    }

    public ArmorSlot.BodyPart bodyPart() {
        return bodyPart;
    }

    /** Ключ перевода: hit_zone.halgventmod.neck */
    public String langKey() {
        return "hit_zone.halgventmod." + name().toLowerCase();
    }

    public static HitZone of(LivingEntity target, Vec3 point) {
        double bottom = target.getBoundingBox().minY;
        double height = Math.max(0.1, target.getBbHeight());
        double share = (point.y - bottom) / height;
        double eye = target.getEyeY();
        // Сторона и перед — от того, куда смотрит тело цели
        float yaw = target.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 offset = point.subtract(target.position());
        double forward = -offset.x * Mth.sin(yaw) + offset.z * Mth.cos(yaw);
        double side = offset.x * Mth.cos(yaw) + offset.z * Mth.sin(yaw);
        if (point.y > eye - HEAD_BELOW_EYES) {
            if (point.y < eye - NECK_BELOW_EYES) {
                return NECK;
            }
            return point.y < eye + CROWN_ABOVE_EYES && forward > 0 ? FACE : CROWN;
        }
        if (share < LEGS_SHARE) {
            if (share < FOOT_BELOW) {
                return FOOT;
            }
            if (share < SHIN_BELOW) {
                return SHIN;
            }
            return share >= KNEE_FROM && share < KNEE_TO ? KNEE : THIGH;
        }
        if (Math.abs(side) > ARM_SIDE * target.getBbWidth() / 0.6) {
            return share < HAND_BELOW ? HAND : ARM;
        }
        return share > CHEST_ABOVE ? CHEST : ABDOMEN;
    }
}
