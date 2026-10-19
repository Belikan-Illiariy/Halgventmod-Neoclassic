package com.nrdhvnstd.halgventmod.squad;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.nrdhvnstd.halgventmod.chat.ChatChannels;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Действия с отрядами — общие для команд (/squad) и окна «Отряд».
 * Каждое действие проверяет права и данные; при отказе возвращает причину (её покажут игроку), при успехе — null.
 * Об изменениях узнают участники отряда (сообщением в чат) и окна отрядов у всех игроков (SquadSync).
 */
public final class SquadService {
    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 24;
    public static final int RANK_MAX = 24;
    public static final int BANNER_MAX_LAYERS = 6; // как у ткацкого станка

    private SquadService() {}

    // ----- Создание и вступление -----

    @Nullable
    public static Component create(ServerPlayer player, String rawName, DyeColor base, BannerPatternLayers patterns) {
        Squads squads = Squads.get(player.server);
        Squad current = squads.of(player.getUUID());
        if (current != null) {
            return Component.translatable("squad.halgventmod.already_in", current.name());
        }
        String name = normalize(rawName);
        Component nameError = checkName(squads, name, null);
        if (nameError != null) {
            return nameError;
        }
        if (patterns.layers().size() > BANNER_MAX_LAYERS) {
            return Component.translatable("squad.halgventmod.banner_too_many", BANNER_MAX_LAYERS);
        }
        Squad squad = squads.create(name, player.getUUID());
        squad.setBanner(base, patterns);
        // Надпись на экране — как о новой дате: серым «Создан отряд», под ним название золотом
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(name).withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("squad.halgventmod.created_title").withStyle(ChatFormatting.GRAY)));
        SquadSync.changed(player.server);
        return null;
    }

    @Nullable
    public static Component invite(ServerPlayer leader, ServerPlayer target) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireLeader(squad, leader);
        if (error != null) {
            return error;
        }
        if (target == leader) {
            return Component.translatable("squad.halgventmod.invite_self");
        }
        if (squads.of(target.getUUID()) != null) {
            return Component.translatable("squad.halgventmod.target_in_squad", ChatChannels.name(target));
        }
        squads.invite(target.getUUID(), squad, leader.getUUID(), leader.server.getTickCount());
        leader.sendSystemMessage(Component.translatable("squad.halgventmod.invite_sent", ChatChannels.name(target)));
        target.sendSystemMessage(Component.translatable("squad.halgventmod.invited", ChatChannels.name(leader), squad.name())
                .withStyle(ChatFormatting.GREEN)
                .append(" ")
                .append(button("squad.halgventmod.button.accept", "/squad accept " + squad.name(), ChatFormatting.GREEN))
                .append(" ")
                .append(button("squad.halgventmod.button.decline", "/squad decline " + squad.name(), ChatFormatting.RED)));
        SquadSync.changed(leader.server);
        return null;
    }

    @Nullable
    public static Component accept(ServerPlayer player, UUID squadId) {
        Squads squads = Squads.get(player.server);
        Squad current = squads.of(player.getUUID());
        if (current != null) {
            return Component.translatable("squad.halgventmod.already_in", current.name());
        }
        Squad squad = squads.squad(squadId);
        if (squad == null || !squads.hasInvite(player.getUUID(), squadId, player.server.getTickCount())) {
            return Component.translatable("squad.halgventmod.no_invite");
        }
        squads.join(squad, player.getUUID());
        Squads.tell(player.server, squad, Component.translatable("squad.halgventmod.joined",
                ChatChannels.name(player), squad.name()).withStyle(ChatFormatting.GREEN));
        SquadSync.changed(player.server);
        return null;
    }

    @Nullable
    public static Component decline(ServerPlayer player, UUID squadId) {
        Squads squads = Squads.get(player.server);
        Squads.Invite invite = squads.invite(player.getUUID(), squadId, player.server.getTickCount());
        if (invite == null) {
            return Component.translatable("squad.halgventmod.no_invite");
        }
        squads.dropInvite(player.getUUID(), squadId);
        ServerPlayer inviter = player.server.getPlayerList().getPlayer(invite.from());
        if (inviter != null) {
            inviter.sendSystemMessage(Component.translatable("squad.halgventmod.declined", ChatChannels.name(player)));
        }
        SquadSync.changed(player.server);
        return null;
    }

    // ----- Состав -----

    @Nullable
    public static Component leave(ServerPlayer player) {
        Squads squads = Squads.get(player.server);
        Squad squad = squads.of(player.getUUID());
        if (squad == null) {
            return Component.translatable("squad.halgventmod.not_in");
        }
        if (squad.isLeader(player.getUUID())) {
            if (squad.members().size() > 1) {
                return Component.translatable("squad.halgventmod.leader_cant_leave");
            }
            return disband(player);
        }
        squads.leave(squad, player.getUUID());
        player.sendSystemMessage(Component.translatable("squad.halgventmod.you_left", squad.name()));
        Squads.tell(player.server, squad, Component.translatable("squad.halgventmod.left", ChatChannels.name(player))
                .withStyle(ChatFormatting.YELLOW));
        SquadSync.changed(player.server);
        return null;
    }

    @Nullable
    public static Component kick(ServerPlayer leader, UUID target) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireMember(squad, leader, target);
        if (error != null) {
            return error;
        }
        if (target.equals(leader.getUUID())) {
            return Component.translatable("squad.halgventmod.kick_self");
        }
        Component name = memberName(leader.server, target);
        squads.leave(squad, target);
        Squads.tell(leader.server, squad, Component.translatable("squad.halgventmod.kicked", name)
                .withStyle(ChatFormatting.YELLOW));
        ServerPlayer kicked = leader.server.getPlayerList().getPlayer(target);
        if (kicked != null) {
            kicked.sendSystemMessage(Component.translatable("squad.halgventmod.you_kicked", squad.name())
                    .withStyle(ChatFormatting.YELLOW));
        }
        SquadSync.changed(leader.server);
        return null;
    }

    @Nullable
    public static Component makeLeader(ServerPlayer leader, UUID target) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireMember(squad, leader, target);
        if (error != null) {
            return error;
        }
        if (target.equals(leader.getUUID())) {
            return null;
        }
        squad.setLeader(target);
        squad.setRank(target, ""); // у лидера своё звание — «Лидер»
        squads.changed();
        Squads.tell(leader.server, squad, Component.translatable("squad.halgventmod.new_leader",
                memberName(leader.server, target)).withStyle(ChatFormatting.GREEN));
        SquadSync.changed(leader.server);
        return null;
    }

    @Nullable
    public static Component disband(ServerPlayer leader) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireLeader(squad, leader);
        if (error != null) {
            return error;
        }
        Squads.tell(leader.server, squad, Component.translatable("squad.halgventmod.disbanded", squad.name())
                .withStyle(ChatFormatting.YELLOW));
        squads.disband(squad);
        SquadSync.changed(leader.server);
        return null;
    }

    // ----- Настройки отряда (лидер) -----

    @Nullable
    public static Component rename(ServerPlayer leader, String rawName) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireLeader(squad, leader);
        if (error != null) {
            return error;
        }
        String name = normalize(rawName);
        Component nameError = checkName(squads, name, squad);
        if (nameError != null) {
            return nameError;
        }
        squad.setName(name);
        squads.changed();
        Squads.tell(leader.server, squad, Component.translatable("squad.halgventmod.renamed", name)
                .withStyle(ChatFormatting.GREEN));
        SquadSync.changed(leader.server);
        return null;
    }

    @Nullable
    public static Component setBanner(ServerPlayer leader, DyeColor base, BannerPatternLayers patterns) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireLeader(squad, leader);
        if (error != null) {
            return error;
        }
        if (patterns.layers().size() > BANNER_MAX_LAYERS) {
            return Component.translatable("squad.halgventmod.banner_too_many", BANNER_MAX_LAYERS);
        }
        squad.setBanner(base, patterns);
        squads.changed();
        SquadSync.changed(leader.server);
        return null;
    }

    @Nullable
    public static Component setRank(ServerPlayer leader, UUID target, String rawRank) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireMember(squad, leader, target);
        if (error != null) {
            return error;
        }
        if (target.equals(leader.getUUID())) {
            return Component.translatable("squad.halgventmod.rank_leader");
        }
        String rank = normalize(rawRank);
        if (rank.length() > RANK_MAX) {
            return Component.translatable("squad.halgventmod.rank_length", RANK_MAX);
        }
        if (!rank.isEmpty() && !allowedName(rank)) {
            return Component.translatable("squad.halgventmod.name_characters");
        }
        squad.setRank(target, rank);
        squads.changed();
        SquadSync.changed(leader.server);
        return null;
    }

    @Nullable
    public static Component setFriendlyFire(ServerPlayer leader, boolean allowed) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireLeader(squad, leader);
        if (error != null) {
            return error;
        }
        squad.setFriendlyFire(allowed);
        squads.changed();
        Squads.tell(leader.server, squad, Component.translatable(allowed
                ? "squad.halgventmod.friendly_fire_on" : "squad.halgventmod.friendly_fire_off")
                .withStyle(ChatFormatting.YELLOW));
        SquadSync.changed(leader.server);
        return null;
    }

    @Nullable
    public static Component setMapMode(ServerPlayer leader, SquadMapMode mode) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireLeader(squad, leader);
        if (error != null) {
            return error;
        }
        squad.setMapMode(mode);
        squads.changed();
        Squads.tell(leader.server, squad, Component.translatable("squad.halgventmod.map_mode",
                Component.translatable("gui.halgventmod.squad.map." + mode.id())).withStyle(ChatFormatting.YELLOW));
        SquadSync.changed(leader.server);
        return null;
    }

    /** Отметить или снять звание, которому видны товарищи на карте в режиме RANKS ("" — «Участник»). */
    @Nullable
    public static Component setMapRank(ServerPlayer leader, String rank, boolean included) {
        Squads squads = Squads.get(leader.server);
        Squad squad = squads.of(leader.getUUID());
        Component error = requireLeader(squad, leader);
        if (error != null) {
            return error;
        }
        squad.setMapRank(normalize(rank), included);
        squads.changed();
        SquadSync.changed(leader.server);
        return null;
    }

    // ----- Проверки -----

    @Nullable
    private static Component requireLeader(@Nullable Squad squad, ServerPlayer player) {
        if (squad == null) {
            return Component.translatable("squad.halgventmod.not_in");
        }
        if (!squad.isLeader(player.getUUID())) {
            return Component.translatable("squad.halgventmod.not_leader");
        }
        return null;
    }

    @Nullable
    private static Component requireMember(@Nullable Squad squad, ServerPlayer leader, UUID target) {
        Component error = requireLeader(squad, leader);
        if (error != null) {
            return error;
        }
        if (!squad.members().contains(target)) {
            return Component.translatable("squad.halgventmod.not_member", memberName(leader.server, target));
        }
        return null;
    }

    @Nullable
    private static Component checkName(Squads squads, String name, @Nullable Squad renamed) {
        if (name.length() < NAME_MIN || name.length() > NAME_MAX) {
            return Component.translatable("squad.halgventmod.name_length", NAME_MIN, NAME_MAX);
        }
        if (!allowedName(name)) {
            return Component.translatable("squad.halgventmod.name_characters");
        }
        Squad existing = squads.byName(name);
        if (existing != null && existing != renamed) {
            return Component.translatable("squad.halgventmod.name_taken", name);
        }
        return null;
    }

    /** Пробелы по краям убраны, несколько подряд — один. */
    public static String normalize(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    /** Буквы (кириллица, латиница), цифры, пробел, дефис, апостроф; в начале и в конце — буква или цифра. */
    public static boolean allowedName(String name) {
        if (name.isEmpty() || !Character.isLetterOrDigit(name.charAt(0))
                || !Character.isLetterOrDigit(name.charAt(name.length() - 1))) {
            return false;
        }
        return name.codePoints().allMatch(c -> c == ' ' || c == '-' || c == '\'' || c == '’'
                || c >= '0' && c <= '9'
                || Character.isLetter(c) && (Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC
                        || Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN));
    }

    // ----- Имена -----

    /** Имя участника для сообщений: в игре — как в чате (RP-имя), нет в игре — ник серым. */
    public static Component memberName(MinecraftServer server, UUID member) {
        ServerPlayer online = server.getPlayerList().getPlayer(member);
        if (online != null) {
            return ChatChannels.name(online);
        }
        String name = profileName(server, member);
        return Component.literal(name != null ? name : member.toString()).withStyle(ChatFormatting.GRAY);
    }

    /** Ник игрока (в игре он или нет), если сервер его знает. */
    @Nullable
    public static String profileName(MinecraftServer server, UUID member) {
        ServerPlayer online = server.getPlayerList().getPlayer(member);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? null
                : server.getProfileCache().get(member).map(GameProfile::getName).orElse(null);
    }

    private static MutableComponent button(String key, String command, ChatFormatting color) {
        return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }
}
