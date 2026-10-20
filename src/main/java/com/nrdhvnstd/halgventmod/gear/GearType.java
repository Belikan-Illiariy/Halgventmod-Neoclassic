package com.nrdhvnstd.halgventmod.gear;

import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.sound.SoundPlay;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

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
    POUCH;  // подсумок на поясе

    public static final float EQUIP_VOLUME = 0.7F;        // ⚠️
    public static final float EQUIP_PITCH_SPREAD = 0.08F; // ⚠️

    /** Звук надевания и снятия. Украшениям и фонарю своих звуков нет — пока ванильный кожаный. */
    public SoundEvent equipSound() {
        return switch (this) {
            case BELT -> ModSounds.GEAR_EQUIP_BELT.get();
            case QUIVER -> ModSounds.GEAR_EQUIP_QUIVER.get();
            case BACK -> ModSounds.GEAR_EQUIP_BACKPACK.get();
            case POUCH -> ModSounds.GEAR_EQUIP_POUCH.get();
            case RING, NECK, BRACELET, LANTERN -> SoundEvents.ARMOR_EQUIP_LEATHER.value();
        };
    }

    /** Надел или снял: звук у того, кто носит. Только на сервере. */
    public void playEquip(Entity wearer) {
        if (!wearer.level().isClientSide) {
            SoundPlay.at(wearer, equipSound(), SoundSource.PLAYERS, EQUIP_VOLUME, EQUIP_PITCH_SPREAD);
        }
    }
}
