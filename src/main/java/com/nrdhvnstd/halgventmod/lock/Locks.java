package com.nrdhvnstd.halgventmod.lock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.network.LocksChunkPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

/** Общие операции с замками: где они висят, подходит ли ключ, запись и синхронизация. */
public final class Locks {
    private Locks() {}

    /** Что можно запереть: двери и сундуки (ванильные и наследники ванильных, например из Conquest). */
    public static boolean isLockable(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof ChestBlock;
    }

    /**
     * Позиции одного запираемого места: нижние половины обеих створок двойной двери
     * или обе половины двойного сундука. Замок вешается на все сразу.
     */
    public static List<BlockPos> place(Level level, BlockPos pos, BlockState state) {
        List<BlockPos> place = new ArrayList<>(2);
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            place.add(lower.immutable());
            BlockState lowerState = level.getBlockState(lower);
            if (lowerState.getBlock() instanceof DoorBlock) {
                BlockPos partner = doorPartner(level, lower, lowerState);
                if (partner != null) {
                    place.add(partner);
                }
            }
        } else if (state.getBlock() instanceof ChestBlock) {
            place.add(pos.immutable());
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                place.add(pos.relative(ChestBlock.getConnectedDirection(state)).immutable());
            }
        }
        return place;
    }

    // Вторая створка двойной двери: смотрит туда же, петли с другой стороны
    @Nullable
    private static BlockPos doorPartner(Level level, BlockPos lower, BlockState state) {
        Direction facing = state.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = state.getValue(DoorBlock.HINGE);
        BlockPos other = lower.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
        BlockState otherState = level.getBlockState(other);
        if (otherState.getBlock() instanceof DoorBlock
                && otherState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && otherState.getValue(DoorBlock.FACING) == facing
                && otherState.getValue(DoorBlock.HINGE) != hinge) {
            return other.immutable();
        }
        return null;
    }

    /** Замок в одной позиции. На клиенте — из копии, присланной сервером. */
    @Nullable
    public static LockData get(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return LockSync.clientGet(pos);
        }
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.hasData(ModAttachments.CHUNK_LOCKS) ? chunk.getData(ModAttachments.CHUNK_LOCKS).get(pos) : null;
    }

    /** Замок места: первый найденный на любой его позиции. */
    @Nullable
    public static LockData find(Level level, List<BlockPos> place) {
        for (BlockPos pos : place) {
            LockData lock = get(level, pos);
            if (lock != null) {
                return lock;
            }
        }
        return null;
    }

    /** Записать замок на всё место (null — снять) и сообщить об этом игрокам поблизости. */
    public static void put(ServerLevel level, List<BlockPos> place, @Nullable LockData lock) {
        Set<LevelChunk> changed = new HashSet<>();
        for (BlockPos pos : place) {
            LevelChunk chunk = level.getChunkAt(pos);
            ChunkLocks data = chunk.getData(ModAttachments.CHUNK_LOCKS);
            if (lock == null) {
                data.remove(pos);
            } else {
                data.put(pos, lock);
            }
            chunk.setUnsaved(true);
            changed.add(chunk);
        }
        for (LevelChunk chunk : changed) {
            PacketDistributor.sendToPlayersTrackingChunk(level, chunk.getPos(),
                    new LocksChunkPayload(chunk.getPos(), chunk.getData(ModAttachments.CHUNK_LOCKS).entries()));
        }
    }

    /** Подходит ли предмет к замку: ключ — по секрету, связка — если подходит хоть один ключ на ней. */
    public static boolean fits(LockData lock, ItemStack stack) {
        if (stack.is(ModItems.KEY_RING.get())) {
            for (ItemStack key : KeyRingItem.keys(stack)) {
                if (fits(lock, key)) {
                    return true;
                }
            }
            return false;
        }
        if (stack.is(ModItems.KEY.get())) {
            Long secret = stack.get(ModComponents.LOCK_SECRET.get());
            return secret != null && secret == lock.secret();
        }
        return false;
    }

    public static long newId(MinecraftServer server) {
        return LockIds.next(server);
    }

    public static ItemStack makeKey(long secret, @Nullable String label) {
        ItemStack key = new ItemStack(ModItems.KEY.get());
        key.set(ModComponents.LOCK_SECRET.get(), secret);
        if (label != null && !label.isBlank()) {
            key.set(ModComponents.KEY_LABEL.get(), label);
        }
        return key;
    }

    public static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Закрыть открытые двери места — запирают только закрытое. */
    public static void closeDoors(Level level, List<BlockPos> place, @Nullable Entity who) {
        for (BlockPos pos : place) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.OPEN)) {
                door.setOpen(who, level, state, pos, false);
            }
        }
    }
}