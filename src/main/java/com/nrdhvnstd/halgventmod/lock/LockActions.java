package com.nrdhvnstd.halgventmod.lock;

import java.util.List;

import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Что происходит с замком на сервере. Решение, какое действие выбрать, — в LockEvents. */
public final class LockActions {
    private LockActions() {}

    // ----- Навесной замок -----

    /**
     * Повесить навесной замок. Новый замок получает новый секрет и даёт ключ,
     * а с ключом во второй руке — секрет этого ключа (так один ключ открывает несколько дверей).
     * Снятый ранее замок сохраняет свой секрет.
     */
    public static void installPadlock(ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> place,
                                      ItemStack padlock) {
        ItemStack off = player.getOffhandItem();
        Long oldSecret = padlock.get(ModComponents.LOCK_SECRET.get());

        long secret;
        boolean giveKey = false;
        if (oldSecret != null) {
            secret = oldSecret;
        } else {
            Long keySecret = off.is(ModItems.KEY.get()) ? off.get(ModComponents.LOCK_SECRET.get()) : null;
            if (keySecret != null) {
                secret = keySecret;
            } else {
                secret = Locks.newId(level.getServer());
                giveKey = true;
            }
        }

        Locks.put(level, place, new LockData(secret, false, false));
        padlock.consume(1, player);
        if (giveKey) {
            Locks.give(player, Locks.makeKey(secret, null));
        }
        feedback(level, player, at, "installed", SoundEvents.CHAIN_PLACE, 1.0F);
    }

    /** Снять навесной замок пустой рукой — только отпертый. Замок уходит в инвентарь вместе с секретом. */
    public static void removePadlock(ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> place,
                                     LockData lock) {
        if (lock.mortise()) {
            feedback(level, player, at, "cannot_remove", SoundEvents.CHEST_LOCKED, 1.0F);
            return;
        }
        if (lock.locked()) {
            feedback(level, player, at, "locked", SoundEvents.CHEST_LOCKED, 1.0F);
            return;
        }
        Locks.put(level, place, null);
        ItemStack padlock = new ItemStack(ModItems.PADLOCK.get());
        padlock.set(ModComponents.LOCK_SECRET.get(), lock.secret());
        Locks.give(player, padlock);
        feedback(level, player, at, "removed", SoundEvents.CHAIN_BREAK, 1.0F);
    }

    // ----- Ключ или связка: Shift + E — запереть или отпереть -----

    public static void useKey(ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> place,
                              LockData lock, ItemStack main) {
        if (!Locks.fits(lock, main)) {
            feedback(level, player, at, "wrong_key", SoundEvents.CHEST_LOCKED, 1.0F);
            return;
        }
        boolean nowLocked = !lock.locked();
        if (nowLocked) {
            Locks.closeDoors(level, place, player);
        }
        Locks.put(level, place, lock.withLocked(nowLocked));
        if (nowLocked) {
            feedback(level, player, at, "locked", SoundEvents.IRON_TRAPDOOR_CLOSE, 1.6F);
        } else {
            feedback(level, player, at, "unlocked", SoundEvents.IRON_TRAPDOOR_OPEN, 1.6F);
        }
    }

    /** Дёрнули запертое: не открывается. */
    public static void denied(ServerLevel level, ServerPlayer player, BlockPos at) {
        feedback(level, player, at, "locked", SoundEvents.CHEST_LOCKED, 1.0F);
    }

    // ----- Набор ключника (администрация или творческий режим) -----

    /** Кому набор ключника не просто предмет: администрации (права 2+) и в творческом режиме. */
    public static boolean mayUseKit(Player player) {
        return player.hasPermissions(2) || player.isCreative();
    }

    /**
     * Где замка нет: E — навесной замок, Shift + E — врезной (секрет — новый, с ключом, или ключа во второй руке).
     * Shift + E по замку — снять любой, навесной или врезной. E по замку сюда не попадает — открывает как обычно.
     */
    public static void locksmithKit(ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> place,
                                    LockData lock, boolean sneaking) {
        if (!mayUseKit(player)) {
            feedback(level, player, at, "no_permission", null, 1.0F);
            return;
        }
        if (lock != null) {
            Locks.put(level, place, null);
            feedback(level, player, at, "removed", SoundEvents.CHAIN_BREAK, 1.0F);
            return;
        }

        ItemStack off = player.getOffhandItem();
        Long keySecret = off.is(ModItems.KEY.get()) ? off.get(ModComponents.LOCK_SECRET.get()) : null;
        long secret = keySecret != null ? keySecret : Locks.newId(level.getServer());

        Locks.put(level, place, new LockData(secret, sneaking, false));
        if (keySecret == null) {
            Locks.give(player, Locks.makeKey(secret, null));
        }
        if (sneaking) {
            feedback(level, player, at, "mortise_installed", SoundEvents.CHAIN_PLACE, 0.8F);
        } else {
            feedback(level, player, at, "installed", SoundEvents.CHAIN_PLACE, 1.0F);
        }
    }

    /** «Замок: секрет 17, врезной, отперт» */
    public static Component info(LockData lock) {
        return Component.translatable("lock.halgventmod.info",
                        lock.secret(),
                        Component.translatable(lock.mortise() ? "lock.halgventmod.type.mortise" : "lock.halgventmod.type.padlock"),
                        Component.translatable(lock.locked() ? "lock.halgventmod.state.locked" : "lock.halgventmod.state.unlocked"))
                .withStyle(ChatFormatting.GRAY);
    }

    // Надпись над хотбаром и звук у замка
    private static void feedback(ServerLevel level, ServerPlayer player, BlockPos at, String message,
                                 SoundEvent sound, float pitch) {
        player.displayClientMessage(Component.translatable("lock.halgventmod." + message), true);
        if (sound != null) {
            level.playSound(null, at, sound, SoundSource.BLOCKS, 0.8F, pitch);
        }
    }
}