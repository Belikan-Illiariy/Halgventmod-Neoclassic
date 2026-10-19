package com.nrdhvnstd.halgventmod.client.sound;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.sound.MusicSet;
import com.nrdhvnstd.halgventmod.sound.SoundData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Своя музыка вместо ванильной (ТЗ «Музыка биомов», часть 1). Ванильная выключена везде (SelectMusicEvent),
 * пластинки не тронуты. Вне мира — набор меню из ресурсов; в мире — набор биома под игроком (SoundData.clientSets):
 * треки по порядку и по кругу, смена набора — затухание (Р6), на границе — задержка (Р7), общий набор не прерывается (Р9).
 * Источники звуков с mute_music приглушают музыку биома (setMute). Команды: /halgventmusic info, skip.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class BiomeMusic {
    private static final int FADE_TICKS = 60;            // Р6: затухание 3 с ⚠️
    private static final int BORDER_DELAY_TICKS = 80;    // Р7: в новом биоме 4 с ⚠️
    private static final int CHECK_INTERVAL = 10;        // биом под игроком — раз в 10 тиков ⚠️
    private static final float MUTE_STEP = 0.05F;        // приглушение меняется плавно, за 1 с от 0 до 1
    private static final String MENU_KEY = "menu";
    private static final ResourceLocation MENU_FILE =
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, SoundData.MUSIC_DIR + "/menu.json");

    @Nullable
    private static MusicSet menuSet;
    @Nullable
    private static Playlist active;
    @Nullable
    private static String pendingKey;
    private static int pendingTicks;
    private static int checkTimer;
    private static int seenVersion = -1;
    @Nullable
    private static ResourceLocation worldSetId;          // набор под игроком по последней проверке
    private static final Map<ResourceKey<Biome>, Optional<ResourceLocation>> CACHE = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static float muteTarget = 1;
    private static float mute = 1;
    private static boolean wasInWorld;

    private BiomeMusic() {}

    // Ванильной музыки нет нигде: ни в меню, ни в мире, ни в титрах
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        event.setMusic(null);
    }

    /** Множитель громкости музыки биома от источников с mute_music (1 — без приглушения). */
    public static void setMute(float factor) {
        muteTarget = factor;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean inWorld = minecraft.level != null && player != null;

        String wantKey;
        List<ResourceLocation> tracks = List.of();
        int pause = 0;
        boolean immediate;                               // без задержки на границе
        if (!inWorld) {
            worldSetId = null;
            muteTarget = 1;
            wantKey = menuSet != null ? MENU_KEY : null;
            if (menuSet != null) {
                tracks = menuSet.tracks();
                pause = menuSet.pauseTicks();
            }
            immediate = true;
        } else {
            // Только что вошли в мир, играет меню или пришли новые данные — набор сразу
            boolean fresh = seenVersion != SoundData.clientVersion;
            if (fresh) {
                seenVersion = SoundData.clientVersion;
                CACHE.clear();
            }
            immediate = fresh || !wasInWorld || isMenu();
            if (--checkTimer <= 0 || immediate) {
                checkTimer = CHECK_INTERVAL;
                worldSetId = find(minecraft.level.getBiome(player.blockPosition()));
            }
            MusicSet set = worldSetId != null ? SoundData.clientSets.get(worldSetId) : null;
            wantKey = set != null ? worldSetId.toString() : null;
            if (set != null) {
                tracks = set.tracks();
                pause = set.pauseTicks();
            }
        }
        wasInWorld = inWorld;

        String activeKey = active != null ? active.key : null;
        if (Objects.equals(wantKey, activeKey)) {
            pendingKey = null;
            if (active != null) {
                active.update(tracks, pause); // /reload: тот же набор с новыми треками не прерывается
            }
        } else if (immediate) {
            switchTo(wantKey, tracks, pause);
        } else {
            if (!Objects.equals(wantKey, pendingKey)) {
                pendingKey = wantKey;
                pendingTicks = 0;
            }
            if (++pendingTicks >= BORDER_DELAY_TICKS) {
                switchTo(wantKey, tracks, pause);
            }
        }

        mute += Math.clamp(muteTarget - mute, -MUTE_STEP, MUTE_STEP);
        if (active != null) {
            active.tick(isMenu() ? 1 : mute);
        }
    }

    private static boolean isMenu() {
        return active != null && MENU_KEY.equals(active.key);
    }

    private static void switchTo(@Nullable String key, List<ResourceLocation> tracks, int pause) {
        pendingKey = null;
        FadingSound playing = active != null ? active.current() : null;
        ResourceLocation playingTrack = active != null ? active.currentTrack() : null;
        boolean alive = playing != null && Minecraft.getInstance().getSoundManager().isActive(playing);
        if (key == null) {
            if (active != null) {
                active.release(FADE_TICKS);
            }
            active = null;
            return;
        }
        Playlist next = new Playlist(key, tracks, pause, SoundSource.MUSIC, 1.0F, 0, alive ? FADE_TICKS : 0);
        int index = playingTrack != null ? tracks.indexOf(playingTrack) : -1;
        if (alive && index >= 0 && !playing.fadingOut()) {
            next.adopt(playing, index); // этот трек есть и в новом наборе — не прерываем
        } else if (active != null) {
            active.release(FADE_TICKS);
        }
        active = next;
    }

    /** Набор для биома: больший priority; при равном — где биом указан прямо, а не тегом; дальше — первый по id. */
    @Nullable
    private static ResourceLocation find(Holder<Biome> biome) {
        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        if (key.isEmpty()) {
            return null;
        }
        return CACHE.computeIfAbsent(key.get(), k -> Optional.ofNullable(choose(biome, k))).orElse(null);
    }

    @Nullable
    private static ResourceLocation choose(Holder<Biome> biome, ResourceKey<Biome> key) {
        String direct = key.location().toString();
        ResourceLocation best = null;
        int bestPriority = 0;
        boolean bestDirect = false;
        for (Map.Entry<ResourceLocation, MusicSet> entry : SoundData.clientSets.entrySet()) {
            MusicSet set = entry.getValue();
            boolean isDirect = set.biomes().contains(direct);
            boolean byTag = !isDirect && set.biomes().stream().anyMatch(b -> b.startsWith("#")
                    && biome.is(TagKey.create(Registries.BIOME, ResourceLocation.parse(b.substring(1)))));
            if (!isDirect && !byTag) {
                continue;
            }
            if (best == null || set.priority() > bestPriority || set.priority() == bestPriority && isDirect && !bestDirect) {
                best = entry.getKey();
                bestPriority = set.priority();
                bestDirect = isDirect;
            } else if (set.priority() == bestPriority && isDirect == bestDirect
                    && WARNED.add(direct + " " + best + " " + entry.getKey())) {
                HalgventMod.LOGGER.warn("Биом {} есть в наборах {} и {} с равным приоритетом — играет {}",
                        direct, best, entry.getKey(), best);
            }
        }
        return best;
    }

    // Теги биомов пришли заново — выбор наборов пересчитать
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        CACHE.clear();
        checkTimer = 0;
    }

    // ===== Набор меню: assets/halgventmod/halgvent_music/menu.json, можно заменить ресурспаком =====

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            menuSet = null;
            Optional<Resource> resource = manager.getResource(MENU_FILE);
            if (resource.isEmpty()) {
                HalgventMod.LOGGER.warn("Нет набора музыки меню {} — в меню тишина", MENU_FILE);
                return;
            }
            try (Reader reader = resource.get().openAsReader()) {
                menuSet = MusicSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .resultOrPartial(error -> HalgventMod.LOGGER.warn("Набор музыки меню: {}", error))
                        .filter(set -> !set.tracks().isEmpty())
                        .orElse(null);
            } catch (Exception e) {
                HalgventMod.LOGGER.warn("Набор музыки меню не прочитан: {}", e.toString());
            }
            if (menuSet == null) {
                HalgventMod.LOGGER.warn("Набор музыки меню пуст — в меню тишина");
            }
        });
    }

    // ===== Команды =====

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("halgventmusic")
                .then(Commands.literal("info").executes(context -> info()))
                .then(Commands.literal("skip").executes(context -> skip())));
    }

    private static int info() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return 0;
        }
        Holder<Biome> biome = minecraft.level.getBiome(player.blockPosition());
        String biomeId = biome.unwrapKey().map(key -> key.location().toString()).orElse("?");
        tell(Component.translatable("command.halgventmod.music.biome", biomeId));
        String tags = biome.tags().map(tag -> "#" + tag.location()).sorted().collect(Collectors.joining(", "));
        tell(tags.isEmpty() ? Component.translatable("command.halgventmod.music.no_tags")
                : Component.translatable("command.halgventmod.music.tags", tags));
        ResourceLocation set = find(biome);
        tell(set == null ? Component.translatable("command.halgventmod.music.no_set")
                : Component.translatable("command.halgventmod.music.set", set.toString()));
        if (active != null && !isMenu()) {
            if (!active.key.equals(String.valueOf(set))) {
                tell(Component.translatable("command.halgventmod.music.playing_set", active.key));
            }
            ResourceLocation track = active.currentTrack();
            tell(track != null && active.currentNumber() > 0
                    ? Component.translatable("command.halgventmod.music.track", track.toString(),
                            active.currentNumber(), active.size())
                    : Component.translatable("command.halgventmod.music.pause"));
        }
        if (mute < 0.99F) {
            tell(Component.translatable("command.halgventmod.music.muted", Math.round(mute * 100)));
        }
        return 1;
    }

    private static int skip() {
        if (active == null) {
            tell(Component.translatable("command.halgventmod.music.nothing"));
            return 0;
        }
        active.skip();
        tell(Component.translatable("command.halgventmod.music.skipped"));
        return 1;
    }

    private static void tell(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, false);
        }
    }
}
