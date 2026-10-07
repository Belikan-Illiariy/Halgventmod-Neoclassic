package com.nrdhvnstd.halgventmod.lock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.LocksChunkPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Копия замков на клиенте: чтобы клиент не «приоткрывал» запертую дверь,
 * а потом — чтобы рисовать замки на дверях и сундуках.
 * Сервер присылает замки чанка, когда игрок начинает его видеть, и после каждого изменения.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class LockSync {
    // Только на клиенте: номер чанка → замки в нём
    private static final Map<Long, Map<BlockPos, LockData>> CLIENT = new HashMap<>();

    private LockSync() {}

    @Nullable
    public static LockData clientGet(BlockPos pos) {
        Map<BlockPos, LockData> chunk = CLIENT.get(ChunkPos.asLong(pos));
        return chunk == null ? null : chunk.get(pos);
    }

    /** Все замки в загруженных чанках — для отрисовки. */
    public static Map<Long, Map<BlockPos, LockData>> clientAll() {
        return CLIENT;
    }

    public static void clientSet(ChunkPos chunkPos, List<ChunkLocks.Entry> entries) {
        if (entries.isEmpty()) {
            CLIENT.remove(chunkPos.toLong());
            return;
        }
        Map<BlockPos, LockData> chunk = new HashMap<>();
        for (ChunkLocks.Entry entry : entries) {
            chunk.put(entry.pos(), entry.lock());
        }
        CLIENT.put(chunkPos.toLong(), chunk);
    }

    // Игрок начал видеть чанк — отправляем его замки
    @SubscribeEvent
    public static void onWatch(ChunkWatchEvent.Watch event) {
        LevelChunk chunk = event.getChunk();
        if (chunk.hasData(ModAttachments.CHUNK_LOCKS)) {
            ChunkLocks data = chunk.getData(ModAttachments.CHUNK_LOCKS);
            if (!data.isEmpty()) {
                PacketDistributor.sendToPlayer(event.getPlayer(), new LocksChunkPayload(chunk.getPos(), data.entries()));
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            CLIENT.remove(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            CLIENT.clear();
        }
    }
}