package com.nrdhvnstd.halgventmod.draw;

import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Как долго предмет достаётся в руку и как это звучит (концепт инвентаря, ТЗ 5.3).
 * Время и звуки временные ⚠️ — подберём на проверке.
 */
public enum DrawType {
    //       тиков   звук
    SMALL   (6,      () -> SoundEvents.BUNDLE_INSERT),              // мелочь: еда, ключи, монеты, свитки — шорох ткани
    TOOL    (12,     () -> SoundEvents.ARMOR_EQUIP_LEATHER.value()), // инструмент, одноручное оружие — кожа, ножны
    HEAVY   (20,     () -> SoundEvents.ARMOR_EQUIP_CHAIN.value());   // тяжёлое, двуручное, щит, блоки — металл, ремни

    private final int ticks;
    private final Supplier<SoundEvent> sound;

    DrawType(int ticks, Supplier<SoundEvent> sound) {
        this.ticks = ticks;
        this.sound = sound;
    }

    /** Сколько тиков от смены предмета до готовности. */
    public int ticks() {
        return ticks;
    }

    public SoundEvent sound() {
        return sound.get();
    }
}
