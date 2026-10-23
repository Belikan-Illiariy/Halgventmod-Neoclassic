package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.carpentry.BenchWorkScreen;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: начать работу на верстаке плотника — какое изделие и зерно, из которого окно лепит заготовки
 * этапов (бугры, сучки, волокно). Обработчик зовётся только на клиенте.
 */
public record BenchWorkOpenPayload(BlockPos pos, int product, int seed) implements CustomPacketPayload {
    public static final Type<BenchWorkOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "bench_work_open"));
    public static final StreamCodec<ByteBuf, BenchWorkOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BenchWorkOpenPayload::pos,
            ByteBufCodecs.VAR_INT, BenchWorkOpenPayload::product,
            ByteBufCodecs.INT, BenchWorkOpenPayload::seed,
            BenchWorkOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BenchWorkOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BenchWorkScreen.open(payload.pos(), payload.product(), payload.seed()));
    }
}
