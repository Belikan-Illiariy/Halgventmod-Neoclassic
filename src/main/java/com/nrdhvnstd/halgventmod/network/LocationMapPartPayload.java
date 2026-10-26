package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.map.LocationMapCache;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: кусок part из total файла снимка карты локации (LocationMaps). */
public record LocationMapPartPayload(int id, int version, int part, int total, byte[] bytes) implements CustomPacketPayload {
    public static final Type<LocationMapPartPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "location_map_part"));

    public static final StreamCodec<ByteBuf, LocationMapPartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LocationMapPartPayload::id,
            ByteBufCodecs.VAR_INT, LocationMapPartPayload::version,
            ByteBufCodecs.VAR_INT, LocationMapPartPayload::part,
            ByteBufCodecs.VAR_INT, LocationMapPartPayload::total,
            ByteBufCodecs.BYTE_ARRAY, LocationMapPartPayload::bytes,
            LocationMapPartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LocationMapPartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LocationMapCache.receive(payload.id(), payload.version(), payload.part(), payload.total(),
                payload.bytes()));
    }
}
