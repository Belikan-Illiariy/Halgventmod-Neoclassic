package com.nrdhvnstd.halgventmod.npc;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/** Ночью — дойти до своей кровати и лечь (HalgNpc.bed ищется по расписанию). Не дошёл за отведённое время — стоит дома. */
final class SleepInBedGoal extends Goal {
    private static final double SPEED = 1.0;
    private static final double LIE_DOWN_DISTANCE_SQR = 2.25; // 1,5 блока
    private static final int GIVE_UP_TICKS = 600;               // 30 с на дорогу

    private final HalgNpc npc;
    private int walking;

    SleepInBedGoal(HalgNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.getActivity() == NpcSchedule.Activity.SLEEP && !npc.isSleeping() && npc.getBed() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && walking < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        walking = 0;
        BlockPos bed = npc.getBed();
        npc.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, SPEED);
    }

    @Override
    public void tick() {
        BlockPos bed = npc.getBed();
        if (bed == null) {
            return;
        }
        walking++;
        if (npc.distanceToSqr(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5) <= LIE_DOWN_DISTANCE_SQR) {
            npc.startSleeping(bed);
        } else if (npc.getNavigation().isDone()) {
            npc.getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, SPEED);
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
    }
}
