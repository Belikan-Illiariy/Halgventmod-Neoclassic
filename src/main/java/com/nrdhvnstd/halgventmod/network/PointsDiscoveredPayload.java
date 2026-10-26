package com.nrdhvnstd.halgventmod.network;

import java.util.HashSet;
import java.util.Set;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.map.LocationMapCache;
import com.nrdhvnstd.halgventmod.client.map.MapScreens;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;
import com.nrdhvnstd.halgventmod.world.Announcements;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: какие точки карты этот игрок уже открыл.
 * announce — точка, которую он открыл только что (надпись «Открытая локация», Announcements); 0 — никакая.
 */
public record PointsDiscoveredPayload(Set<Integer> ids, int announce) implements CustomPacketPayload {
    public static final Type<PointsDiscoveredPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "points_discovered"));

    public static final StreamCodec<ByteBuf, PointsDiscoveredPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, Integer, Set<Integer>>collection(HashSet::new, ByteBufCodecs.VAR_INT),
            PointsDiscoveredPayload::ids,
            ByteBufCodecs.VAR_INT, PointsDiscoveredPayload::announce,
            PointsDiscoveredPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PointsDiscoveredPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MapPoints.clientDiscovered = Set.copyOf(payload.ids());
            if (payload.announce() != 0) {
                MapScreens.discovered(payload.announce());
            }
            LocationMapCache.pointsChanged(); // только что открытое место — сразу попросить его снимок
            for (MapPoint point : MapPoints.clientPoints) {
                if (payload.announce() != 0 && point.id == payload.announce()) {
                    Announcements.location(point.name);
                }
            }
        });
    }
}
