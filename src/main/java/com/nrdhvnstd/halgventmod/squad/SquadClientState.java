package com.nrdhvnstd.halgventmod.squad;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.network.SquadActionPayload;
import com.nrdhvnstd.halgventmod.network.SquadStatePayload;

import net.minecraft.network.chat.Component;

/**
 * На клиенте: последнее состояние отряда, присланное сервером (SquadStatePayload), для окна «Отряд».
 * Открытое окно отряда подписывается как listener и узнаёт о новом состоянии и об ответах на свои действия.
 */
public final class SquadClientState {
    public interface Listener {
        default void squadStateChanged() {}

        default void squadResult(SquadActionPayload.Action action, @Nullable Component error) {}
    }

    @Nullable
    public static SquadStatePayload.SquadView squad;
    public static List<SquadStatePayload.InviteView> invites = List.of();
    public static List<SquadStatePayload.PlayerView> invitable = List.of();
    @Nullable
    public static Listener listener;

    private SquadClientState() {}

    public static void update(SquadStatePayload payload) {
        squad = payload.squad();
        invites = payload.invites();
        invitable = payload.invitable();
        if (listener != null) {
            listener.squadStateChanged();
        }
    }

    public static void result(SquadActionPayload.Action action, @Nullable Component error) {
        if (listener != null) {
            listener.squadResult(action, error);
        }
    }

    public static boolean isLeader(UUID player) {
        return squad != null && squad.leader().equals(player);
    }

    /** При выходе из мира — забыть всё. */
    public static void clear() {
        squad = null;
        invites = List.of();
        invitable = List.of();
        listener = null;
    }
}
