package com.nrdhvnstd.halgventmod.npc;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Стражник ходит по маршруту (HalgNpc.getRoute, точки ставит жезл НПС) туда и обратно: дошёл до точки, постоял и к
 * следующей; у последней разворачивается. Меньше двух точек — маршрута нет, стоит на посту. Погоня и бой выше по
 * приоритету, после них патруль продолжается с ближайшей точки.
 */
final class PatrolGoal extends Goal {
    // Меняйте здесь
    private static final double SPEED = 1.0;
    private static final double ARRIVE_DISTANCE_SQR = 4.0;  // 2 блока
    private static final int PAUSE_MIN = 40;                // постоял у точки: 2–5 секунд
    private static final int PAUSE_RANGE = 60;
    private static final int STUCK_TICKS = 600;             // не дошёл за 30 с — к следующей

    private final HalgNpc guard;
    private int index;
    private int direction = 1;
    private int pause;
    private int walking;

    PatrolGoal(HalgNpc guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return guard.getRole() == NpcRole.GUARD && guard.getTarget() == null && !guard.isSleeping()
                && guard.getRoute().size() >= 2;
    }

    @Override
    public void start() {
        List<BlockPos> route = guard.getRoute();
        index = nearest(route);
        direction = 1;
        pause = 0;
        walking = 0;
        goTo(route.get(index));
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        List<BlockPos> route = guard.getRoute();
        if (route.size() < 2) {
            return;
        }
        index = Math.min(index, route.size() - 1);
        BlockPos point = route.get(index);
        if (pause > 0) {
            pause--;
            if (pause == 0) {
                advance(route.size());
                walking = 0;
                goTo(route.get(index));
            }
            return;
        }
        walking++;
        boolean arrived = guard.distanceToSqr(point.getX() + 0.5, point.getY(), point.getZ() + 0.5) <= ARRIVE_DISTANCE_SQR;
        if (arrived) {
            guard.getNavigation().stop();
            pause = PAUSE_MIN + guard.getRandom().nextInt(PAUSE_RANGE);
        } else if (walking > STUCK_TICKS) {
            advance(route.size());
            walking = 0;
            goTo(route.get(index));
        } else if (guard.getNavigation().isDone()) {
            goTo(point); // дорога оборвалась — пробуем снова
        }
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
    }

    // Туда и обратно: у конца разворот
    private void advance(int size) {
        if (index + direction < 0 || index + direction >= size) {
            direction = -direction;
        }
        index += direction;
    }

    private static int nearestIndex(List<BlockPos> route, BlockPos from) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double distance = route.get(i).distSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private int nearest(List<BlockPos> route) {
        return nearestIndex(route, guard.blockPosition());
    }

    private void goTo(BlockPos pos) {
        guard.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPEED);
    }
}
