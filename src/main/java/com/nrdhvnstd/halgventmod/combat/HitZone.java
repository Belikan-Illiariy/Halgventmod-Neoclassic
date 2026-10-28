package com.nrdhvnstd.halgventmod.combat;

import java.util.function.IntFunction;

import com.nrdhvnstd.halgventmod.armor.ArmorSlot;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Зона попадания (ТЗ боя, 6.3) — по высоте точки в хитбоксе цели: голова выше «глаза − 0.25», ноги ниже 45 % высоты,
 * между ними корпус и руки. Зона задаёт множитель урона и слоты брони, которые закрывают удар (этап 7).
 */
public enum HitZone {
    //      урон ⚠️  часть тела
    HEAD   (1.5F,  ArmorSlot.BodyPart.HEAD),
    TORSO  (1.0F,  ArmorSlot.BodyPart.TORSO),
    LEGS   (0.8F,  ArmorSlot.BodyPart.LEGS);

    public static final double HEAD_BELOW_EYES = 0.25;
    public static final double LEGS_SHARE = 0.45;

    private static final IntFunction<HitZone> BY_ID =
            ByIdMap.continuous(HitZone::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, HitZone> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, HitZone::ordinal);

    private final float damage;
    private final ArmorSlot.BodyPart bodyPart;

    HitZone(float damage, ArmorSlot.BodyPart bodyPart) {
        this.damage = damage;
        this.bodyPart = bodyPart;
    }

    public float damage() {
        return damage;
    }

    public ArmorSlot.BodyPart bodyPart() {
        return bodyPart;
    }

    /** Ключ перевода: hit_zone.halgventmod.head */
    public String langKey() {
        return "hit_zone.halgventmod." + name().toLowerCase();
    }

    public static HitZone of(LivingEntity target, Vec3 point) {
        if (point.y > target.getEyeY() - HEAD_BELOW_EYES) {
            return HEAD;
        }
        double bottom = target.getBoundingBox().minY;
        return point.y < bottom + target.getBbHeight() * LEGS_SHARE ? LEGS : TORSO;
    }
}
