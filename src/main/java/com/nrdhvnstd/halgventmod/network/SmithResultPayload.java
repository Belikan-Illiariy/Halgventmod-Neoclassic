package com.nrdhvnstd.halgventmod.network;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.smithing.Minigame;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: мини-игра кузнечества закончена (done) или брошена; данные — счёт этапов 0–100 и прочее,
 * что нужно хозяину игры. Сервер проверяет всё остальное (Minigame.result).
 */
public record SmithResultPayload(BlockPos pos, int kind, boolean done, List<Integer> data) implements CustomPacketPayload {
    public static final Type<SmithResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "smith_result"));
    public static final StreamCodec<ByteBuf, SmithResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SmithResultPayload::pos,
            ByteBufCodecs.VAR_INT, SmithResultPayload::kind,
            ByteBufCodecs.BOOL, SmithResultPayload::done,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(24)), SmithResultPayload::data,
            SmithResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SmithResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Minigame.result(player, payload.pos(), payload.kind(), payload.done(), payload.data());
            }
        });
    }
}
