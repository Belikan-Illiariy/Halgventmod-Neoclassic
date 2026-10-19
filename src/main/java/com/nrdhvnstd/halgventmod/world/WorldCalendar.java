package com.nrdhvnstd.halgventmod.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.CalendarEventsPayload;
import com.nrdhvnstd.halgventmod.network.CalendarSyncPayload;
import com.nrdhvnstd.halgventmod.network.DawnPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Свой счётчик дней и события календаря, сохраняются вместе с миром.
 * День прибавляется на каждом рассвете — и обычном, и после сна.
 * Если время перевели назад в обход /calendar, календарь не откатывается; назад его переводит rewind.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public class WorldCalendar extends SavedData {
    public static final boolean SHOW_DAWN_TITLE = true; // надпись с датой на рассвете

    private static final String NAME = HalgventMod.MODID + "_calendar";
    private static final Factory<WorldCalendar> FACTORY = new Factory<>(WorldCalendar::new, WorldCalendar::load, null);

    private long day;                        // номер дня с начала мира, с нуля
    private long lastIndex = Long.MIN_VALUE; // номер ванильных суток, который видели последним
    private final Map<Long, List<Calendar.Event>> events = new TreeMap<>();

    public static WorldCalendar get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public long day() {
        return day;
    }

    public void setDay(long day) {
        this.day = Math.max(day, 0);
        setDirty();
    }

    /**
     * Перевести календарь назад вместе со временем мира (команды /calendar).
     * Сам по себе откат времени календарь не трогает, поэтому день и сутки ставим явно.
     */
    public void rewind(long day, long dayTime) {
        this.day = Math.max(day, 0);
        this.lastIndex = Math.floorDiv(dayTime, Calendar.TIME_PER_DAY);
        setDirty();
    }

    // ----- События -----

    public Map<Long, List<Calendar.Event>> events() {
        return Collections.unmodifiableMap(events);
    }

    public void addEvent(long day, Calendar.Event event) {
        events.computeIfAbsent(day, d -> new ArrayList<>()).add(event);
        setDirty();
    }

    /** Удалить все события на дату. false — их не было. */
    public boolean removeEvents(long day) {
        boolean removed = events.remove(day) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // ----- Ход дней -----

    /** Возвращает true, если наступил новый день. */
    private boolean update(long dayTime) {
        long index = Math.floorDiv(dayTime, Calendar.TIME_PER_DAY);
        if (lastIndex == Long.MIN_VALUE) {
            // Первый запуск: в уже существующем мире продолжаем с его текущего дня
            if (day == 0) {
                day = Math.max(index, 0);
            }
            lastIndex = index;
            setDirty();
            return false;
        }
        if (index > lastIndex) {
            day += index - lastIndex;
            lastIndex = index;
            setDirty();
            return true;
        }
        if (index < lastIndex) {
            lastIndex = index; // время перевели назад — календарь не трогаем
            setDirty();
        }
        return false;
    }

    // ----- Сохранение -----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("day", day);
        tag.putLong("last_index", lastIndex);
        ListTag list = new ListTag();
        events.forEach((eventDay, dayEvents) -> {
            for (Calendar.Event event : dayEvents) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("day", eventDay);
                entry.putString("title", event.title());
                entry.putString("description", event.description());
                list.add(entry);
            }
        });
        tag.put("events", list);
        return tag;
    }

    private static WorldCalendar load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldCalendar calendar = new WorldCalendar();
        calendar.day = tag.getLong("day");
        calendar.lastIndex = tag.contains("last_index") ? tag.getLong("last_index") : Long.MIN_VALUE;
        ListTag list = tag.getList("events", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            calendar.events.computeIfAbsent(entry.getLong("day"), d -> new ArrayList<>())
                    .add(new Calendar.Event(entry.getString("title"), entry.getString("description")));
        }
        return calendar;
    }

    // ----- Игровые события -----

    // LOW — после DayLength, который сначала поправляет ход времени
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        WorldCalendar calendar = get(server);
        if (calendar.update(server.overworld().getDayTime())) {
            PacketDistributor.sendToAllPlayers(new CalendarSyncPayload(calendar.day));
            if (SHOW_DAWN_TITLE) {
                announceDawn(server, calendar.day);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WorldCalendar calendar = get(player.server);
            PacketDistributor.sendToPlayer(player, new CalendarSyncPayload(calendar.day),
                    new CalendarEventsPayload(calendar.eventsCopy()));
        }
    }

    /** Отправить всем игрокам день и события — после команд. */
    public static void syncAll(MinecraftServer server) {
        WorldCalendar calendar = get(server);
        PacketDistributor.sendToAllPlayers(new CalendarSyncPayload(calendar.day),
                new CalendarEventsPayload(calendar.eventsCopy()));
    }

    private Map<Long, List<Calendar.Event>> eventsCopy() {
        Map<Long, List<Calendar.Event>> copy = new HashMap<>();
        events.forEach((eventDay, dayEvents) -> copy.put(eventDay, List.copyOf(dayEvents)));
        return copy;
    }

    // «Монтаг, 3 трент» крупно и «Весна, 1384 г.» под ней
    private static void announceDawn(MinecraftServer server, long day) {
        Calendar.Date date = Calendar.date(day);
        Component title = Component.translatable("calendar.halgventmod.dawn.title",
                date.weekdayName(), date.dayOfMonth(), date.monthNameOf()).withStyle(ChatFormatting.GOLD);
        Component subtitle = Component.translatable("calendar.halgventmod.dawn.subtitle",
                date.season().displayName(), date.year()).withStyle(ChatFormatting.GRAY);
        // Не ванильный заголовок, а надпись вверху экрана (Announcements) — в одной очереди с «Открытой локацией»
        PacketDistributor.sendToAllPlayers(new DawnPayload(title, subtitle));
    }
}