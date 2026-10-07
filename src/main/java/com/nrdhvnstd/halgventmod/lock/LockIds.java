package com.nrdhvnstd.halgventmod.lock;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Счётчик номеров для секретов замков и групп: 1, 2, 3…
 * Общий для всего мира, поэтому два замка или две группы случайно не совпадут.
 */
public class LockIds extends SavedData {
    private static final String NAME = HalgventMod.MODID + "_lock_ids";
    private static final Factory<LockIds> FACTORY = new Factory<>(LockIds::new, LockIds::load, null);

    private long next = 1;

    public static long next(MinecraftServer server) {
        LockIds ids = server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        long id = ids.next++;
        ids.setDirty();
        return id;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("next", next);
        return tag;
    }

    private static LockIds load(CompoundTag tag, HolderLookup.Provider registries) {
        LockIds ids = new LockIds();
        ids.next = Math.max(tag.getLong("next"), 1);
        return ids;
    }
}