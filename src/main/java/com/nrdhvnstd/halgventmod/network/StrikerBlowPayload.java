package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.smithing.SmithScreens;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → кузнец: удар молотобойца (точность 0–100) или -1 / -2 — молотобоец встал / отошёл (smithing.Striker). */
public record StrikerBlowPayload(int accuracy) implements CustomPacketPayload {
    public static final Type<StrikerBlowPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "striker_blow"));
    public static final StreamCodec<ByteBuf, StrikerBlowPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StrikerBlowPayload::accuracy,
            StrikerBlowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StrikerBlowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SmithScreens.strikerBlow(payload.accuracy()));
    }
}
