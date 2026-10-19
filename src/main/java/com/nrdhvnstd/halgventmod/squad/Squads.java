package com.nrdhvnstd.halgventmod.squad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Все отряды мира. Хранятся в верхнем мире и переживают перезапуск сервера.
 * Игрок состоит не больше чем в одном отряде, но приглашений может держать несколько — по одному от каждого отряда.
 * Приглашения не сохраняются и живут INVITE_TICKS.
 */
public final class Squads extends SavedData {
    public static final int INVITE_TICKS = 5 * 60 * 20; // пять минут

    private static final String NAME = HalgventMod.MODID + "_squads";
    private static final Factory<Squads> FACTORY = new Factory<>(Squads::new, Squads::load, null);

    private final Map<UUID, Squad> squads = new HashMap<>();   // по id отряда
    private final Map<UUID, Squad> byMember = new HashMap<>(); // по участнику
    // Приглашённый → (отряд → приглашение), в порядке получения
    private final Map<UUID, Map<UUID, Invite>> invites = new HashMap<>();

    public record Invite(UUID squad, UUID from, int expiresAt) {}

    public static Squads get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // ----- Чтение -----

    @Nullable
    public Squad of(UUID player) {
        return byMember.get(player);
    }

    public boolean sameSquad(UUID a, UUID b) {
        Squad squad = byMember.get(a);
        return squad != null && squad.members().contains(b);
    }

    @Nullable
    public Squad byName(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        for (Squad squad : squads.values()) {
            if (squad.name().toLowerCase(Locale.ROOT).equals(key)) {
                return squad;
            }
        }
        return null;
    }

    @Nullable
    public Squad squad(UUID id) {
        return squads.get(id);
    }

    public Collection<Squad> all() {
        return squads.values();
    }

    // ----- Изменения (через SquadService) -----

    Squad create(String name, UUID leader) {
        Squad squad = new Squad(UUID.randomUUID(), name, leader);
        squads.put(squad.id(), squad);
        byMember.put(leader, squad);
        invites.remove(leader);
        setDirty();
        return squad;
    }

    void join(Squad squad, UUID player) {
        squad.add(player);
        byMember.put(player, squad);
        invites.remove(player);
        setDirty();
    }

    void leave(Squad squad, UUID player) {
        squad.remove(player);
        byMember.remove(player);
        setDirty();
    }

    void disband(Squad squad) {
        for (UUID member : squad.members()) {
            byMember.remove(member);
        }
        squads.remove(squad.id());
        for (Map<UUID, Invite> list : invites.values()) {
            list.remove(squad.id());
        }
        setDirty();
    }

    /** Отметить изменение отряда (название, знамя, звания, настройки), чтобы мир сохранил его. */
    void changed() {
        setDirty();
    }

    // ----- Приглашения -----

    void invite(UUID player, Squad squad, UUID from, int now) {
        invites.computeIfAbsent(player, p -> new LinkedHashMap<>()).put(squad.id(), new Invite(squad.id(), from, now + INVITE_TICKS));
    }

    /** Действующие приглашения игрока (просроченные и от распущенных отрядов заодно удаляются). */
    public List<Invite> invites(UUID player, int now) {
        Map<UUID, Invite> list = invites.get(player);
        if (list == null) {
            return List.of();
        }
        list.values().removeIf(invite -> invite.expiresAt() < now || !squads.containsKey(invite.squad()));
        if (list.isEmpty()) {
            invites.remove(player);
            return List.of();
        }
        return new ArrayList<>(list.values());
    }

    @Nullable
    Invite invite(UUID player, UUID squad, int now) {
        for (Invite invite : invites(player, now)) {
            if (invite.squad().equals(squad)) {
                return invite;
            }
        }
        return null;
    }

    boolean hasInvite(UUID player, UUID squad, int now) {
        return invite(player, squad, now) != null;
    }

    void dropInvite(UUID player, UUID squad) {
        Map<UUID, Invite> list = invites.get(player);
        if (list != null) {
            list.remove(squad);
        }
    }

    // ----- Рассылка -----

    /** Сообщение всем участникам отряда, кто сейчас в игре. */
    public static void tell(MinecraftServer server, Squad squad, Component message) {
        for (UUID member : squad.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    // ----- Сохранение -----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Squad squad : squads.values()) {
            list.add(squad.save(registries));
        }
        tag.put("squads", list);
        return tag;
    }

    private static Squads load(CompoundTag tag, HolderLookup.Provider registries) {
        Squads data = new Squads();
        for (Tag entry : tag.getList("squads", Tag.TAG_COMPOUND)) {
            Squad squad = Squad.load((CompoundTag) entry, registries);
            data.squads.put(squad.id(), squad);
            for (UUID member : squad.members()) {
                data.byMember.put(member, squad);
            }
        }
        return data;
    }
}
