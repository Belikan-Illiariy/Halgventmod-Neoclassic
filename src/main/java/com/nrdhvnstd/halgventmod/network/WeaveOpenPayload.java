package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.weaving.WeavingScreen;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть окно тканья у станка. Что на станке (волокно, прокидки, изъяны) окно читает
 * из блок-сущности на клиенте. Обработчик зовётся только на клиенте.
 */
public record WeaveOpenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<WeaveOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "weave_open"));
    public static final StreamCodec<ByteBuf, WeaveOpenPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(WeaveOpenPayload::new, WeaveOpenPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeaveOpenPayload payload, IPayloadContext context) {
        // Окно открывает сам WeavingScreen (статический вызов), чтобы сервер не трогал классы интерфейса
        context.enqueueWork(() -> WeavingScreen.open(payload.pos()));
    }
}
