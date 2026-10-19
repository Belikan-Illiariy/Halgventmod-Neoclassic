package com.nrdhvnstd.halgventmod.sound;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * Сцена звуков места (рынок, кузница, таверна): радиус, до какого расстояния громкость полная,
 * глушить ли музыку биома, слои. Файл data/<ns>/halgvent_sound_scene/<имя>.json. Источник ссылается на сцену по id.
 */
public record SoundScene(int radius, Optional<Float> fullRadius, boolean muteMusic, List<Layer> layers) {
    public static final int DEFAULT_RADIUS = 16;
    public static final int MAX_RADIUS = 128;

    public static final String LOOP = "loop";
    public static final String INTERVAL = "interval";
    public static final String PLAYLIST = "playlist";

    public static final Codec<SoundScene> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, MAX_RADIUS).optionalFieldOf("radius", DEFAULT_RADIUS).forGetter(SoundScene::radius),
            Codec.FLOAT.optionalFieldOf("full_radius").forGetter(SoundScene::fullRadius),
            Codec.BOOL.optionalFieldOf("mute_music", false).forGetter(SoundScene::muteMusic),
            Layer.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(SoundScene::layers)
    ).apply(instance, SoundScene::new));

    public SoundScene {
        layers = List.copyOf(layers);
    }

    public SoundScene withLayers(List<Layer> newLayers) {
        return new SoundScene(radius, fullRadius, muteMusic, newLayers);
    }

    /** До какого расстояния громкость полная при радиусе источника radius (З2): та же доля, что в сцене. */
    public float fullRadiusFor(int sourceRadius) {
        float full = Math.clamp(fullRadius.orElse(radius / 2.0F), 0, radius);
        return full / radius * sourceRadius;
    }

    /**
     * Слой: loop — непрерывный гул вокруг игрока; interval — разовый звук из точки рядом с источником;
     * playlist — своя музыка места по порядку. В файле у loop можно писать "sound" вместо "sounds".
     */
    public record Layer(String type, List<ResourceLocation> sounds, float volume, float intervalSeconds,
                        float intervalSpread, float offset, float pauseSeconds) {
        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(Layer::type),
                ResourceLocation.CODEC.listOf().fieldOf("sounds").forGetter(Layer::sounds),
                Codec.floatRange(0, 1).optionalFieldOf("volume", 1.0F).forGetter(Layer::volume),
                Codec.floatRange(0.05F, 3600).optionalFieldOf("interval_seconds", 5.0F).forGetter(Layer::intervalSeconds),
                Codec.floatRange(0, 3600).optionalFieldOf("interval_spread", 0.0F).forGetter(Layer::intervalSpread),
                Codec.floatRange(0, MAX_RADIUS).optionalFieldOf("offset", 0.0F).forGetter(Layer::offset),
                Codec.floatRange(0, 3600).optionalFieldOf("pause_seconds", MusicSet.DEFAULT_PAUSE_SECONDS).forGetter(Layer::pauseSeconds)
        ).apply(instance, Layer::new));

        public Layer {
            sounds = List.copyOf(sounds);
        }
    }
}
