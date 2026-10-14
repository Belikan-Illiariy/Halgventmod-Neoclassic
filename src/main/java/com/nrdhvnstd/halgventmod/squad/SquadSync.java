package com.nrdhvnstd.halgventmod.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.name.RpNames;
import com.nrdhvnstd.halgventmod.network.SquadStatePayload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Состояние окна «Отряд» на клиентах. Любое изменение (отряды, приглашения, вход и выход игроков) помечается,
 * и в конце тика всем игрокам уходит свежее состояние — одним пакетом на игрока, сколько бы изменений ни было.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class SquadSync {
    private static boolean dirty;

    private SquadSync() {}

    /** Что-то в отрядах изменилось — разослать состояние в конце тика. */
    public static void changed(MinecraftServer server) {
        dirty = true;
        SquadEvents.syncSoon(); // и метки на карте
    }

    /** Разослать сразу, если есть изменения (перед ответом на действие из окна). */
    public static void flush(MinecraftServer server) {
        if (dirty) {
            dirty = false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                send(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        flush(event.getServer());
    }

    // Вход и выход меняют «в игре» у товарищей и список тех, кого можно пригласить
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            changed(player.server);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            changed(player.server);
        }
    }

    /** Состояние для одного игрока. */
    public static void send(ServerPlayer player) {
        MinecraftServer server = player.server;
        Squads squads = Squads.get(server);
        int now = server.getTickCount();
        Squad squad = squads.of(player.getUUID());

        SquadStatePayload.SquadView view = null;
        List<SquadStatePayload.PlayerView> invitable = new ArrayList<>();
        if (squad != null) {
            List<SquadStatePayload.MemberView> members = new ArrayList<>();
            for (UUID id : squad.members()) {
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                String name = SquadService.profileName(server, id);
                members.add(new SquadStatePayload.MemberView(id, name != null ? name : "?",
                        online != null ? RpNames.get(online) : "", squad.rank(id), online != null));
            }
            view = new SquadStatePayload.SquadView(squad.id(), squad.name(), squad.bannerBase(),
                    squad.bannerPatterns(), squad.leader(), squad.friendlyFire(), squad.mapMode(),
                    List.copyOf(squad.mapRanks()), members);
            if (squad.isLeader(player.getUUID())) {
                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                    if (squads.of(other.getUUID()) == null) {
                        invitable.add(new SquadStatePayload.PlayerView(other.getUUID(),
                                other.getGameProfile().getName(), squads.hasInvite(other.getUUID(), squad.id(), now)));
                    }
                }
            }
        }

        List<SquadStatePayload.InviteView> invites = new ArrayList<>();
        if (squad == null) {
            for (Squads.Invite invite : squads.invites(player.getUUID(), now)) {
                Squad from = squads.squad(invite.squad());
                String inviter = SquadService.profileName(server, invite.from());
                if (from != null) {
                    invites.add(new SquadStatePayload.InviteView(from.id(), from.name(), inviter != null ? inviter : "?"));
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new SquadStatePayload(view, invites, invitable));
    }
}
