package com.nrdhvnstd.halgventmod.npc;

/**
 * Распорядок дня НПС (docs/TZ_npc.md, 2.4). Время — игровое время суток 0…23999: 0 = 06:00, 12000 = 18:00,
 * 18000 = 00:00 (так же, как в /calendar time). Рассвет и закат в течение года двигаются, но игровые сутки
 * остаются по 24 000, поэтому распорядок держится по ним.
 */
public final class NpcSchedule {
    /** Чем НПС занят: работа — у поста, вечер — у дома, сон — в постели. */
    public enum Activity { WORK, HOME, SLEEP }

    // Меняйте здесь (время суток в тиках, 0 = 06:00)
    private static final long WORK_END = 11000;   // 17:00 — рабочий день кончился
    private static final long SLEEP_START = 15000; // 21:00 — легли
    private static final long SLEEP_END = 23000;   // 05:00 — встали, ещё дома
    // Радиус хождения вокруг привязки, блоков
    private static final int CITIZEN_WORK_RADIUS = 10;
    private static final int MERCHANT_WORK_RADIUS = 3;
    private static final int GUARD_WORK_RADIUS = 2;
    private static final int HOME_RADIUS = 4;

    private NpcSchedule() {}

    /** Занятие в этот час. Стражник пока всегда на посту — смены придут вместе с его целями (этап 7). */
    public static Activity at(NpcRole role, long dayTime) {
        if (role == NpcRole.GUARD) {
            return Activity.WORK;
        }
        long time = Math.floorMod(dayTime, 24000L);
        if (time < WORK_END) {
            return Activity.WORK;
        }
        if (time >= SLEEP_START && time < SLEEP_END) {
            return Activity.SLEEP;
        }
        return Activity.HOME;
    }

    /** Радиус, в котором НПС бродит около привязки этого занятия. */
    public static int radius(NpcRole role, Activity activity) {
        if (activity != Activity.WORK) {
            return HOME_RADIUS;
        }
        return switch (role) {
            case CITIZEN -> CITIZEN_WORK_RADIUS;
            case MERCHANT -> MERCHANT_WORK_RADIUS;
            case GUARD -> GUARD_WORK_RADIUS;
        };
    }
}
