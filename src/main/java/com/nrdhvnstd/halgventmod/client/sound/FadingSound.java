package com.nrdhvnstd.halgventmod.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Звук без направления (вокруг игрока) с изменяемой громкостью: трек музыки, гул места.
 * Громкость = volume × fade × gain: fade плавно идёт к цели (затухание и нарастание), gain задаёт владелец
 * каждый тик (расстояние, приглушение). Затух до нуля — останавливается сам.
 */
public final class FadingSound extends AbstractTickableSoundInstance {
    private final float baseVolume;
    private float fade;
    private float fadeTarget = 1;
    private float fadeStep = 1;
    private float gain = 1;

    public FadingSound(ResourceLocation id, SoundSource source, boolean loop, float baseVolume, int fadeInTicks) {
        super(SoundEvent.createVariableRangeEvent(id), source, SoundInstance.createUnseededRandom());
        this.baseVolume = baseVolume;
        this.looping = loop;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        if (fadeInTicks > 0) {
            fade = 0;
            fadeStep = 1.0F / fadeInTicks;
        } else {
            fade = 1;
        }
        updateVolume();
    }

    public void setGain(float gain) {
        this.gain = gain;
    }

    /** Плавно затухнуть за ticks и остановиться. */
    public void fadeOut(int ticks) {
        fadeTarget = 0;
        fadeStep = ticks > 0 ? 1.0F / ticks : 1;
    }

    /** Вернуться к полной громкости за ticks (после fadeOut, если ещё не затих). */
    public void fadeIn(int ticks) {
        fadeTarget = 1;
        fadeStep = ticks > 0 ? 1.0F / ticks : 1;
    }

    public boolean fadingOut() {
        return fadeTarget == 0;
    }

    @Override
    public void tick() {
        if (fade < fadeTarget) {
            fade = Math.min(fadeTarget, fade + fadeStep);
        } else if (fade > fadeTarget) {
            fade = Math.max(fadeTarget, fade - fadeStep);
        }
        if (fadeTarget == 0 && fade <= 0) {
            stop();
            return;
        }
        updateVolume();
    }

    private void updateVolume() {
        volume = Math.max(0, baseVolume * fade * gain);
    }

    // Звук на краю радиуса или в начале нарастания — с нулевой громкостью, но должен начаться
    @Override
    public boolean canStartSilent() {
        return true;
    }
}
