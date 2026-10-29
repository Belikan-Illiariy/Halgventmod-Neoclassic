package com.nrdhvnstd.halgventmod.npc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.social.SocialAction;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Правонарушения и розыск (docs/TZ_npc.md, 2.8). Правонарушение — удар по НПС (убийство тоже начинается с удара) и
 * враждебное действие над другим игроком (обыск, мешок, кандалы, наручники). Если его видит стражник —
 * в WITNESS_RANGE блоков и в прямой видимости игрока, — игрок объявляется в розыске на WANTED_TICKS: все стражники,
 * которые сами увидят его в этом радиусе, идут бить (PursueOffenderGoal). Если не видел никто — ничего не было.
 * Увидевший стражник зовёт на помощь всю стражу в 64 блоках (callForHelp): она бежит к месту (RespondToAlarmGoal).
 * Сбит нарушитель с ног, вышел или розыск кончился — розыск и тревога снимаются, стража возвращается на маршруты.
 * Розыск хранится в памяти сервера и со сбросом сервера пропадает.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Crimes {
    // Меняйте здесь
    public static final double WITNESS_RANGE = 40;  // блоков, радиус «стражник сам видит»
    private static final int WANTED_TICKS = 6000;   // ⚠️ 5 минут розыска; новое правонарушение продлевает

    private static final double ALARM_RANGE = 64;   // ⚠️ дальше этого стража зов не слышит
    private static final int ALARM_TICKS = 1200;    // ⚠️ 1 минута бега на тревогу
    private static final int ALARM_COOLDOWN = 200;  // не чаще раза в 10 секунд
    private static final float ALARM_VOLUME = 3.0F;
    private static final int CLEANUP_INTERVAL = 10;

    private static final Map<UUID, Long> WANTED = new HashMap<>();
    private static long lastCall = Long.MIN_VALUE / 2;

    private Crimes() {}

    /** Игрок сейчас в розыске. */
    public static boolean isWanted(Player player) {
        Long until = WANTED.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (player.level().getGameTime() >= until) {
            WANTED.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** Снять розыск: нарушитель наказан (стражник сбил его с ног) или прощён. */
    public static void pardon(Player player) {
        WANTED.remove(player.getUUID());
    }

    /** Есть ли сейчас хоть кто-то в розыске (иначе тревога не нужна). */
    public static boolean anyWanted() {
        return !WANTED.isEmpty();
    }

    // Зов на помощь: стражник, видевший правонарушение, зовёт всю стражу в ALARM_RANGE блоков — она бежит к месту,
    // даже если не видит нарушителя. Звон слышен вокруг; повторно звонят не чаще раза в ALARM_COOLDOWN тиков
    private static void callForHelp(ServerPlayer offender, HalgNpc caller) {
        long now = offender.level().getGameTime();
        if (now - lastCall < ALARM_COOLDOWN) {
            return;
        }
        lastCall = now;
        caller.level().playSound(null, caller.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, ALARM_VOLUME, 0.8F);
        for (HalgNpc guard : offender.level().getEntitiesOfClass(HalgNpc.class,
                caller.getBoundingBox().inflate(ALARM_RANGE),
                other -> other.getRole() == NpcRole.GUARD && other.isAlive() && other != caller
                        && other.distanceToSqr(caller) <= ALARM_RANGE * ALARM_RANGE)) {
            guard.raiseAlarm(offender.blockPosition(), now + ALARM_TICKS);
        }
    }

    // Раз в полсекунды: розыск кончился, игрок сбит с ног («Повержен») или вышел — снимаем. Стража возвращается на маршруты:
    // цель снимает PursueOffenderGoal, тревогу — RespondToAlarmGoal (она смотрит на anyWanted)
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (WANTED.isEmpty() || event.getServer().getTickCount() % CLEANUP_INTERVAL != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Long>> it = WANTED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || Downed.isDowned(player) || player.level().getGameTime() >= entry.getValue()) {
                it.remove();
            }
        }
    }

    /** Игрок совершил правонарушение: если его видит стражник — розыск, и видевшие идут за ним. */
    public static void committed(ServerPlayer offender) {
        if (offender.isSpectator()) {
            return;
        }
        List<HalgNpc> witnesses = offender.level().getEntitiesOfClass(HalgNpc.class,
                offender.getBoundingBox().inflate(WITNESS_RANGE),
                guard -> guard.getRole() == NpcRole.GUARD && guard.isAlive() && !guard.isSleeping()
                        && guard.distanceToSqr(offender) <= WITNESS_RANGE * WITNESS_RANGE
                        && guard.hasLineOfSight(offender));
        if (witnesses.isEmpty()) {
            return;
        }
        boolean fresh = !isWanted(offender);
        WANTED.put(offender.getUUID(), offender.level().getGameTime() + WANTED_TICKS);
        witnesses.forEach(guard -> guard.setTarget(offender));
        callForHelp(offender, witnesses.get(0));
        if (fresh) {
            offender.displayClientMessage(Component.translatable("npc.halgventmod.crime.seen"), true);
        }
    }

    /** Враждебные действия над игроком — правонарушение; перевязка, перенос и снятие кандалов — нет. */
    public static boolean hostile(SocialAction action) {
        return action == SocialAction.SEARCH || (action.restraint() != null && !action.removes());
    }

    // Удар по НПС (стражник, торговец, горожанин). Убийство — тот же удар, отдельно не нужно
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof HalgNpc && event.getSource().getEntity() instanceof ServerPlayer player) {
            committed(player);
        }
    }

    // Смертельный удар тоже виден: бывает, что урон добивает раньше, чем отработает Post
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof HalgNpc && event.getSource().getEntity() instanceof ServerPlayer player) {
            committed(player);
        }
    }
}
