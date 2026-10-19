package com.nrdhvnstd.halgventmod.client.sound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.sound.SoundData;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlockEntity;
import com.nrdhvnstd.halgventmod.sound.SoundScene;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Звуки мест (ТЗ, часть 2) — целиком на клиенте. Один менеджер со списком загруженных источников
 * (SoundEmitterBlockEntity сообщает о себе через SoundClient), без тикеров у блоков и перебора мира.
 * Раз в SCAN_INTERVAL тиков: у каждой сцены — ближайший источник в радиусе (З8), звучат не больше MAX_SCENES ближайших
 * сцен (потоковые каналы). Слои: loop — гул вокруг игрока, interval — разовый звук из точки, playlist — музыка места.
 * Громкость каждый тик плавно по расстоянию (З2). mute_music приглушает музыку биома (BiomeMusic.setMute).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class SoundEmitters {
    private static final int SCAN_INTERVAL = 5;          // ⚠️
    private static final int MAX_SCENES = 4;             // одновременно звучащих сцен ⚠️
    private static final int FADE_TICKS = 20;            // вход в радиус и выход — ~1 с ⚠️
    private static final int MARKER_INTERVAL = 40;       // значок источника для администрации
    private static final int MARKER_RANGE = 32;

    private static final Set<SoundEmitterBlockEntity> EMITTERS = new HashSet<>();
    private static final Map<ResourceLocation, Voice> VOICES = new HashMap<>();
    @Nullable
    private static ClientLevel lastLevel;
    private static int scanTimer;
    private static int markerTimer;

    private SoundEmitters() {}

    static void add(SoundEmitterBlockEntity emitter) {
        EMITTERS.add(emitter);
    }

    static void remove(SoundEmitterBlockEntity emitter) {
        EMITTERS.remove(emitter);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level != lastLevel || player == null) {
            stopAll(); // выход из мира, смена измерения
            lastLevel = level;
        }
        if (level == null || player == null) {
            EMITTERS.clear();
            return;
        }
        if (--scanTimer <= 0) {
            scanTimer = SCAN_INTERVAL;
            scan(level, player);
        }
        Iterator<Voice> voices = VOICES.values().iterator();
        while (voices.hasNext()) {
            Voice voice = voices.next();
            voice.tick(player);
            if (voice.finished()) {
                voices.remove();
            }
        }
        if (--markerTimer <= 0) {
            markerTimer = MARKER_INTERVAL;
            markers(level, player);
        }
    }

    // Ближайший источник каждой сцены в радиусе; звучат MAX_SCENES ближайших сцен
    private static void scan(ClientLevel level, LocalPlayer player) {
        Map<ResourceLocation, Candidate> nearest = new HashMap<>();
        float mute = 1;
        Iterator<SoundEmitterBlockEntity> iterator = EMITTERS.iterator();
        while (iterator.hasNext()) {
            SoundEmitterBlockEntity emitter = iterator.next();
            if (emitter.isRemoved() || emitter.getLevel() != level) {
                iterator.remove();
                continue;
            }
            ResourceLocation sceneId = emitter.sceneId();
            SoundScene scene = sceneId != null ? SoundData.clientScenes.get(sceneId) : null;
            if (scene == null) {
                continue;
            }
            int radius = emitter.radius() > 0 ? emitter.radius() : scene.radius();
            double distance = emitter.getBlockPos().getCenter().distanceToSqr(player.position());
            if (distance >= (double) radius * radius) {
                continue;
            }
            if (scene.muteMusic()) {
                mute = Math.min(mute, (float) Math.sqrt(distance) / radius);
            }
            Candidate old = nearest.get(sceneId);
            if (old == null || distance < old.distance) {
                nearest.put(sceneId, new Candidate(sceneId, scene, emitter, radius, distance));
            }
        }
        BiomeMusic.setMute(mute);

        List<Candidate> chosen = new ArrayList<>(nearest.values());
        chosen.sort(Comparator.comparingDouble(Candidate::distance));
        Set<ResourceLocation> playing = new HashSet<>();
        for (Candidate candidate : chosen.subList(0, Math.min(MAX_SCENES, chosen.size()))) {
            playing.add(candidate.id);
            Voice voice = VOICES.get(candidate.id);
            if (voice != null && !voice.scene.equals(candidate.scene)) {
                voice.release(); // /reload поменял сцену — старая затихает, новая начинается
                VOICES.put(retiredKey(candidate.id), voice);
                voice = null;
            }
            if (voice == null) {
                voice = new Voice(candidate.id, candidate.scene);
                VOICES.put(candidate.id, voice);
            }
            voice.follow(candidate.emitter, candidate.radius);
        }
        for (Map.Entry<ResourceLocation, Voice> entry : VOICES.entrySet()) {
            if (!playing.contains(entry.getKey())) {
                entry.getValue().release();
            }
        }
    }

    private static int retired;

    // Затихающая старая сцена с тем же id — под своим ключом, пока не затихнет
    private static ResourceLocation retiredKey(ResourceLocation id) {
        return id.withSuffix("/retired_" + retired++);
    }

    private static void stopAll() {
        for (Voice voice : VOICES.values()) {
            voice.stopNow();
        }
        VOICES.clear();
        BiomeMusic.setMute(1);
    }

    // Администрация с предметом источника в руке видит значки источников рядом, как у блока света
    private static void markers(ClientLevel level, LocalPlayer player) {
        if (!player.isHolding(ModItems.SOUND_EMITTER.get())) {
            return;
        }
        for (SoundEmitterBlockEntity emitter : EMITTERS) {
            BlockPos pos = emitter.getBlockPos();
            if (emitter.getLevel() == level && pos.closerToCenterThan(player.position(), MARKER_RANGE)) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, emitter.getBlockState()),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0);
            }
        }
    }

    private record Candidate(ResourceLocation id, SoundScene scene, SoundEmitterBlockEntity emitter, int radius,
                             double distance) {}

    /** Звучащая сцена: один инстанс на слой, пока игрок в радиусе (звук не пересоздаётся). */
    private static final class Voice {
        final SoundScene scene;
        private final List<LayerVoice> layers = new ArrayList<>();
        @Nullable
        private SoundEmitterBlockEntity emitter;
        private int radius;
        private boolean released;

        Voice(ResourceLocation id, SoundScene scene) {
            this.scene = scene;
            for (int i = 0; i < scene.layers().size(); i++) {
                layers.add(new LayerVoice(id + "/" + i, scene.layers().get(i)));
            }
        }

        void follow(SoundEmitterBlockEntity newEmitter, int newRadius) {
            emitter = newEmitter;
            radius = newRadius;
            if (released) {
                released = false;
                layers.forEach(LayerVoice::resume);
            }
        }

        void release() {
            if (!released) {
                released = true;
                layers.forEach(LayerVoice::release);
            }
        }

        void tick(LocalPlayer player) {
            float gain = 0;
            if (!released && emitter != null) {
                if (emitter.isRemoved()) {
                    release(); // сломали
                } else {
                    float distance = (float) Math.sqrt(emitter.getBlockPos().getCenter().distanceToSqr(player.position()));
                    float full = scene.fullRadiusFor(radius);
                    gain = distance <= full ? 1 : Mth.clamp((radius - distance) / Math.max(0.01F, radius - full), 0, 1);
                }
            }
            for (LayerVoice layer : layers) {
                layer.tick(gain, released ? null : emitter, radius);
            }
        }

        boolean finished() {
            return released && layers.stream().allMatch(LayerVoice::silent);
        }

        void stopNow() {
            layers.forEach(LayerVoice::stopNow);
        }
    }

    /** Слой сцены: loop, interval или playlist. */
    private static final class LayerVoice {
        private static final RandomSource RANDOM = RandomSource.create();

        private final String key;
        private final SoundScene.Layer layer;
        @Nullable
        private FadingSound loop;
        @Nullable
        private Playlist playlist;
        private int timer;
        private int last = -1;

        LayerVoice(String key, SoundScene.Layer layer) {
            this.key = key;
            this.layer = layer;
            resume();
        }

        void resume() {
            timer = nextInterval(); // первый разовый звук — через интервал после входа в радиус
            if (layer.type().equals(SoundScene.LOOP)) {
                if (loop != null && Minecraft.getInstance().getSoundManager().isActive(loop)) {
                    loop.fadeIn(FADE_TICKS);
                } else {
                    loop = null;
                }
            } else if (layer.type().equals(SoundScene.PLAYLIST)) {
                if (playlist != null) {
                    playlist.release(FADE_TICKS);
                }
                // вернулся — набор с первого трека
                playlist = new Playlist(key, layer.sounds(), Math.round(layer.pauseSeconds() * 20), SoundSource.MUSIC,
                        layer.volume(), FADE_TICKS, 0);
            }
        }

        void release() {
            if (loop != null) {
                loop.fadeOut(FADE_TICKS);
            }
            if (playlist != null) {
                playlist.release(FADE_TICKS);
                playlist = null;
            }
        }

        void tick(float gain, @Nullable SoundEmitterBlockEntity emitter, int radius) {
            switch (layer.type()) {
                case SoundScene.LOOP -> {
                    if (loop == null && emitter != null) {
                        loop = new FadingSound(layer.sounds().getFirst(), SoundSource.AMBIENT, true, layer.volume(), FADE_TICKS);
                        loop.setGain(gain);
                        Minecraft.getInstance().getSoundManager().play(loop);
                    } else if (loop != null) {
                        loop.setGain(gain);
                    }
                }
                case SoundScene.INTERVAL -> {
                    if (emitter != null && --timer <= 0) {
                        timer = nextInterval();
                        playOnce(emitter, radius);
                    }
                }
                case SoundScene.PLAYLIST -> {
                    if (playlist != null) {
                        playlist.tick(gain);
                    }
                }
                default -> {}
            }
        }

        // Случайный вариант, но не тот же, что в прошлый раз (З5), из случайной точки в пределах offset (З3)
        private void playOnce(SoundEmitterBlockEntity emitter, int radius) {
            List<ResourceLocation> sounds = layer.sounds();
            int index = RANDOM.nextInt(sounds.size());
            if (sounds.size() > 1 && index == last) {
                index = (index + 1 + RANDOM.nextInt(sounds.size() - 1)) % sounds.size();
            }
            last = index;
            BlockPos pos = emitter.getBlockPos();
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double shift = Math.sqrt(RANDOM.nextDouble()) * layer.offset();
            Minecraft.getInstance().getSoundManager().play(new PointSound(sounds.get(index),
                    pos.getX() + 0.5 + Math.cos(angle) * shift, pos.getY() + 0.5, pos.getZ() + 0.5 + Math.sin(angle) * shift,
                    layer.volume(), radius));
        }

        private int nextInterval() {
            float spread = layer.intervalSpread() > 0 ? (RANDOM.nextFloat() * 2 - 1) * layer.intervalSpread() : 0;
            return Math.max(1, Math.round((layer.intervalSeconds() + spread) * 20));
        }

        boolean silent() {
            return (loop == null || !Minecraft.getInstance().getSoundManager().isActive(loop)) && playlist == null;
        }

        void stopNow() {
            if (loop != null) {
                Minecraft.getInstance().getSoundManager().stop(loop);
                loop = null;
            }
            if (playlist != null) {
                playlist.stopNow();
                playlist = null;
            }
        }
    }
}
