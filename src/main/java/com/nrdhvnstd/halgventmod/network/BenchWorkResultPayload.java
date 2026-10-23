package com.nrdhvnstd.halgventmod.network;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.carpentry.CarpenterBenchBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: работа на верстаке закончена (done) или брошена; счёт каждого этапа 0–100, по порядку.
 * Сервер проверяет всё остальное.
 */
public record BenchWorkResultPayload(BlockPos pos, boolean done, List<Integer> scores) implements CustomPacketPayload {
    public static final Type<BenchWorkResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "bench_work_result"));
    public static final StreamCodec<ByteBuf, BenchWorkResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BenchWorkResultPayload::pos,
            ByteBufCodecs.BOOL, BenchWorkResultPayload::done,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), BenchWorkResultPayload::scores,
            BenchWorkResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BenchWorkResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof CarpenterBenchBlockEntity bench) {
                if (payload.done()) {
                    bench.worked(player, payload.scores());
                } else {
                    bench.workCancelled(player);
                }
            }
        });
    }
}
