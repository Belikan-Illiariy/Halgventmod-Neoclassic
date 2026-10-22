package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.pottery.WheelScreen;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: сесть за гончарный круг. lumps — сколько комьев на круге, quality — качество глины. */
public record WheelOpenPayload(BlockPos pos, int lumps, int quality) implements CustomPacketPayload {
    public static final Type<WheelOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "wheel_open"));
    public static final StreamCodec<ByteBuf, WheelOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WheelOpenPayload::pos,
            ByteBufCodecs.VAR_INT, WheelOpenPayload::lumps,
            ByteBufCodecs.VAR_INT, WheelOpenPayload::quality,
            WheelOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WheelOpenPayload payload, IPayloadContext context) {
        // Окно открывает сам WheelScreen (статический вызов), чтобы сервер не трогал классы интерфейса
        context.enqueueWork(() -> WheelScreen.open(payload.pos(), payload.lumps(), payload.quality()));
    }
}
