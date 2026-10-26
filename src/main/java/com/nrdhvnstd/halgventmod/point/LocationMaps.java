package com.nrdhvnstd.halgventmod.point;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.map.ColumnSampler;
import com.nrdhvnstd.halgventmod.map.LocationMapData;
import com.nrdhvnstd.halgventmod.network.LocationCaptureProgressPayload;
import com.nrdhvnstd.halgventmod.network.LocationMapPartPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;

/**
 * Снимки карт локаций. Администратор снимает локацию (кнопка технической карты или /point capture <id>):
 * сервер обходит чанки прямоугольника вокруг её границы — только уже сгенерированные, ничего не генерируя, —
 * не больше CAPTURE_CHUNKS_PER_TICK за тик, и записывает столбцы внутри границы в файл
 * <мир>/data/halgventmod/location_maps/<id>.bin (LocationMapData). Версия снимка у точки растёт, точки рассылаются,
 * и клиенты сами просят новый файл (LocationMapRequestPayload). Файл получает только тот, у кого локация открыта,
 * или администратор: неоткрытую локацию из файлов клиента не вытащить. Файл идёт кусками по PART_BYTES,
 * не больше PARTS_PER_TICK кусков на игрока за тик.
 * Одновременно идёт один снимок на сервер.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class LocationMaps {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "location_maps";
    private static final int MAX_SIZE = 2048;               // ⚠️ сторона прямоугольника локации в блоках
    private static final int CAPTURE_CHUNKS_PER_TICK = 8;   // ⚠️
    private static final int PART_BYTES = 256 * 1024;       // ⚠️
    private static final int PARTS_PER_TICK = 2;            // ⚠️
    private static final int PROGRESS_INTERVAL = 20;        // ход снимка — раз в секунду

    @Nullable
    private static Capture capture;
    /** Снимок дописывается на диск в фоне — новый пока не начать. */
    private static boolean writing;
    private static final Map<UUID, ArrayDeque<LocationMapPartPayload>> OUTGOING = new HashMap<>();

    private LocationMaps() {}

    public static Path file(MinecraftServer server, int id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(HalgventMod.MODID).resolve(FOLDER)
                .resolve(id + ".bin");
    }

    // ===== Снимок =====

    /** Начать снимок локации; сообщения — источнику команды (администратору или консоли). */
    public static void start(CommandSourceStack source, int id) {
        MinecraftServer server = source.getServer();
        MapPoint point = MapPoints.get(server).find(id);
        if (point == null || point.isSpawn()) {
            fail(source, Component.translatable("message.halgventmod.location_map.no_point", id));
            return;
        }
        if (capture != null || writing) {
            String name = capture != null ? capture.point.name : "";
            fail(source, Component.translatable("message.halgventmod.location_map.busy", name));
            return;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(point.dimension);
        ServerLevel level = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            fail(source, Component.translatable("message.halgventmod.location_map.no_dimension", point.dimension));
            return;
        }
        int[] bounds = point.bounds();
        if (bounds[2] - bounds[0] + 1 > MAX_SIZE || bounds[3] - bounds[1] + 1 > MAX_SIZE) {
            fail(source, Component.translatable("message.halgventmod.location_map.too_big", MAX_SIZE));
            return;
        }
        capture = new Capture(source, point.copy(), level, bounds);
        LOGGER.info("{} снимает карту локации «{}» #{}: {} чанков", source.getTextName(), point.name, point.id,
                capture.chunks.size());
        message(source, Component.translatable("message.halgventmod.location_map.started", point.name));
    }

    /** Снимается ли сейчас эта точка. */
    public static boolean capturing(int id) {
        return capture != null && capture.point.id == id;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (capture != null) {
            capture.tick();
        }
        // Куски файлов снимков — каждому понемногу
        Iterator<Map.Entry<UUID, ArrayDeque<LocationMapPartPayload>>> it = OUTGOING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ArrayDeque<LocationMapPartPayload>> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            for (int i = 0; i < PARTS_PER_TICK && !entry.getValue().isEmpty(); i++) {
                PacketDistributor.sendToPlayer(player, entry.getValue().poll());
            }
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OUTGOING.remove(event.getEntity().getUUID());
    }

    /** Один снимок: чанки по порядку; не загруженные читаются с диска в фоне игры и берутся, когда прочитаны. */
    private static final class Capture {
        final CommandSourceStack source;
        final MapPoint point;
        final ServerLevel level;
        final LocationMapData data;
        final List<ChunkPos> chunks = new ArrayList<>();
        final Map<Long, CompletableFuture<Optional<CompoundTag>>> reading = new LinkedHashMap<>();
        final Map<BlockState, Short> blockIndex = new HashMap<>();
        final Map<ResourceLocation, Short> biomeIndex = new HashMap<>();
        final ColumnSampler sampler = new ColumnSampler();
        int next;
        int done;
        int ticks;

        Capture(CommandSourceStack source, MapPoint point, ServerLevel level, int[] bounds) {
            this.source = source;
            this.point = point;
            this.level = level;
            // Прямоугольник — по границам чанков: так клетки биомов 4 × 4 совпадают с клетками карты клиента
            int minChunkX = bounds[0] >> 4;
            int minChunkZ = bounds[1] >> 4;
            int maxChunkX = bounds[2] >> 4;
            int maxChunkZ = bounds[3] >> 4;
            this.data = new LocationMapData(point.id, 0, minChunkX << 4, minChunkZ << 4,
                    (maxChunkX - minChunkX + 1) << 4, (maxChunkZ - minChunkZ + 1) << 4);
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
        }

        void tick() {
            int budget = CAPTURE_CHUNKS_PER_TICK;
            // Прочитанные с диска: сгенерированный целиком — загрузить (без генерации) и взять, иначе «неизвестно»
            Iterator<Map.Entry<Long, CompletableFuture<Optional<CompoundTag>>>> it = reading.entrySet().iterator();
            while (it.hasNext() && budget > 0) {
                Map.Entry<Long, CompletableFuture<Optional<CompoundTag>>> entry = it.next();
                if (!entry.getValue().isDone()) {
                    continue;
                }
                it.remove();
                Optional<CompoundTag> tag;
                try {
                    tag = entry.getValue().join();
                } catch (RuntimeException e) {
                    tag = Optional.empty();
                }
                if (tag.isPresent() && ChunkSerializer.getChunkTypeFromTag(tag.get()) == ChunkType.LEVELCHUNK) {
                    sampleChunk(level.getChunk(ChunkPos.getX(entry.getKey()), ChunkPos.getZ(entry.getKey())));
                    budget--;
                }
                done++;
            }
            while (budget > 0 && next < chunks.size() && reading.size() < CAPTURE_CHUNKS_PER_TICK) {
                ChunkPos pos = chunks.get(next++);
                LevelChunk loaded = level.getChunkSource().getChunkNow(pos.x, pos.z);
                if (loaded != null) {
                    sampleChunk(loaded);
                    budget--;
                    done++;
                } else {
                    reading.put(pos.toLong(), level.getChunkSource().chunkMap.read(pos));
                }
            }
            if (++ticks % PROGRESS_INTERVAL == 0) {
                progress(done * 100 / chunks.size());
            }
            if (done >= chunks.size()) {
                finish();
            }
        }

        private void sampleChunk(LevelChunk chunk) {
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int worldX = baseX + x;
                    int worldZ = baseZ + z;
                    // Вне границы — «неизвестно»: так на диске и в сети нет ничего за локацией
                    if (!point.contains(worldX + 0.5, worldZ + 0.5) || !sampler.sample(chunk, worldX, worldZ)) {
                        continue;
                    }
                    int index = (worldZ - data.minZ) * data.width + (worldX - data.minX);
                    data.blocks[index] = data.blockIndex(sampler.state, blockIndex);
                    data.heights[index] = (short) sampler.height;
                    data.depths[index] = (byte) sampler.depth;
                    if (sampler.depth > 0 && sampler.floor != null) {
                        data.floors[index] = data.blockIndex(sampler.floor, blockIndex);
                    }
                    // Биом клетки 4 × 4 — по её середине, как у клиента
                    if ((x & 3) == 2 && (z & 3) == 2) {
                        ResourceLocation biome = sampler.biome(worldX, worldZ);
                        if (biome != null) {
                            int cell = ((worldZ - data.minZ) >> 2) * data.cellsX() + ((worldX - data.minX) >> 2);
                            data.biomes[cell] = data.biomeIndex(biome, biomeIndex);
                        }
                    }
                }
            }
        }

        private void progress(int percent) {
            if (source.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new LocationCaptureProgressPayload(point.id, percent));
            }
        }

        // Всё прочитано: файл пишется в фоне, потом — новая версия у точки и сообщение
        private void finish() {
            capture = null;
            writing = true;
            MinecraftServer server = level.getServer();
            MapPoint current = MapPoints.get(server).find(point.id);
            int version = (current != null ? current.mapVersion : point.mapVersion) + 1;
            LocationMapData result = new LocationMapData(data, version);
            Path file = file(server, point.id);
            Util.ioPool().execute(() -> {
                boolean ok;
                try {
                    result.write(file);
                    ok = true;
                } catch (IOException e) {
                    LOGGER.error("Не удалось записать снимок карты локации {}", file, e);
                    ok = false;
                }
                boolean written = ok;
                server.execute(() -> {
                    writing = false;
                    progress(100);
                    if (!written) {
                        fail(source, Component.translatable("message.halgventmod.location_map.write_failed", point.name));
                        return;
                    }
                    MapPoints.setMapVersion(server, point.id, version);
                    LOGGER.info("Снимок карты локации «{}» #{} готов, версия {}", point.name, point.id, version);
                    message(source, Component.translatable("message.halgventmod.location_map.done", point.name));
                });
            });
        }
    }

    // ===== Раздача =====

    /** Клиент просит снимок (у него версия clientVersion). Неоткрытая и не администратору — молчим. */
    public static void request(ServerPlayer player, int id, int clientVersion) {
        MapPoint point = MapPoints.get(player.server).find(id);
        if (point == null || point.isSpawn() || point.mapVersion <= clientVersion) {
            return;
        }
        if (!Permissions.isAdmin(player) && !player.getData(ModAttachments.POINT_DISCOVERY).has(id)) {
            return;
        }
        ArrayDeque<LocationMapPartPayload> queue = OUTGOING.computeIfAbsent(player.getUUID(), uuid -> new ArrayDeque<>());
        if (queue.stream().anyMatch(part -> part.id() == id)) {
            return; // уже отправляется
        }
        Path file = file(player.server, id);
        int[] header = Files.isRegularFile(file) ? LocationMapData.readHeader(file) : null;
        if (header == null) {
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            LOGGER.warn("Не удалось прочитать снимок карты локации {}", file, e);
            return;
        }
        int total = Math.max(1, (bytes.length + PART_BYTES - 1) / PART_BYTES);
        for (int part = 0; part < total; part++) {
            int from = part * PART_BYTES;
            int to = Math.min(bytes.length, from + PART_BYTES);
            byte[] piece = new byte[to - from];
            System.arraycopy(bytes, from, piece, 0, piece.length);
            queue.add(new LocationMapPartPayload(id, header[1], part, total, piece));
        }
    }

    // ===== Сообщения =====

    private static void message(CommandSourceStack source, Component text) {
        source.sendSuccess(() -> withPrefix(text), false);
    }

    private static void fail(CommandSourceStack source, Component text) {
        source.sendFailure(withPrefix(text));
    }

    // «[Карта] …»: приставка своим цветом, текст — цветом сообщения
    private static MutableComponent withPrefix(Component text) {
        return Component.empty()
                .append(Component.translatable("message.halgventmod.location_map.prefix").withStyle(ChatFormatting.GOLD))
                .append(" ").append(text);
    }
}
