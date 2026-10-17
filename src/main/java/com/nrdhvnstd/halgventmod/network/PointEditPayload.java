package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: администратор ставит, правит или удаляет точку карты. Права и точку проверяет сервер (MapPoints). */
public record PointEditPayload(MapPoints.Action action, MapPoint point) implements CustomPacketPayload {
    public static final Type<PointEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "point_edit"));

    public static final StreamCodec<FriendlyByteBuf, PointEditPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action());
                MapPoint.STREAM_CODEC.encode(buf, payload.point());
            },
            buf -> new PointEditPayload(buf.readEnum(MapPoints.Action.class), MapPoint.STREAM_CODEC.decode(buf)));

    public PointEditPayload {
        point = point.copy(); // в одиночной игре пакет не кодируется — серверу своя копия
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PointEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MapPoints.edit(player, payload.action(), payload.point());
            }
        });
    }
}
