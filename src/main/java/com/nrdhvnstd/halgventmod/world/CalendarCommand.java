package com.nrdhvnstd.halgventmod.world;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды календаря. Время двигается вместе с освещением — вперёд и назад.
 *
 * /calendar                                            — сегодняшняя дата и длина дня
 * /calendar time dawn|noon|dusk|midnight               — вперёд до ближайшего рассвета, полудня, заката, полуночи
 * /calendar time <час> [минута]                        — вперёд до ближайшего указанного времени (игровые часы)
 * /calendar skip <дней>                                — на столько суток (меньше нуля — назад), время суток то же
 * /calendar goto <год> <месяц> <число> [час] [минута]  — на дату, в будущее или в прошлое (по умолчанию — рассвет)
 * /calendar event add <год> <месяц> <число> <текст>    — событие на дату; «Название | Пояснение»
 * /calendar event remove <год> <месяц> <число>         — удалить все события на дату
 * Всё, кроме просмотра, — для операторов. Ванильная /time set отключена: она сбила бы календарь.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class CalendarCommand {
    // Время суток в единицах игрового времени: 0 = 06:00, сутки = 24 000
    private static final int DAWN = 0;         // 06:00
    private static final int NOON = 6000;      // 12:00
    private static final int DUSK = 12000;     // 18:00
    private static final int MIDNIGHT = 18000; // 00:00

    private CalendarCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("calendar")
                .executes(context -> show(context.getSource()))
                .then(Commands.literal("time")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("dawn").executes(context -> toTimeOfDay(context.getSource(), DAWN)))
                        .then(Commands.literal("noon").executes(context -> toTimeOfDay(context.getSource(), NOON)))
                        .then(Commands.literal("dusk").executes(context -> toTimeOfDay(context.getSource(), DUSK)))
                        .then(Commands.literal("midnight").executes(context -> toTimeOfDay(context.getSource(), MIDNIGHT)))
                        .then(hour()
                                .executes(context -> toTimeOfDay(context.getSource(), clock(context, false)))
                                .then(minute()
                                        .executes(context -> toTimeOfDay(context.getSource(), clock(context, true))))))
                .then(Commands.literal("skip")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("days", IntegerArgumentType.integer())
                                .executes(context -> skipDays(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "days")))))
                .then(Commands.literal("goto")
                        .requires(source -> source.hasPermission(2))
                        .then(year().then(month().then(day()
                                .executes(context -> goTo(context, DAWN))
                                .then(hour()
                                        .executes(context -> goTo(context, clock(context, false)))
                                        .then(minute()
                                                .executes(context -> goTo(context, clock(context, true)))))))))
                .then(Commands.literal("event")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(year().then(month().then(day()
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(CalendarCommand::addEvent))))))
                        .then(Commands.literal("remove")
                                .then(year().then(month().then(day().executes(CalendarCommand::removeEvents)))))));
    }

    // ----- Запрет /time set: время переводим только через /calendar, чтобы не сбить календарь -----

    @SubscribeEvent
    public static void blockTimeSet(CommandEvent event) {
        // Проверяем и саму команду, и вложенные (/execute ... run time set)
        CommandContextBuilder<CommandSourceStack> context = event.getParseResults().getContext();
        while (context != null) {
            List<ParsedCommandNode<CommandSourceStack>> nodes = context.getNodes();
            if (nodes.size() >= 2
                    && nodes.get(0).getNode().getName().equals("time")
                    && nodes.get(1).getNode().getName().equals("set")) {
                event.setCanceled(true);
                context.getSource().sendFailure(Component.translatable("calendar.halgventmod.command.time_set_blocked"));
                return;
            }
            context = context.getChild();
        }
    }

    // ----- Аргументы -----

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> year() {
        return Commands.argument("year", IntegerArgumentType.integer(Calendar.START_YEAR));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> month() {
        return Commands.argument("month", IntegerArgumentType.integer(1, Calendar.MONTHS_PER_YEAR));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> day() {
        return Commands.argument("day", IntegerArgumentType.integer(1, Calendar.DAYS_PER_MONTH));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> hour() {
        return Commands.argument("hour", IntegerArgumentType.integer(0, 23));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> minute() {
        return Commands.argument("minute", IntegerArgumentType.integer(0, 59));
    }

    /** Часы и минуты игровых часов → время суток (06:00 = 0). */
    private static int clock(CommandContext<CommandSourceStack> context, boolean withMinute) {
        int hour = IntegerArgumentType.getInteger(context, "hour");
        int minute = withMinute ? IntegerArgumentType.getInteger(context, "minute") : 0;
        return Math.floorMod(hour - 6, 24) * 1000 + minute * 1000 / 60;
    }

    /** Время суток → «12:00». */
    private static String clockText(long dayTime) {
        long time = Math.floorMod(dayTime, (long) Calendar.TIME_PER_DAY);
        long hour = (time / 1000 + 6) % 24;
        long minute = time % 1000 * 60 / 1000;
        return String.format("%02d:%02d", hour, minute);
    }

    /** Номер дня из аргументов; -1 и сообщение об ошибке, если дата раньше начала мира. */
    private static long readDay(CommandContext<CommandSourceStack> context) {
        long day = Calendar.toDay(
                IntegerArgumentType.getInteger(context, "year"),
                IntegerArgumentType.getInteger(context, "month"),
                IntegerArgumentType.getInteger(context, "day"));
        if (day < 0) {
            context.getSource().sendFailure(Component.translatable("calendar.halgventmod.command.too_early",
                    Calendar.dateText(0)));
            return -1;
        }
        return day;
    }

    // ----- Просмотр -----

    private static int show(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Calendar.Date date = Calendar.date(WorldCalendar.get(server).day());
        String time = clockText(server.overworld().getDayTime());
        source.sendSuccess(() -> Component.translatable("calendar.halgventmod.command.date",
                date.weekdayName(), date.dayOfMonth(), date.monthNameOf(), date.year(),
                date.season().displayName(), time), false);

        int dayMinutes = (int) Math.round(Calendar.dayShare(date.dayOfYear()) * DayLength.DAY_LENGTH_TICKS / 1200.0);
        int nightMinutes = DayLength.DAY_LENGTH_TICKS / 1200 - dayMinutes;
        source.sendSuccess(() -> Component.translatable("calendar.halgventmod.command.daylight",
                dayMinutes / 60, dayMinutes % 60, nightMinutes / 60, nightMinutes % 60), false);
        return 1;
    }

    // ----- Перевод времени -----

    /** До ближайшего такого времени суток: если сегодня оно уже прошло — до завтрашнего. */
    private static int toTimeOfDay(CommandSourceStack source, int timeOfDay) {
        long now = source.getServer().overworld().getDayTime();
        long target = Math.floorDiv(now, (long) Calendar.TIME_PER_DAY) * Calendar.TIME_PER_DAY + timeOfDay;
        if (target <= now) {
            target += Calendar.TIME_PER_DAY;
        }
        return jump(source, target);
    }

    private static int skipDays(CommandSourceStack source, int days) {
        long now = source.getServer().overworld().getDayTime();
        return jump(source, now + (long) days * Calendar.TIME_PER_DAY);
    }

    private static int goTo(CommandContext<CommandSourceStack> context, int timeOfDay) {
        long targetDay = readDay(context);
        if (targetDay < 0) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        long now = source.getServer().overworld().getDayTime();
        long today = WorldCalendar.get(source.getServer()).day();
        long target = (Math.floorDiv(now, (long) Calendar.TIME_PER_DAY) + (targetDay - today)) * Calendar.TIME_PER_DAY
                + timeOfDay;
        return jump(source, target);
    }

    /**
     * Переставляем время верхнего мира. Солнце, луна и освещение встают на место сами.
     * Вперёд — календарь на следующем тике отсчитает пройденные рассветы и сообщит игрокам дату;
     * назад — переводим календарь сразу и рассылаем игрокам новый день.
     */
    private static int jump(CommandSourceStack source, long target) {
        MinecraftServer server = source.getServer();
        WorldCalendar calendar = WorldCalendar.get(server);
        long now = server.overworld().getDayTime();
        long newDay = calendar.day()
                + Math.floorDiv(target, (long) Calendar.TIME_PER_DAY) - Math.floorDiv(now, (long) Calendar.TIME_PER_DAY);
        if (newDay < 0) {
            source.sendFailure(Component.translatable("calendar.halgventmod.command.too_early", Calendar.dateText(0)));
            return 0;
        }
        server.overworld().setDayTime(target);
        boolean back = target < now;
        if (back) {
            calendar.rewind(newDay, target);
            WorldCalendar.syncAll(server);
        }
        source.sendSuccess(() -> Component.translatable(back
                        ? "calendar.halgventmod.command.rewound"
                        : "calendar.halgventmod.command.jumped",
                Calendar.dateText(newDay), clockText(target)), true);
        return 1;
    }

    // ----- События -----

    private static int addEvent(CommandContext<CommandSourceStack> context) {
        long day = readDay(context);
        if (day < 0) {
            return 0;
        }
        // «Ярмарка | Большая осенняя ярмарка в Лотангаре» → название и пояснение
        String text = StringArgumentType.getString(context, "text");
        int bar = text.indexOf('|');
        String title = (bar >= 0 ? text.substring(0, bar) : text).trim();
        String description = bar >= 0 ? text.substring(bar + 1).trim() : "";

        WorldCalendar.get(context.getSource().getServer()).addEvent(day, new Calendar.Event(title, description));
        WorldCalendar.syncAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("calendar.halgventmod.command.event_added",
                Calendar.dateText(day), title), true);
        return 1;
    }

    private static int removeEvents(CommandContext<CommandSourceStack> context) {
        long day = readDay(context);
        if (day < 0) {
            return 0;
        }
        if (!WorldCalendar.get(context.getSource().getServer()).removeEvents(day)) {
            context.getSource().sendFailure(Component.translatable("calendar.halgventmod.command.no_events",
                    Calendar.dateText(day)));
            return 0;
        }
        WorldCalendar.syncAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("calendar.halgventmod.command.events_removed",
                Calendar.dateText(day)), true);
        return 1;
    }
}