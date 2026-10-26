package com.nrdhvnstd.halgventmod.network;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.map.LocationMapCache;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: точки карты всех измерений, которые игрок может видеть (при входе, после каждой правки
 * и когда игрок стал администратором или перестал им быть: точки появления видит только администрация).
 * Точки копируются и при отправке, и при приёме: в одиночной игре пакет не кодируется,
 * и иначе клиент правил бы те же объекты, что лежат на сервере.
 */
public record PointsSyncPayload(List<MapPoint> points) implements CustomPacketPayload {
    public static final Type<PointsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "points_sync"));

    public static final StreamCodec<FriendlyByteBuf, PointsSyncPayload> STREAM_CODEC =
            MapPoint.STREAM_CODEC.apply(ByteBufCodecs.list()).map(PointsSyncPayload::new, PointsSyncPayload::points);

    public PointsSyncPayload {
        points = points.stream().map(MapPoint::copy).toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PointsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MapPoints.clientPoints = payload.points().stream().map(MapPoint::copy).toList();
            LocationMapCache.pointsChanged(); // новые версии снимков — попросить
        });
    }
}
