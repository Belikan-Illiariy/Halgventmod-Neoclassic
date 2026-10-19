package com.nrdhvnstd.halgventmod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.squad.SquadClientState;
import com.nrdhvnstd.halgventmod.squad.SquadMapMode;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: всё, что показывает окно «Отряд»: свой отряд (или null), приглашения
 * и — лидеру — игроки в сети, которых можно пригласить.
 */
public record SquadStatePayload(@Nullable SquadView squad, List<InviteView> invites, List<PlayerView> invitable)
        implements CustomPacketPayload {

    public record SquadView(UUID id, String name, DyeColor bannerBase, BannerPatternLayers bannerPatterns, UUID leader,
            boolean friendlyFire, SquadMapMode mapMode, List<String> mapRanks, List<MemberView> members) {}

    /** Участник: ник, RP-имя (или пусто), звание (или пусто), в игре ли. */
    public record MemberView(UUID id, String name, String rpName, String rank, boolean online) {}

    public record InviteView(UUID squad, String squadName, String inviter) {}

    /** Игрок в сети без отряда; invited — приглашение от этого отряда уже ждёт ответа. */
    public record PlayerView(UUID id, String name, boolean invited) {}

    public static final Type<SquadStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "squad_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SquadStatePayload> STREAM_CODEC =
            StreamCodec.of(SquadStatePayload::write, SquadStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SquadStatePayload payload) {
        SquadView squad = payload.squad();
        buf.writeBoolean(squad != null);
        if (squad != null) {
            UUIDUtil.STREAM_CODEC.encode(buf, squad.id());
            buf.writeUtf(squad.name());
            DyeColor.STREAM_CODEC.encode(buf, squad.bannerBase());
            BannerPatternLayers.STREAM_CODEC.encode(buf, squad.bannerPatterns());
            UUIDUtil.STREAM_CODEC.encode(buf, squad.leader());
            buf.writeBoolean(squad.friendlyFire());
            buf.writeEnum(squad.mapMode());
            buf.writeVarInt(squad.mapRanks().size());
            squad.mapRanks().forEach(buf::writeUtf);
            buf.writeVarInt(squad.members().size());
            for (MemberView member : squad.members()) {
                UUIDUtil.STREAM_CODEC.encode(buf, member.id());
                buf.writeUtf(member.name());
                buf.writeUtf(member.rpName());
                buf.writeUtf(member.rank());
                buf.writeBoolean(member.online());
            }
        }
        buf.writeVarInt(payload.invites().size());
        for (InviteView invite : payload.invites()) {
            UUIDUtil.STREAM_CODEC.encode(buf, invite.squad());
            buf.writeUtf(invite.squadName());
            buf.writeUtf(invite.inviter());
        }
        buf.writeVarInt(payload.invitable().size());
        for (PlayerView player : payload.invitable()) {
            UUIDUtil.STREAM_CODEC.encode(buf, player.id());
            buf.writeUtf(player.name());
            buf.writeBoolean(player.invited());
        }
    }

    private static SquadStatePayload read(RegistryFriendlyByteBuf buf) {
        SquadView squad = null;
        if (buf.readBoolean()) {
            UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
            String name = buf.readUtf();
            DyeColor base = DyeColor.STREAM_CODEC.decode(buf);
            BannerPatternLayers patterns = BannerPatternLayers.STREAM_CODEC.decode(buf);
            UUID leader = UUIDUtil.STREAM_CODEC.decode(buf);
            boolean friendlyFire = buf.readBoolean();
            SquadMapMode mapMode = buf.readEnum(SquadMapMode.class);
            int rankCount = buf.readVarInt();
            List<String> mapRanks = new ArrayList<>(rankCount);
            for (int i = 0; i < rankCount; i++) {
                mapRanks.add(buf.readUtf());
            }
            int count = buf.readVarInt();
            List<MemberView> members = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                members.add(new MemberView(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readBoolean()));
            }
            squad = new SquadView(id, name, base, patterns, leader, friendlyFire, mapMode, mapRanks, members);
        }
        int inviteCount = buf.readVarInt();
        List<InviteView> invites = new ArrayList<>(inviteCount);
        for (int i = 0; i < inviteCount; i++) {
            invites.add(new InviteView(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf()));
        }
        int playerCount = buf.readVarInt();
        List<PlayerView> invitable = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            invitable.add(new PlayerView(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readBoolean()));
        }
        return new SquadStatePayload(squad, invites, invitable);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SquadStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SquadClientState.update(payload));
    }
}
