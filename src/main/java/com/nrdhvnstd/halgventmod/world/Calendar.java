package com.nrdhvnstd.halgventmod.world;

import java.util.List;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Календарь мира: неделя 8 дней, месяц 4 недели (32 дня), год 12 месяцев (384 дня), сезон 3 месяца.
 * Год начинается с весны. Сутки начинаются на рассвете (06:00 по игровым часам).
 * Мир начинается 1 студня (декабря) 1419 года.
 * Здесь только расчёты и названия; номер текущего дня и события хранит WorldCalendar.
 */
public final class Calendar {
    // ===== Устройство календаря =====
    public static final int DAYS_PER_WEEK = 8;
    public static final int WEEKS_PER_MONTH = 4;
    public static final int DAYS_PER_MONTH = DAYS_PER_WEEK * WEEKS_PER_MONTH; // 32
    public static final int MONTHS_PER_YEAR = 12;
    public static final int DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR;   // 384
    public static final int MONTHS_PER_SEASON = 3;

    // ===== Начальная дата мира =====
    public static final int START_YEAR = 1419;
    public static final int START_MONTH = 10;       // Студень — по-старому декабрь
    public static final int START_DAY_OF_MONTH = 1;
    private static final int START_OFFSET = (START_MONTH - 1) * DAYS_PER_MONTH + (START_DAY_OF_MONTH - 1);

    // ===== Длина дня по сезонам =====
    // Доля суток, которую занимает день в середине лета. 2/3 = 1 ч дня и 30 мин ночи;
    // в середине зимы наоборот, в середине весны и осени поровну.
    public static final double DAY_SHARE_MAX = 2.0 / 3.0;
    // Середина лета: лето — 4, 5 и 6-й месяцы, середина — 16-й день 5-го месяца
    public static final int MIDSUMMER = 4 * DAYS_PER_MONTH + DAYS_PER_MONTH / 2;

    /** Единиц игрового времени в сутках — ванильные 24 000, они не меняются, меняется только скорость. */
    public static final int TIME_PER_DAY = 24000;

    // ===== Названия — меняйте здесь, переводы сделает runData =====
    public static final String[] WEEKDAYS_RU = {
            "Первица", "Вторица", "Третица", "Четверица", "Пятица", "Шестица", "Седмица", "Осьмица"};
    public static final String[] WEEKDAYS_EN = {
            "Firstday", "Secondday", "Thirdday", "Fourthday", "Fifthday", "Sixthday", "Seventhday", "Eighthday"};

    // Именительный падеж — для заголовков, родительный — для даты («3 травня»)
    public static final String[] MONTHS_RU = {
            "Март", "Апрель", "Май",      // весна
            "Июнь", "Июль", "Август",        // лето
            "Сентябрь", "Октябрь", "Ноябрь",     // осень
            "Декабрь", "Январь", "Февраль"};        // зима
    public static final String[] MONTHS_RU_OF = {
            "март", "апрель", "май",
            "июнь", "июль", "август",
            "сентябрь", "октябрь", "ноябрь",
            "декабрь", "январь", "февраль"};
    public static final String[] MONTHS_EN = {
            "Berezen", "Tsveten", "Traven",
            "Cherven", "Lipen", "Serpen",
            "Veresen", "Listopad", "Gruden",
            "Studen", "Sechen", "Lyuten"};


    private Calendar() {}

    public enum Season {
        SPRING("Весна", "Spring"),
        SUMMER("Лето", "Summer"),
        AUTUMN("Осень", "Autumn"),
        WINTER("Зима", "Winter");

        public final String nameRu;
        public final String nameEn;

        Season(String nameRu, String nameEn) {
            this.nameRu = nameRu;
            this.nameEn = nameEn;
        }

        public String langKey() {
            return "calendar." + HalgventMod.MODID + ".season." + name().toLowerCase(java.util.Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable(langKey());
        }
    }

    /** Дата. Год — настоящий (1419...), месяц, число и день недели — с единицы. */
    public record Date(long day, int year, int month, int dayOfMonth, int weekday, int dayOfYear, Season season) {
        public Component weekdayName() {
            return Component.translatable(weekdayKey(weekday));
        }

        public Component monthName() {
            return Component.translatable(monthKey(month));
        }

        /** «травня» — для даты */
        public Component monthNameOf() {
            return Component.translatable(monthKey(month) + ".of");
        }
    }

    /** Событие, назначенное на дату командой /calendar event. */
    public record Event(String title, String description) {}

    /** Дата по номеру дня с начала мира (день 0 — начальная дата). */
    public static Date date(long day) {
        long safeDay = Math.max(day, 0);
        long fromYearStart = safeDay + START_OFFSET; // дней от 1-го числа 1-го месяца START_YEAR
        int dayOfYear = (int) (fromYearStart % DAYS_PER_YEAR);
        int monthIndex = dayOfYear / DAYS_PER_MONTH;
        return new Date(
                safeDay,
                START_YEAR + (int) (fromYearStart / DAYS_PER_YEAR),
                monthIndex + 1,
                dayOfYear % DAYS_PER_MONTH + 1,
                (int) (fromYearStart % DAYS_PER_WEEK) + 1,
                dayOfYear,
                Season.values()[monthIndex / MONTHS_PER_SEASON]);
    }

    /** Номер дня с начала мира по дате. Отрицательный — дата раньше начала мира. */
    public static long toDay(int year, int month, int dayOfMonth) {
        return (long) (year - START_YEAR) * DAYS_PER_YEAR
                + (long) (month - 1) * DAYS_PER_MONTH + (dayOfMonth - 1) - START_OFFSET;
    }

    /** Порядковый номер месяца, считая от первого месяца START_YEAR, — для листания. */
    public static int monthIndex(int year, int month) {
        return (year - START_YEAR) * MONTHS_PER_YEAR + (month - 1);
    }

    public static int dayOfYear(long day) {
        return (int) Math.floorMod(day + START_OFFSET, (long) DAYS_PER_YEAR);
    }

    /** «3 травня 1419 г.» */
    public static MutableComponent dateText(long day) {
        Date date = date(day);
        return Component.translatable("calendar." + HalgventMod.MODID + ".date",
                date.dayOfMonth(), date.monthNameOf(), date.year());
    }

    /** Какую долю суток занимает день: плавно от 1/3 зимой до 2/3 летом. */
    public static double dayShare(int dayOfYear) {
        double amplitude = DAY_SHARE_MAX - 0.5;
        return 0.5 + amplitude * Math.cos(2 * Math.PI * (dayOfYear - MIDSUMMER) / DAYS_PER_YEAR);
    }

    // ----- Текущий день и события на клиенте (приходят с сервера) -----

    public static volatile long clientDay;
    public static volatile Map<Long, List<Event>> clientEvents = Map.of();

    /** Номер текущего дня с начала мира — на сервере и на клиенте. */
    public static long today(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return WorldCalendar.get(serverLevel.getServer()).day();
        }
        return clientDay;
    }

    // ----- Ключи переводов -----

    public static String weekdayKey(int weekday) {
        return "calendar." + HalgventMod.MODID + ".weekday." + weekday;
    }

    public static String monthKey(int month) {
        return "calendar." + HalgventMod.MODID + ".month." + month;
    }
}