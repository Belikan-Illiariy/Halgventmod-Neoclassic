package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.carpentry.SawingScreen;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть окно распила у пильных козел. Что на козлах (бревно, сколько пропилов) окно читает
 * из блок-сущности на клиенте. Обработчик зовётся только на клиенте.
 */
public record SawOpenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<SawOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "saw_open"));
    public static final StreamCodec<ByteBuf, SawOpenPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(SawOpenPayload::new, SawOpenPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SawOpenPayload payload, IPayloadContext context) {
        // Окно открывает сам SawingScreen (статический вызов), чтобы сервер не трогал классы интерфейса
        context.enqueueWork(() -> SawingScreen.open(payload.pos()));
    }
}
