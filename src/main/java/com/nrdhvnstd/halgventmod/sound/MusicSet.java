package com.nrdhvnstd.halgventmod.sound;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * Набор музыки: какие биомы (id или #тег), какие треки по порядку, пауза между ними, приоритет.
 * Файл data/<ns>/halgvent_music/<имя>.json; набор меню — assets/halgventmod/halgvent_music/menu.json (без biomes и priority).
 */
public record MusicSet(List<String> biomes, List<ResourceLocation> tracks, float pauseSeconds, int priority) {
    public static final float DEFAULT_PAUSE_SECONDS = 5; // Р5 ⚠️

    public static final Codec<MusicSet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(MusicSet::biomes),
            ResourceLocation.CODEC.listOf().fieldOf("tracks").forGetter(MusicSet::tracks),
            Codec.FLOAT.optionalFieldOf("pause_seconds", DEFAULT_PAUSE_SECONDS).forGetter(MusicSet::pauseSeconds),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(MusicSet::priority)
    ).apply(instance, MusicSet::new));

    public MusicSet {
        biomes = List.copyOf(biomes);
        tracks = List.copyOf(tracks);
    }

    public int pauseTicks() {
        return Math.max(0, Math.round(pauseSeconds * 20));
    }

    public MusicSet withBiomes(List<String> newBiomes) {
        return new MusicSet(newBiomes, tracks, pauseSeconds, priority);
    }
}
