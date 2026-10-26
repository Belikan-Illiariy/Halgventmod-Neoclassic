package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.map.LocationMapCache;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: ход снимка карты локации, 0–100 % (полоска на технической карте). */
public record LocationCaptureProgressPayload(int id, int percent) implements CustomPacketPayload {
    public static final Type<LocationCaptureProgressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "location_capture_progress"));

    public static final StreamCodec<ByteBuf, LocationCaptureProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LocationCaptureProgressPayload::id,
            ByteBufCodecs.VAR_INT, LocationCaptureProgressPayload::percent,
            LocationCaptureProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LocationCaptureProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LocationMapCache.captureProgress(payload.id(), payload.percent()));
    }
}
