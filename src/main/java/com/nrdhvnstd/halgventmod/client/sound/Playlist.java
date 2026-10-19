package com.nrdhvnstd.halgventmod.client.sound;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Треки по порядку и по кругу (Р3, Р4) с паузой между ними: музыка биома, меню, слой playlist сцены.
 * Трек закончился (SoundManager.isActive — нет) — пауза, затем следующий; после последнего — первый.
 */
public final class Playlist {
    final String key;                 // что играет: id набора, "menu" или id сцены + слой
    private List<ResourceLocation> tracks;
    private int pauseTicks;
    private final SoundSource source;
    private final float volume;
    private final int fadeInTicks;

    private int next;                 // номер следующего трека
    private int wait;                 // тиков до следующего трека
    private int playingIndex = -1;
    @Nullable
    private FadingSound current;

    public Playlist(String key, List<ResourceLocation> tracks, int pauseTicks, SoundSource source, float volume,
                    int fadeInTicks, int initialWait) {
        this.key = key;
        this.tracks = tracks;
        this.pauseTicks = pauseTicks;
        this.source = source;
        this.volume = volume;
        this.fadeInTicks = fadeInTicks;
        this.wait = initialWait;
    }

    /** Подхватить уже играющий трек (новый набор, где он тоже есть, — не прерывать). */
    public void adopt(FadingSound sound, int index) {
        current = sound;
        playingIndex = index;
        next = (index + 1) % tracks.size();
    }

    /** Новые данные того же набора: треки и пауза, номер не сбрасывается. */
    public void update(List<ResourceLocation> newTracks, int newPause) {
        if (!newTracks.equals(tracks)) {
            tracks = newTracks;
            next %= tracks.size();
        }
        pauseTicks = newPause;
    }

    public void tick(float gain) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (current != null) {
            current.setGain(gain);
            if (!sounds.isActive(current)) {
                current = null;
                playingIndex = -1;
                wait = pauseTicks;
            }
        }
        if (current == null && !tracks.isEmpty()) {
            if (wait > 0) {
                wait--;
                return;
            }
            playingIndex = next;
            next = (next + 1) % tracks.size();
            current = new FadingSound(tracks.get(playingIndex), source, false, volume, fadeInTicks);
            current.setGain(gain);
            sounds.play(current);
        }
    }

    /** Следующий трек сразу (/halgventmusic skip). */
    public void skip() {
        if (current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            current = null;
        }
        playingIndex = -1;
        wait = 0;
    }

    /** Отпустить: текущий трек затухает сам, плейлист больше не нужен. */
    public void release(int fadeTicks) {
        if (current != null) {
            current.fadeOut(fadeTicks);
            current = null;
        }
    }

    public void stopNow() {
        if (current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            current = null;
        }
    }

    @Nullable
    public FadingSound current() {
        return current;
    }

    @Nullable
    public ResourceLocation currentTrack() {
        return playingIndex >= 0 && playingIndex < tracks.size() ? tracks.get(playingIndex) : null;
    }

    /** Номер играющего трека с 1; 0 — пауза. */
    public int currentNumber() {
        return current != null ? playingIndex + 1 : 0;
    }

    public int size() {
        return tracks.size();
    }

    public List<ResourceLocation> tracks() {
        return tracks;
    }
}
