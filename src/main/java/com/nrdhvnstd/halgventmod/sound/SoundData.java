package com.nrdhvnstd.halgventmod.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.SoundDataPayload;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Наборы музыки и сцены звуков — данные датапака. Сервер читает их при запуске и /reload и шлёт
 * клиентам одним пакетом (при входе и после /reload, OnDatapackSyncEvent). Во время игры сервер ничего не делает.
 * Ошибки в файлах — предупреждение в лог, набор, сцена или слой пропускаются.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class SoundData {
    public static final String MUSIC_DIR = "halgvent_music";
    public static final String SCENE_DIR = "halgvent_sound_scene";
    private static final Gson GSON = new Gson();

    // Сервер: по id файла, по порядку id
    private static Map<ResourceLocation, MusicSet> serverSets = Map.of();
    private static Map<ResourceLocation, SoundScene> serverScenes = Map.of();

    // Клиент: что прислал сервер. version растёт с каждым пакетом — проигрыватели по ней видят новые данные
    public static Map<ResourceLocation, MusicSet> clientSets = Map.of();
    public static Map<ResourceLocation, SoundScene> clientScenes = Map.of();
    public static int clientVersion;

    private SoundData() {}

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MusicLoader(event.getRegistryAccess()));
        event.addListener(new SceneLoader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SoundDataPayload payload = new SoundDataPayload(serverSets, serverScenes);
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    /** Пакет с сервера пришёл (клиент). */
    public static void receive(Map<ResourceLocation, MusicSet> sets, Map<ResourceLocation, SoundScene> scenes) {
        clientSets = Collections.unmodifiableMap(new TreeMap<>(sets));
        clientScenes = Collections.unmodifiableMap(new TreeMap<>(scenes));
        clientVersion++;
    }

    // ===== Наборы музыки =====

    private static final class MusicLoader extends SimpleJsonResourceReloadListener {
        private final RegistryAccess registries;

        MusicLoader(RegistryAccess registries) {
            super(GSON, MUSIC_DIR);
            this.registries = registries;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Optional<Registry<Biome>> biomes = registries.registry(Registries.BIOME);
            Map<ResourceLocation, MusicSet> sets = new TreeMap<>();
            files.forEach((id, json) -> {
                MusicSet set = MusicSet.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> HalgventMod.LOGGER.warn("Набор музыки {}: {}", id, error))
                        .orElse(null);
                if (set == null) {
                    return;
                }
                List<String> valid = new ArrayList<>();
                for (String biome : set.biomes()) {
                    boolean tag = biome.startsWith("#");
                    ResourceLocation biomeId = ResourceLocation.tryParse(tag ? biome.substring(1) : biome);
                    if (biomeId == null) {
                        HalgventMod.LOGGER.warn("Набор музыки {}: неверный id биома «{}»", id, biome);
                    } else if (!tag && biomes.isPresent() && !biomes.get().containsKey(biomeId)) {
                        HalgventMod.LOGGER.warn("Набор музыки {}: неизвестный биом {}", id, biomeId);
                    } else {
                        valid.add(tag ? "#" + biomeId : biomeId.toString());
                    }
                }
                if (valid.isEmpty() || set.tracks().isEmpty()) {
                    HalgventMod.LOGGER.warn("Набор музыки {}: нет биомов или треков, набор пропущен", id);
                    return;
                }
                sets.put(id, set.withBiomes(valid));
            });
            serverSets = Collections.unmodifiableMap(sets);
            HalgventMod.LOGGER.info("Наборов музыки: {}", sets.size());
        }
    }

    // ===== Сцены звуков мест =====

    private static final class SceneLoader extends SimpleJsonResourceReloadListener {
        SceneLoader() {
            super(GSON, SCENE_DIR);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, SoundScene> scenes = new TreeMap<>();
            files.forEach((id, json) -> {
                if (!json.isJsonObject()) {
                    HalgventMod.LOGGER.warn("Сцена звуков {}: ожидался объект", id);
                    return;
                }
                // Слои разбираются по одному, чтобы ошибка в слое не выкидывала всю сцену
                JsonObject object = json.getAsJsonObject().deepCopy();
                JsonElement layersJson = object.remove("layers");
                SoundScene scene = SoundScene.CODEC.parse(JsonOps.INSTANCE, object)
                        .resultOrPartial(error -> HalgventMod.LOGGER.warn("Сцена звуков {}: {}", id, error))
                        .orElse(null);
                if (scene == null) {
                    return;
                }
                List<SoundScene.Layer> layers = new ArrayList<>();
                if (layersJson != null && layersJson.isJsonArray()) {
                    JsonArray array = layersJson.getAsJsonArray();
                    for (int i = 0; i < array.size(); i++) {
                        SoundScene.Layer layer = layer(id, i, array.get(i));
                        if (layer != null) {
                            layers.add(layer);
                        }
                    }
                }
                if (layers.isEmpty()) {
                    HalgventMod.LOGGER.warn("Сцена звуков {}: нет ни одного слоя, сцена пропущена", id);
                    return;
                }
                scenes.put(id, scene.withLayers(layers));
            });
            serverScenes = Collections.unmodifiableMap(scenes);
            HalgventMod.LOGGER.info("Сцен звуков: {}", scenes.size());
        }

        private static SoundScene.Layer layer(ResourceLocation id, int index, JsonElement json) {
            if (!json.isJsonObject()) {
                HalgventMod.LOGGER.warn("Сцена звуков {}, слой {}: ожидался объект", id, index + 1);
                return null;
            }
            JsonObject object = json.getAsJsonObject().deepCopy();
            if (object.has("sound") && !object.has("sounds")) {
                JsonArray sounds = new JsonArray();
                sounds.add(object.remove("sound"));
                object.add("sounds", sounds);
            }
            SoundScene.Layer layer = SoundScene.Layer.CODEC.parse(JsonOps.INSTANCE, object)
                    .resultOrPartial(error -> HalgventMod.LOGGER.warn("Сцена звуков {}, слой {}: {}", id, index + 1, error))
                    .orElse(null);
            if (layer == null) {
                return null;
            }
            if (!List.of(SoundScene.LOOP, SoundScene.INTERVAL, SoundScene.PLAYLIST).contains(layer.type())) {
                HalgventMod.LOGGER.warn("Сцена звуков {}, слой {}: неизвестный тип «{}»", id, index + 1, layer.type());
                return null;
            }
            if (layer.sounds().isEmpty()) {
                HalgventMod.LOGGER.warn("Сцена звуков {}, слой {}: нет звуков", id, index + 1);
                return null;
            }
            return layer;
        }
    }
}
