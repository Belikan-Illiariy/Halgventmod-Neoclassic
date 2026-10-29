package com.nrdhvnstd.halgventmod.npc;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Горожанин время от времени идёт на случайную точку интереса (магазин, рынок, трактир, площадь), гуляет там
 * и возвращается к своим делам. Куда — по весам вида для текущего занятия (PoiType.weight). Торговцы и стражники
 * сюда не ходят. Пока идёт, поверх привязки (MoveTowardsRestrictionGoal) — приоритет выше.
 * В трактире (PoiType.hasSeats) вместо прогулки садится на свободный стул из тега npc_seats и сидит, потом встаёт.
 */
final class VisitPoiGoal extends Goal {
    // Меняйте здесь (тики; игровые сутки идут ~1,5 ч реального времени, так что это минуты)
    private static final double SEARCH_RADIUS = 96;  // блоков от НПС
    private static final int FIRST_DELAY_MIN = 200;  // до первого похода после появления
    private static final int FIRST_DELAY_RANGE = 1200;
    private static final int PAUSE_MIN = 2400;       // между походами: 2–5 минут
    private static final int PAUSE_RANGE = 3600;
    private static final int RETRY_DELAY = 1200;     // нет подходящих точек — проверить позже
    private static final int STAY_MIN = 400;         // гуляет на точке: 20–45 секунд
    private static final int STAY_RANGE = 500;
    private static final int SIT_MIN = 1200;         // сидит на стуле: 1–2 минуты ⚠️
    private static final int SIT_RANGE = 1200;
    private static final int SEAT_WALK_LIMIT = 400;  // не дошёл до стула за 20 с — бросить стул
    private static final double SIT_DISTANCE_SQR = 4.0;   // садится, подойдя на 2 блока
    private static final double SIT_DISTANCE_LATE_SQR = 6.25; // или остановившись в 2,5 блоках
    private static final int FACE_INTERVAL = 20;     // как часто сидящий поправляет взгляд
    private static final int TRAVEL_LIMIT = 1800;    // не дошёл за 90 с — бросить
    private static final double SPEED = 1.0;
    private static final int ARRIVE_DISTANCE_SQR = 9; // 3 блока
    private static final int STROLL_RADIUS = 4;
    private static final int STROLL_INTERVAL = 60;

    private final HalgNpc npc;
    private long nextVisit;
    @Nullable
    private BlockPos target;
    @Nullable
    private PoiType targetType;
    private int travelling;
    private int stayLeft;
    private boolean arrived;
    // Стул (трактир)
    private boolean seatTried;
    @Nullable
    private BlockPos chair;
    @Nullable
    private SeatEntity seat;
    private int seatWalk;

