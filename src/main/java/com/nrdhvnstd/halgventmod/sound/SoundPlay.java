package com.nrdhvnstd.halgventmod.sound;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Проиграть звук на сервере, чтобы слышали все рядом; тон со случайным разбросом ±spread. */
public final class SoundPlay {
    public static final float CHEST = 0.7F; // высота груди — доля роста существа

    private SoundPlay() {}

    public static float pitch(RandomSource random, float spread) {
        return 1.0F + (random.nextFloat() * 2.0F - 1.0F) * spread;
    }

    /** У ног существа. except — кто не услышит (он проиграл звук у себя сам), обычно null. */
    public static void at(Entity entity, @Nullable Player except, SoundEvent sound, SoundSource source, float volume, float spread) {
        entity.level().playSound(except, entity.getX(), entity.getY(), entity.getZ(), sound, source,
                volume, pitch(entity.getRandom(), spread));
    }

    public static void at(Entity entity, SoundEvent sound, SoundSource source, float volume, float spread) {
        at(entity, null, sound, source, volume, spread);
    }

    /** На высоте груди: удары, блок щитом. */
    public static void atChest(Entity entity, SoundEvent sound, SoundSource source, float volume, float spread) {
        entity.level().playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * CHEST, entity.getZ(),
                sound, source, volume, pitch(entity.getRandom(), spread));
    }

    /** Незарегистрированный звук (голос): Holder.direct уходит клиентам по id. */
    public static void at(Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float spread) {
        entity.level().playSeededSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, source,
                volume, pitch(entity.getRandom(), spread), entity.getRandom().nextLong());
    }
}
