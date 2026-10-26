package com.nrdhvnstd.halgventmod.network;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.point.MapRoute;
import com.nrdhvnstd.halgventmod.point.MapRoutes;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → администрация: все маршруты (MapRoutes). Пустой список — права сняли, маршруты убрать.
 * Маршруты копируются при отправке и при приёме — в одиночной игре пакет не кодируется.
 */
public record RoutesSyncPayload(List<MapRoute> routes) implements CustomPacketPayload {
    public static final Type<RoutesSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "routes_sync"));

    public static final StreamCodec<FriendlyByteBuf, RoutesSyncPayload> STREAM_CODEC =
            MapRoute.STREAM_CODEC.apply(ByteBufCodecs.list()).map(RoutesSyncPayload::new, RoutesSyncPayload::routes);

    public RoutesSyncPayload {
        routes = routes.stream().map(MapRoute::copy).toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RoutesSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MapRoutes.clientRoutes = payload.routes().stream().map(MapRoute::copy).toList());
    }
}
