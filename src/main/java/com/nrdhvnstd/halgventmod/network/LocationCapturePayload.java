package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.point.LocationMaps;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: снять карту локации (кнопка технической карты). Только администрация в творческом режиме. */
public record LocationCapturePayload(int id) implements CustomPacketPayload {
    public static final Type<LocationCapturePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "location_capture"));

    public static final StreamCodec<ByteBuf, LocationCapturePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(LocationCapturePayload::new, LocationCapturePayload::id);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LocationCapturePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && MapPoints.canEdit(player)) {
                LocationMaps.start(player.createCommandSourceStack(), payload.id());
            }
        });
    }
}
