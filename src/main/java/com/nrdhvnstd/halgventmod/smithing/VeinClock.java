package com.nrdhvnstd.halgventmod.smithing;

import java.util.HashMap;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Когда к жиле или россыпи вернётся следующая ступень богатства — по календарю мира ({@link Smithing#now}, ночь во
 * сне тоже идёт). Срок каждой ступени свой: средние сутки (OreKind.regrow) ± {@link #SPREAD}. Считается, даже если
 * чанк всё это время не был загружен: догоняем при обращении к блоку ({@link #catchUp}), на запланированном тике
 * ({@link #schedule} — работает и без случайных тиков) и на случайном. Полная
 * жила из списка уходит.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = HalgventMod.MODID)
public class VeinClock extends SavedData {
    private static final String NAME = HalgventMod.MODID + "_vein_clock";
    private static final Factory<VeinClock> FACTORY = new Factory<>(VeinClock::new, VeinClock::load, null);
    public static final double SPREAD = 0.5;   // ⚠️ срок ступени — от половины до полутора средних
    public static final int RECHECK = 1200;    // тиков: не реже раза в минуту проверять обедневшую жилу

    private final Map<Long, Long> due = new HashMap<>();   // когда вернётся следующая ступень

    private static VeinClock of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Срок ступени: days суток ± {@link #SPREAD}, каждый раз свой. */
    private static long period(ServerLevel level, double days) {
        double factor = 1 - SPREAD + level.random.nextDouble() * 2 * SPREAD;
        return Math.max(1, Math.round(days * factor * Calendar.TIME_PER_DAY));
    }

    /**
     * Вернуть ступени, чей срок прошёл (по {@code days} суток на ступень в среднем), и вернуть новое состояние.
     * Жила не полная и срока нет (обеднела до этого обновления) — срок назначается сейчас.
     */
    public static BlockState catchUp(ServerLevel level, BlockPos pos, BlockState state, double days) {
        int richness = state.getValue(OreVeinBlock.RICHNESS);
        VeinClock clock = of(level);
        long key = pos.asLong();
        long now = Smithing.now(level);
        if (richness >= 3) {
            if (clock.due.remove(key) != null) {
                clock.setDirty();
            }
            return state;
        }
        Long next = clock.due.get(key);
        if (next == null) {
            clock.due.put(key, now + period(level, days));
            clock.setDirty();
            return state;
        }
        int grown = richness;
        long at = next;
        while (grown < 3 && now >= at) {
            grown++;
            if (grown < 3) {
                at += period(level, days);
            }
        }
        if (grown == richness) {
            return state;
        }
        if (grown >= 3) {
            clock.due.remove(key);
        } else {
            clock.due.put(key, at);
        }
        clock.setDirty();
        BlockState updated = state.setValue(OreVeinBlock.RICHNESS, grown);
        level.setBlock(pos, updated, 3);
        return updated;
    }

    /** Жила обеднела: если была полной — срок первой ступени назначается сейчас; уже шёл — идёт дальше. */
    public static void depleted(ServerLevel level, BlockPos pos, double days) {
        VeinClock clock = of(level);
        if (!clock.due.containsKey(pos.asLong())) {
            clock.due.put(pos.asLong(), Smithing.now(level) + period(level, days));
            clock.setDirty();
        }
    }

    /**
     * Не полной жиле — запланированный тик к сроку следующей ступени (не позже {@link #RECHECK} тиков: сутки во
     * сне проходят быстрее тиков). Так жила меняет вид сама, даже если случайные тики выключены (randomTickSpeed 0).
     */
    public static void schedule(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block block) {
        Long next = of(level).due.get(pos.asLong());
        long left = next == null ? RECHECK : (next - Smithing.now(level))
                * com.nrdhvnstd.halgventmod.world.DayLength.DAY_LENGTH_TICKS / Calendar.TIME_PER_DAY;
        level.scheduleTick(pos, block, (int) Math.max(20, Math.min(RECHECK, left + 1)));
    }

    /**
     * Раз в {@link #RECHECK} тиков: все обедневшие жилы в загруженных чанках, чей срок прошёл, догоняют календарь —
     * и те, у которых нет запланированного тика (обеднели до этого обновления, мир грузился без них).
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % RECHECK != 0) {
            return;
        }
        VeinClock clock = of(level);
        if (clock.due.isEmpty()) {
            return;
        }
        long now = Smithing.now(level);
        for (Map.Entry<Long, Long> entry : new HashMap<>(clock.due).entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (entry.getValue() > now || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof OreVeinBlock vein) {
                catchUp(level, pos, state, vein.kind().regrow());
            } else if (state.getBlock() instanceof GoldPlacerBlock) {
                catchUp(level, pos, state, GoldPlacerBlock.REGROW);
            } else {
                clock.due.remove(entry.getKey());
                clock.setDirty();
            }
        }
    }

    /** Запланированный тик: догнать календарь и, если ещё не полная, запланировать следующий. */
    public static void tick(ServerLevel level, BlockPos pos, BlockState state, double days) {
        BlockState now = catchUp(level, pos, state, days);
        if (now.getValue(OreVeinBlock.RICHNESS) < 3) {
            schedule(level, pos, now.getBlock());
        }
    }

    /** Сколько суток до следующей ступени; 0 — полная или вот-вот. */
    public static double daysToNext(ServerLevel level, BlockPos pos) {
        Long next = of(level).due.get(pos.asLong());
        return next == null ? 0 : Math.max(0, next - Smithing.now(level)) / (double) Calendar.TIME_PER_DAY;
    }

    /** «через 1,5 сут.» / «через 6 ч» — сколько ждать следующей ступени (для сообщений). */
    public static net.minecraft.network.chat.Component until(ServerLevel level, BlockPos pos) {
        double left = daysToNext(level, pos);
        if (left < 1) {
            return net.minecraft.network.chat.Component.translatable("message.halgventmod.smithing.wait_hours",
                    Math.max(1, (int) Math.ceil(left * 24)));
        }
        return net.minecraft.network.chat.Component.translatable("message.halgventmod.smithing.wait_days",
                String.format(java.util.Locale.ROOT, "%.1f", left).replace(".0", "").replace('.', ','));
    }

    /** Блок убрали — забыть. */
    public static void forget(ServerLevel level, BlockPos pos) {
        VeinClock clock = of(level);
        if (clock.due.remove(pos.asLong()) != null) {
            clock.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] keys = new long[due.size()];
        long[] times = new long[due.size()];
        int i = 0;
        for (Map.Entry<Long, Long> entry : due.entrySet()) {
            keys[i] = entry.getKey();
            times[i] = entry.getValue();
            i++;
        }
        tag.putLongArray("Pos", keys);
        tag.putLongArray("Due", times);
        return tag;
    }

    private static VeinClock load(CompoundTag tag, HolderLookup.Provider registries) {
        VeinClock clock = new VeinClock();
        long[] keys = tag.getLongArray("Pos");
        long[] times = tag.getLongArray("Due");
        for (int i = 0; i < Math.min(keys.length, times.length); i++) {
            clock.due.put(keys[i], times[i]);
        }
        return clock;
    }
}
