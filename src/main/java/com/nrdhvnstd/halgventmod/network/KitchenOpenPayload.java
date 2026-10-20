package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.cooking.KitchenScreen;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть окно рецептов разделочного стола. current — выбранный сейчас (−1 — никакой).
 * Обработчик зовётся только на клиенте.
 */
public record KitchenOpenPayload(BlockPos pos, int current) implements CustomPacketPayload {
    public static final Type<KitchenOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "kitchen_open"));
    public static final StreamCodec<ByteBuf, KitchenOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, KitchenOpenPayload::pos,
            ByteBufCodecs.INT, KitchenOpenPayload::current,
            KitchenOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KitchenOpenPayload payload, IPayloadContext context) {
        // Окно открывает сам KitchenScreen (статический вызов), чтобы сервер не трогал классы интерфейса
        context.enqueueWork(() -> KitchenScreen.open(payload.pos(), payload.current()));
    }
}
