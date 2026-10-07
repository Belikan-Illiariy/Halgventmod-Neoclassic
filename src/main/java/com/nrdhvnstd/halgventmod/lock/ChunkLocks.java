package com.nrdhvnstd.halgventmod.lock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/** Все замки одного чанка. Хранится в данных чанка и сохраняется вместе с ним. */
public class ChunkLocks {
    /** Замок и место, где он висит. */
    public record Entry(BlockPos pos, LockData lock) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                LockData.CODEC.fieldOf("lock").forGetter(Entry::lock)
        ).apply(instance, Entry::new));

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos,
                LockData.STREAM_CODEC, Entry::lock,
                Entry::new);
    }

    public static final Codec<ChunkLocks> CODEC = Entry.CODEC.listOf().xmap(ChunkLocks::fromEntries, ChunkLocks::entries);

    private final Map<BlockPos, LockData> locks = new HashMap<>();

    public ChunkLocks() {}

    @Nullable
    public LockData get(BlockPos pos) {
        return locks.get(pos);
    }

    public void put(BlockPos pos, LockData lock) {
        locks.put(pos.immutable(), lock);
    }

    public void remove(BlockPos pos) {
        locks.remove(pos);
    }

    public boolean isEmpty() {
        return locks.isEmpty();
    }

    public List<Entry> entries() {
        List<Entry> list = new ArrayList<>(locks.size());
        locks.forEach((pos, lock) -> list.add(new Entry(pos, lock)));
        return list;
    }

    private static ChunkLocks fromEntries(List<Entry> entries) {
        ChunkLocks chunkLocks = new ChunkLocks();
        for (Entry entry : entries) {
            chunkLocks.put(entry.pos(), entry.lock());
        }
        return chunkLocks;
    }
}