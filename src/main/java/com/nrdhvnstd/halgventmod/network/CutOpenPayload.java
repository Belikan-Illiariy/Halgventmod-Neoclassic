package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.leather.CutScreen;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: открыть мини-игру раскроя по выкройке с этим номером. Обработчик зовётся только на клиенте. */
public record CutOpenPayload(BlockPos pos, int pattern) implements CustomPacketPayload {
    public static final Type<CutOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "cut_open"));
    public static final StreamCodec<ByteBuf, CutOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CutOpenPayload::pos,
            ByteBufCodecs.VAR_INT, CutOpenPayload::pattern,
            CutOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CutOpenPayload payload, IPayloadContext context) {
        // Окно открывает сам CutScreen (статический вызов), чтобы сервер не трогал классы интерфейса
        context.enqueueWork(() -> CutScreen.open(payload.pos(), payload.pattern()));
    }
}
