package com.nrdhvnstd.halgventmod.npc;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Стражник, услышавший зов на помощь (Crimes.callForHelp), бежит к месту правонарушения. Увидит нарушителя в 40 блоках —
 * его подхватит PursueOffenderGoal. Добежал, тревога кончилась или нарушителя больше не ищут (нок, прощён) — тревога
 * снимается, и он возвращается на свой маршрут (PatrolGoal).
 */
final class RespondToAlarmGoal extends Goal {
    private static final double ARRIVE_DISTANCE_SQR = 36.0; // 6 блоков от места
    private static final int REPATH_TICKS = 40;

    private final HalgNpc guard;
    private final double speed;
    private int repath;

    RespondToAlarmGoal(HalgNpc guard, double speed) {
        this.guard = guard;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return active() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return active() != null;
    }

    // Куда бежать, или null — тревога не в силе
    @Nullable
    private BlockPos active() {
        if (guard.getRole() != NpcRole.GUARD || guard.getTarget() != null || guard.isSleeping()) {
            return null;
        }
        BlockPos alarm = guard.getAlarm();
        if (alarm == null) {
            return null;
        }
        if (!Crimes.anyWanted() || guard.distanceToSqr(alarm.getX() + 0.5, alarm.getY(), alarm.getZ() + 0.5) <= ARRIVE_DISTANCE_SQR) {
            guard.clearAlarm();
            return null;
        }
        return alarm;
    }

    @Override
    public void start() {
        repath = 0;
        run();
    }

    @Override
    public void tick() {
        if (--repath <= 0) {
            run();
        }
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
    }

    private void run() {
        BlockPos alarm = guard.getAlarm();
        if (alarm != null) {
            guard.getNavigation().moveTo(alarm.getX() + 0.5, alarm.getY(), alarm.getZ() + 0.5, speed);
        }
        repath = REPATH_TICKS;
    }
}
