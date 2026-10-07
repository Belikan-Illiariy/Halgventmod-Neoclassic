package com.nrdhvnstd.halgventmod.network;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.lock.ChunkLocks;
import com.nrdhvnstd.halgventmod.lock.LockSync;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: все замки одного чанка. */
public record LocksChunkPayload(ChunkPos chunk, List<ChunkLocks.Entry> entries) implements CustomPacketPayload {
    public static final Type<LocksChunkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "locks_chunk"));

    public static final StreamCodec<ByteBuf, LocksChunkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.map(ChunkPos::new, ChunkPos::toLong), LocksChunkPayload::chunk,
            ChunkLocks.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), LocksChunkPayload::entries,
            LocksChunkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LocksChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LockSync.clientSet(payload.chunk(), payload.entries()));
    }
}