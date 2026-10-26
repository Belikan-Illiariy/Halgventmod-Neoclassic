package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.point.LocationMaps;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: пришли снимок карты локации, если он новее моего (version — версия в кэше клиента, 0 — нет).
 * Сервер отвечает, только если локация у игрока открыта или он администратор (LocationMaps.request).
 */
public record LocationMapRequestPayload(int id, int version) implements CustomPacketPayload {
    public static final Type<LocationMapRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "location_map_request"));

    public static final StreamCodec<ByteBuf, LocationMapRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LocationMapRequestPayload::id,
            ByteBufCodecs.VAR_INT, LocationMapRequestPayload::version,
            LocationMapRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LocationMapRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                LocationMaps.request(player, payload.id(), payload.version());
            }
        });
    }
}
