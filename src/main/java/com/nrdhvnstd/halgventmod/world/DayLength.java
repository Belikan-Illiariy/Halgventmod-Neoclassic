package com.nrdhvnstd.halgventmod.world;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Длина игровых суток. Ванилла каждый тик сдвигает время суток на 1 (сутки = 24 000 единиц = 20 минут).
 * Мы после каждого тика откатываем этот шаг и двигаем время медленнее, так что сутки длятся 3 часа.
 * Дневная и ночная половины идут с разной скоростью в зависимости от даты: летом день длиннее, зимой ночь.
 * Сон, /time set и прочие скачки времени работают как обычно.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class DayLength {
    // ===== Длина суток — меняйте здесь =====
    public static final int DAY_LENGTH_TICKS = 90 * 60 * 20; // 1,5 часа реального времени

    private static final Clock SERVER_CLOCK = new Clock();

    private DayLength() {}

    /** Сколько единиц игрового времени проходит за один тик в этой половине суток в этот день года. */
    public static double rate(long dayTime, int dayOfYear) {
        double dayShare = Calendar.dayShare(dayOfYear);
        boolean daytime = Math.floorMod(dayTime, (long) Calendar.TIME_PER_DAY) < Calendar.TIME_PER_DAY / 2;
        double ticks = DAY_LENGTH_TICKS * (daytime ? dayShare : 1.0 - dayShare);
        return (Calendar.TIME_PER_DAY / 2.0) / ticks;
    }

    /** Замедлитель времени. Отдельный экземпляр на сервере и на клиенте. */
    public static final class Clock {
        private long last = Long.MIN_VALUE;
        private double accumulator;

        /** Принимает время после ванильного шага и возвращает, каким оно должно быть. */
        public long correct(long now, int dayOfYear) {
            if (last != Long.MIN_VALUE && now == last + 1) {
                // Обычный ход времени: вместо +1 за тик даём столько, сколько положено в этот день года
                accumulator += rate(last, dayOfYear);
                long step = (long) accumulator;
                accumulator -= step;
                last += step;
                return last;
            }
            // Первый тик, сон, /time set, синхронизация с сервером — принимаем как есть
            last = now;
            return now;
        }

        public void reset() {
            last = Long.MIN_VALUE;
            accumulator = 0;
        }
    }

    // Время общее для всех измерений и хранится в верхнем мире
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        int dayOfYear = Calendar.dayOfYear(WorldCalendar.get(server).day());
        long now = overworld.getDayTime();
        long corrected = SERVER_CLOCK.correct(now, dayOfYear);
        if (corrected != now) {
            overworld.setDayTime(corrected);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        SERVER_CLOCK.reset();
    }
}