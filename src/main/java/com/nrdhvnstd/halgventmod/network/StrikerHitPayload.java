package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.smithing.Striker;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: молотобоец ударил кувалдой; точность — насколько в такт, 0–100. */
public record StrikerHitPayload(BlockPos pos, int accuracy) implements CustomPacketPayload {
    public static final Type<StrikerHitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "striker_hit"));
    public static final StreamCodec<ByteBuf, StrikerHitPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StrikerHitPayload::pos,
            ByteBufCodecs.VAR_INT, StrikerHitPayload::accuracy,
            StrikerHitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StrikerHitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Striker.hit(player, payload.pos(), payload.accuracy());
            }
        });
    }
}
