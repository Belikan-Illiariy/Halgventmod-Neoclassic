package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;
import com.nrdhvnstd.halgventmod.banner.BannerRegistry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: действие из окна реестра знамён — взять копию себе, поправить название и описание, удалить.
 * Права и длины проверяет сервер (BannerRegistry.act).
 */
public record BannerActionPayload(Action action, String id, String name, String description) implements CustomPacketPayload {
    public enum Action { GIVE, EDIT, REMOVE }

    public static final Type<BannerActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "banner_action"));

    public static final StreamCodec<FriendlyByteBuf, BannerActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action());
                buf.writeUtf(payload.id(), BannerEntry.ID_MAX);
                buf.writeUtf(payload.name(), BannerEntry.NAME_MAX * 4);
                buf.writeUtf(payload.description(), BannerEntry.DESCRIPTION_MAX * 4);
            },
            buf -> new BannerActionPayload(buf.readEnum(Action.class), buf.readUtf(BannerEntry.ID_MAX),
                    buf.readUtf(BannerEntry.NAME_MAX * 4), buf.readUtf(BannerEntry.DESCRIPTION_MAX * 4)));

    public static BannerActionPayload simple(Action action, String id) {
        return new BannerActionPayload(action, id, "", "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BannerActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BannerRegistry.act(player, payload);
            }
        });
    }
}
