package com.nrdhvnstd.halgventmod.client.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Разовый звук из точки мира (стук копыт): с направлением, тише с расстоянием.
 * Слышимость берётся из sounds.json (attenuation_distance, по умолчанию 16) — здесь она не меньше радиуса сцены.
 */
public final class PointSound extends AbstractSoundInstance {
    private final int range;

    public PointSound(ResourceLocation id, double x, double y, double z, float volume, int range) {
        super(id, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = volume;
        this.range = range;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents events = super.resolve(manager);
        if (events != null && sound != SoundManager.EMPTY_SOUND && sound.getAttenuationDistance() < range) {
            sound = new Sound(sound.getLocation(), sound.getVolume(), sound.getPitch(), sound.getWeight(),
                    sound.getType(), sound.shouldStream(), sound.shouldPreload(), range);
        }
        return events;
    }
}
