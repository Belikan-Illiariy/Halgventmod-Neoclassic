package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.leather.PatternScreen;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть окно выбора выкройки стола кожевника. current — выбранная сейчас (−1 — никакая).
 * Обработчик зовётся только на клиенте.
 */
public record PatternOpenPayload(BlockPos pos, int current) implements CustomPacketPayload {
    public static final Type<PatternOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "pattern_open"));
    public static final StreamCodec<ByteBuf, PatternOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PatternOpenPayload::pos,
            ByteBufCodecs.INT, PatternOpenPayload::current,
            PatternOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PatternOpenPayload payload, IPayloadContext context) {
        // Окно открывает сам PatternScreen (статический вызов), чтобы сервер не трогал классы интерфейса
        context.enqueueWork(() -> PatternScreen.open(payload.pos(), payload.current()));
    }
}
