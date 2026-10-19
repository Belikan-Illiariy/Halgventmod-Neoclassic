package com.nrdhvnstd.halgventmod.network;

import java.util.UUID;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.squad.SquadMapMode;
import com.nrdhvnstd.halgventmod.squad.SquadService;
import com.nrdhvnstd.halgventmod.squad.SquadSync;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: действие из окна «Отряд». Какие поля нужны — зависит от действия:
 * target — игрок или отряд, text — название, звание или режим карты, base/patterns — знамя, flag — переключатель.
 * Ответ — SquadResultPayload (успех или причина отказа); новое состояние приходит отдельно (SquadStatePayload).
 */
public record SquadActionPayload(Action action, UUID target, String text, DyeColor base, BannerPatternLayers patterns,
        boolean flag) implements CustomPacketPayload {

    public enum Action {
        REFRESH, CREATE, ACCEPT, DECLINE, INVITE, LEAVE, KICK, MAKE_LEADER, SET_RANK, RENAME, SET_BANNER,
        FRIENDLY_FIRE, MAP_MODE, MAP_RANK, DISBAND
    }

    private static final UUID NONE = new UUID(0, 0);

    public static SquadActionPayload of(Action action) {
        return new SquadActionPayload(action, NONE, "", DyeColor.WHITE, BannerPatternLayers.EMPTY, false);
    }

    public static SquadActionPayload target(Action action, UUID target) {
        return new SquadActionPayload(action, target, "", DyeColor.WHITE, BannerPatternLayers.EMPTY, false);
    }

    public static SquadActionPayload text(Action action, UUID target, String text) {
        return new SquadActionPayload(action, target, text, DyeColor.WHITE, BannerPatternLayers.EMPTY, false);
    }

    public static SquadActionPayload text(Action action, String text) {
        return text(action, NONE, text);
    }

    public static SquadActionPayload textFlag(Action action, String text, boolean flag) {
        return new SquadActionPayload(action, NONE, text, DyeColor.WHITE, BannerPatternLayers.EMPTY, flag);
    }

    public static SquadActionPayload banner(Action action, String name, DyeColor base, BannerPatternLayers patterns) {
        return new SquadActionPayload(action, NONE, name, base, patterns, false);
    }

    public static SquadActionPayload flag(Action action, boolean flag) {
        return new SquadActionPayload(action, NONE, "", DyeColor.WHITE, BannerPatternLayers.EMPTY, flag);
    }

    public static final Type<SquadActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "squad_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SquadActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action());
                UUIDUtil.STREAM_CODEC.encode(buf, payload.target());
                buf.writeUtf(payload.text(), 256);
                DyeColor.STREAM_CODEC.encode(buf, payload.base());
                BannerPatternLayers.STREAM_CODEC.encode(buf, payload.patterns());
                buf.writeBoolean(payload.flag());
            },
            buf -> new SquadActionPayload(buf.readEnum(Action.class), UUIDUtil.STREAM_CODEC.decode(buf),
                    buf.readUtf(256), DyeColor.STREAM_CODEC.decode(buf), BannerPatternLayers.STREAM_CODEC.decode(buf),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SquadActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Component error = switch (payload.action()) {
                case REFRESH -> {
                    SquadSync.send(player);
                    yield null;
                }
                case CREATE -> SquadService.create(player, payload.text(), payload.base(), payload.patterns());
                case ACCEPT -> SquadService.accept(player, payload.target());
                case DECLINE -> SquadService.decline(player, payload.target());
                case INVITE -> {
                    ServerPlayer target = player.server.getPlayerList().getPlayer(payload.target());
                    yield target == null
                            ? Component.translatable("squad.halgventmod.player_offline")
                            : SquadService.invite(player, target);
                }
                case LEAVE -> SquadService.leave(player);
                case KICK -> SquadService.kick(player, payload.target());
                case MAKE_LEADER -> SquadService.makeLeader(player, payload.target());
                case SET_RANK -> SquadService.setRank(player, payload.target(), payload.text());
                case RENAME -> SquadService.rename(player, payload.text());
                case SET_BANNER -> SquadService.setBanner(player, payload.base(), payload.patterns());
                case FRIENDLY_FIRE -> SquadService.setFriendlyFire(player, payload.flag());
                case MAP_MODE -> SquadService.setMapMode(player, SquadMapMode.byId(payload.text()));
                case MAP_RANK -> SquadService.setMapRank(player, payload.text(), payload.flag());
                case DISBAND -> SquadService.disband(player);
            };
            if (payload.action() != Action.REFRESH) {
                SquadSync.flush(player.server); // сначала новое состояние, потом ответ
                PacketDistributor.sendToPlayer(player, new SquadResultPayload(payload.action(), error));
            }
        });
    }
}
