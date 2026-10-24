package com.nrdhvnstd.halgventmod.smithing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.network.SmithOpenPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Мини-игры кузнечества: сервер открывает окно игры ({@link #open}) и запоминает, что, где и когда начато;
 * итог (SmithResultPayload) принимается, только если окно было открыто здесь и прошло не меньше
 * {@code minTicks} — клиенту доверяем лишь счёт. Итог уходит хозяину — блок-сущности или блоку ({@link Host}).
 */
public final class Minigame {
    // Виды игр
    public static final int ANVIL = 0;       // этапы изделия на наковальне (product — номер изделия)
    public static final int CASTING = 1;     // заливка формы из тигля (product — номер отливки)
    public static final int BLOOMERY = 2;    // мехи сыродутного горна (product — OreKind)
    public static final int CUPEL = 3;       // купель: поймать проблеск серебра
    public static final int MORTAR = 4;      // толочь в ступе (product — что толкут)
    public static final int PAN_PLACER = 5;  // лоток на россыпи
    public static final int PAN_SLUICE = 6;  // лоток в желобе с толчёной рудой
    public static final int SHARPEN = 7;     // подточить на точиле
    public static final int STRIKER = 8;     // молотобоец у наковальни
    public static final int FORGE_MELT = 9;  // плавка в тигле на кузнечном горне (product — Metal)
    public static final int GLASS = 10;      // стеклодув у печи (product — Glassmaking.Form, info — жар печи)

    /** Кто принимает итог: блок-сущность станции или сам блок (россыпь, точило). */
    public interface Host {
        void minigameResult(ServerPlayer player, Session session, boolean done, List<Integer> data);
    }

    /** Начатая игра. */
    public record Session(BlockPos pos, int kind, int product, long started, int minTicks, int info, int extra) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Minigame() {}

    /**
     * Открыть игроку окно игры. info и extra — что окну надо знать (жар горна, жидкость в бочке, богатство
     * россыпи…), их смысл — у каждого вида свой.
     */
    public static void open(ServerPlayer player, BlockPos pos, int kind, int product, int minTicks, int info, int extra) {
        SESSIONS.put(player.getUUID(), new Session(pos.immutable(), kind, product, player.serverLevel().getGameTime(),
                minTicks, info, extra));
        PacketDistributor.sendToPlayer(player, new SmithOpenPayload(pos, kind, product, player.getRandom().nextInt(),
                info, extra));
    }

    @Nullable
    public static Session current(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /** Кто сейчас работает у этого места (для молотобойца); null — никто. */
    @Nullable
    public static UUID workingAt(BlockPos pos, int kind) {
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            if (entry.getValue().kind() == kind && entry.getValue().pos().equals(pos)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static void forget(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** Итог пришёл: проверить, что окно открывали здесь, и отдать хозяину. */
    public static void result(ServerPlayer player, BlockPos pos, int kind, boolean done, List<Integer> data) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.kind() != kind || !session.pos().equals(pos)) {
            return;
        }
        SESSIONS.remove(player.getUUID());
        boolean early = done && player.serverLevel().getGameTime() - session.started() < session.minTicks();
        if (player.distanceToSqr(pos.getCenter()) > 64) {
            return;
        }
        Host host = player.level().getBlockEntity(pos) instanceof Host be ? be
                : player.level().getBlockState(pos).getBlock() instanceof Host block ? block : null;
        if (host != null) {
            // Слишком быстро — подделка или сбой: как будто бросили
            host.minigameResult(player, session, done && !early, early ? List.of() : data);
        }
    }

    /** Число из данных итога, 0–100; нет такого — 0. */
    public static int score(List<Integer> data, int index) {
        return index < data.size() ? Math.clamp(data.get(index), 0, 100) : 0;
    }

    /** Число из данных итога как есть; нет такого — fallback. */
    public static int value(List<Integer> data, int index, int fallback) {
        return index < data.size() ? data.get(index) : fallback;
    }
}
