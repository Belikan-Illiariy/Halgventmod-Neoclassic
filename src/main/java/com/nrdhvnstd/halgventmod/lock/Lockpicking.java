package com.nrdhvnstd.halgventmod.lock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.LockpickStartPayload;
import com.nrdhvnstd.halgventmod.npc.Crimes;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Взлом замка отмычкой — сервер. E с отмычкой по запертому замку (LockEvents) открывает у игрока мини-игру
 * (client/lock/LockpickScreen): штифты, натяжение, отмычка, которая может сломаться. Игра идёт у клиента, как кузнечные;
 * сервер выдаёт зерно (по нему клиент строит замок), следит за сеансом и проверяет итог: игрок рядом, отмычка в руке,
 * замок на месте и заперт, и взлом не быстрее физического минимума. Врезной замок сложнее навесного.
 * Пока идёт взлом и в момент его начала и конца, стражник, который видит игрока, считает это преступлением (Crimes).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Lockpicking {
    /** Чем кончился взлом. */
    public enum Outcome { SUCCESS, BROKEN, CANCEL }

    // Меняйте здесь ⚠️ (сложность подбирать в игре)
    public static final int PADLOCK_PINS = 5;
    public static final int MORTISE_PINS = 7;
    public static final float PADLOCK_WINDOW = 0.045F;   // допуск у линии среза; от него же ширина «вилки» натяжения
    public static final float MORTISE_WINDOW = 0.030F;
    private static final int MIN_TICKS_PER_PIN = 20;     // быстрее штифт не поставить — клиент врёт
    private static final double MAX_DISTANCE = 6.0;         // блоков до замка
    private static final int SESSION_MAX_TICKS = 6000;      // 5 минут на взлом
    private static final int CHECK_INTERVAL = 10;           // как часто смотреть, видит ли стража

    private record Session(BlockPos at, List<BlockPos> place, long start, int pins) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Lockpicking() {}

    /** Начать взлом: открыть мини-игру. */
    public static void start(ServerLevel level, ServerPlayer player, BlockPos at, List<BlockPos> place, LockData lock) {
        if (SESSIONS.containsKey(player.getUUID())) {
            return;
        }
        int pins = lock.mortise() ? MORTISE_PINS : PADLOCK_PINS;
        float window = lock.mortise() ? MORTISE_WINDOW : PADLOCK_WINDOW;
        SESSIONS.put(player.getUUID(), new Session(at, place, level.getGameTime(), pins));
        PacketDistributor.sendToPlayer(player, new LockpickStartPayload(level.getRandom().nextLong(), pins, window));
        Crimes.committed(player); // стражник, который уже смотрит, увидел начало
    }

    /** Итог от клиента. */
    public static void finish(ServerPlayer player, Outcome outcome, int ticks) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null || !(player.level() instanceof ServerLevel level) || outcome == Outcome.CANCEL) {
            return;
        }
        ItemStack pick = player.getMainHandItem();
        LockData lock = Locks.find(level, session.place());
        if (!pick.is(ModItems.LOCKPICK.get()) || lock == null || !lock.locked()
                || player.distanceToSqr(session.at().getX() + 0.5, session.at().getY() + 0.5, session.at().getZ() + 0.5)
                > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        Crimes.committed(player); // закончил на глазах у стражи — тоже виден
        if (outcome == Outcome.BROKEN) {
            pick.shrink(1);
            feedback(level, player, session.at(), "pick_broke", SoundEvents.ITEM_BREAK, 0.8F);
            return;
        }
        if (ticks < session.pins() * MIN_TICKS_PER_PIN) { // быстрее не бывает — клиент врёт
            feedback(level, player, session.at(), "pick_failed", SoundEvents.CHEST_LOCKED, 1.0F);
            return;
        }
        Locks.put(level, session.place(), lock.withLocked(false));
        pick.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        feedback(level, player, session.at(), "picked", SoundEvents.IRON_TRAPDOOR_OPEN, 1.6F);
    }

    // Раз в полсекунды: взломщик ушёл, убрал отмычку или возится слишком долго — сеанс снимается; пока идёт — стража смотрит
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty() || event.getServer().getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isAlive() || !player.getMainHandItem().is(ModItems.LOCKPICK.get())
                    || player.level().getGameTime() - session.start() > SESSION_MAX_TICKS) {
                it.remove();
                continue;
            }
            Crimes.committed(player);
        }
    }

    private static void feedback(ServerLevel level, ServerPlayer player, BlockPos at, String message, SoundEvent sound, float pitch) {
        player.displayClientMessage(Component.translatable("lock.halgventmod." + message), true);
        level.playSound(null, at, sound, SoundSource.BLOCKS, 0.8F, pitch);
    }
}
