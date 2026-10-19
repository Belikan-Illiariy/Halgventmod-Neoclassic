package com.nrdhvnstd.halgventmod.squad;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.name.RpNames;
import com.nrdhvnstd.halgventmod.network.SquadMembersPayload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Отряды в игре: нет урона по своим; позиции товарищей для карты — тем, кому режим карты отряда это разрешает. */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class SquadEvents {
    private static final int SYNC_INTERVAL = 10; // тиков: метки на карте обновляются дважды в секунду

    private static final Set<UUID> sentMarkers = new HashSet<>(); // кому последним ушёл непустой список
    private static boolean syncNow;

    private SquadEvents() {}

    /** Состав отряда или его настройки изменились — обновить метки на следующем тике, не дожидаясь интервала. */
    static void syncSoon() {
        syncNow = true;
    }

    // Удар, стрела, зелье и прочее от товарища по отряду не проходит (вместе с отбрасыванием), если лидер не разрешил
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim
                && event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker != victim
                && !friendlyFireAllowed(victim, attacker)) {
            event.setCanceled(true);
        }
    }

    // Не товарищи — урон проходит как обычно; товарищи — только если в отряде разрешён урон по своим
    private static boolean friendlyFireAllowed(ServerPlayer victim, ServerPlayer attacker) {
        Squad squad = Squads.get(victim.server).of(victim.getUUID());
        return squad == null || !squad.members().contains(attacker.getUUID()) || squad.friendlyFire();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!syncNow && server.getTickCount() % SYNC_INTERVAL != 0) {
            return;
        }
        syncNow = false;
        Squads squads = Squads.get(server);
        Set<UUID> sent = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Squad squad = squads.of(player.getUUID());
            List<SquadMembersPayload.Member> members = squad == null || !squad.seesOnMap(player.getUUID())
                    ? List.of() : visibleMembers(server, squad, player);
            if (!members.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new SquadMembersPayload(members));
                sent.add(player.getUUID());
            } else if (sentMarkers.contains(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new SquadMembersPayload(List.of())); // убрать старые метки
            }
        }
        sentMarkers.clear();
        sentMarkers.addAll(sent);
    }

    // Товарищи в игре и в том же измерении, кроме самого игрока
    private static List<SquadMembersPayload.Member> visibleMembers(MinecraftServer server, Squad squad, ServerPlayer viewer) {
        List<SquadMembersPayload.Member> members = new ArrayList<>();
        for (UUID id : squad.members()) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null && member != viewer && member.level() == viewer.level()) {
                String name = RpNames.get(member);
                members.add(new SquadMembersPayload.Member(id,
                        name.isEmpty() ? member.getGameProfile().getName() : name,
                        member.getX(), member.getZ(), member.getYRot()));
            }
        }
        return members;
    }
}
