package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.point.MapRoute;
import com.nrdhvnstd.halgventmod.point.MapRoutes;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: администратор прокладывает, правит или удаляет маршрут. Права проверяет сервер (MapRoutes). */
public record RouteEditPayload(MapRoutes.Action action, MapRoute route) implements CustomPacketPayload {
    public static final Type<RouteEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "route_edit"));

    public static final StreamCodec<FriendlyByteBuf, RouteEditPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action());
                MapRoute.STREAM_CODEC.encode(buf, payload.route());
            },
            buf -> new RouteEditPayload(buf.readEnum(MapRoutes.Action.class), MapRoute.STREAM_CODEC.decode(buf)));

    public RouteEditPayload {
        route = route.copy(); // в одиночной игре пакет не кодируется — серверу своя копия
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RouteEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MapRoutes.edit(player, payload.action(), payload.route());
            }
        });
    }
}