    VisitPoiGoal(HalgNpc npc) {
        this.npc = npc;
        this.nextVisit = Long.MIN_VALUE;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (npc.getRole() != NpcRole.CITIZEN || npc.isSleeping() || npc.isPassenger()
                || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        NpcSchedule.Activity activity = npc.getActivity();
        if (activity == null || activity == NpcSchedule.Activity.SLEEP) {
            return false;
        }
        long now = level.getGameTime();
        if (nextVisit == Long.MIN_VALUE) { // первый раз — не сразу и не у всех в один миг
            nextVisit = now + FIRST_DELAY_MIN + npc.getRandom().nextInt(FIRST_DELAY_RANGE);
        }
        if (now < nextVisit) {
            return false;
        }
        NpcPois.Poi poi = pick(level, activity);
        if (poi == null) {
            nextVisit = now + RETRY_DELAY;
            return false;
        }
        target = poi.pos();
        targetType = poi.type();
        return true;
    }

    // Случайная точка по весам вида; null — подходящих рядом нет
    @Nullable
    private NpcPois.Poi pick(ServerLevel level, NpcSchedule.Activity activity) {
        List<NpcPois.Poi> near = NpcPois.get(level.getServer()).near(level, npc.blockPosition(), SEARCH_RADIUS);
        int total = 0;
        for (NpcPois.Poi poi : near) {
            total += poi.type().weight(activity);
        }
        if (total <= 0) {
            return null;
        }
        int roll = npc.getRandom().nextInt(total);
        for (NpcPois.Poi poi : near) {
            roll -= poi.type().weight(activity);
            if (roll < 0) {
                return poi;
            }
        }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        NpcSchedule.Activity activity = npc.getActivity();
        return target != null && activity != null && activity != NpcSchedule.Activity.SLEEP
                && !npc.isSleeping() && (arrived ? stayLeft > 0 : travelling < TRAVEL_LIMIT);
    }

    @Override
    public void start() {
        travelling = 0;
        arrived = false;
        seatTried = false;
        chair = null;
        seat = null;
        stayLeft = STAY_MIN + npc.getRandom().nextInt(STAY_RANGE);
        goTo(target);
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        if (!arrived) {
            travelling++;
            if (npc.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= ARRIVE_DISTANCE_SQR) {
                arrived = true;
            } else if (npc.getNavigation().isDone()) {
                goTo(target); // дорога оборвалась — пробуем снова
            }
            return;
        }
        if (targetType != null && targetType.hasSeats() && npc.level() instanceof ServerLevel level) {
            if (!seatTried) {
                seatTried = true;
                findChair(level);
            }
            if (chair != null) {
                seatTick(level);
                return;
            }
        }
        stayLeft--;
        if (stayLeft % STROLL_INTERVAL == 0) { // гуляет по площадке
            Vec3 spot = LandRandomPos.getPosTowards(npc, STROLL_RADIUS, 2, Vec3.atBottomCenterOf(target));
            if (spot != null) {
                npc.getNavigation().moveTo(spot.x, spot.y, spot.z, SPEED * 0.7);
            }
        }
    }

    // Выбрать свободный стул у трактира и занять его, пока идёт
    private void findChair(ServerLevel level) {
        BlockPos found = NpcSeats.find(level, target, npc.getRandom());
        if (found == null) {
            return;
        }
        seat = NpcSeats.reserve(level, found);
        if (seat != null) {
            chair = found;
            seatWalk = 0;
        }
    }

    // Дойти до стула и сесть; севшему время идёт, как на прогулке
    private void seatTick(ServerLevel level) {
        if (npc.isPassenger()) {
            stayLeft--;
            if (stayLeft % FACE_INTERVAL == 0) {
                face(level);
            }
            return;
        }
        seatWalk++;
        double distance = npc.distanceToSqr(chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5);
        if (distance <= SIT_DISTANCE_SQR || (npc.getNavigation().isDone() && distance <= SIT_DISTANCE_LATE_SQR)) {
            npc.getNavigation().stop();
            npc.startRiding(seat, true);
            stayLeft = SIT_MIN + npc.getRandom().nextInt(SIT_RANGE);
            face(level);
        } else if (seatWalk > SEAT_WALK_LIMIT) {
            releaseSeat(level); // не добрался — гуляет, как без стула
        } else if (npc.getNavigation().isDone()) {
            npc.getNavigation().moveTo(chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5, SPEED);
        }
    }

    // Сидит лицом туда же, куда стул (или на точку интереса)
    private void face(ServerLevel level) {
        float yaw = NpcSeats.yaw(level.getBlockState(chair), chair, target);
        npc.setYRot(yaw);
        npc.yBodyRot = yaw;
        npc.setYHeadRot(yaw);
    }

    // Встать (если сидел) и отпустить стул
    private void releaseSeat(ServerLevel level) {
        if (npc.isPassenger()) {
            npc.stopRiding();
            if (chair != null) {
                BlockPos stand = NpcSeats.standPos(level, chair);
                npc.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, npc.getYRot(), npc.getXRot());
            }
        }
        if (seat != null && seat.isAlive()) {
            seat.discard();
        }
        seat = null;
        chair = null;
    }

    @Override
    public void stop() {
        if (npc.level() instanceof ServerLevel level) {
            releaseSeat(level);
        }
        npc.getNavigation().stop();
        target = null;
        targetType = null;
        nextVisit = npc.level().getGameTime() + PAUSE_MIN + npc.getRandom().nextInt(PAUSE_RANGE);
    }

    private void goTo(BlockPos pos) {
        npc.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPEED);
    }
}
