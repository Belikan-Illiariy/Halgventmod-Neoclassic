package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.weaving.LoomBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: одна прокидка в окне тканья, итог — Weaving.PASS_*. Сервер проверяет всё остальное. */
public record WeavePassPayload(BlockPos pos, int result) implements CustomPacketPayload {
    public static final Type<WeavePassPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "weave_pass"));
    public static final StreamCodec<ByteBuf, WeavePassPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WeavePassPayload::pos,
            ByteBufCodecs.VAR_INT, WeavePassPayload::result,
            WeavePassPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeavePassPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof LoomBlockEntity loom) {
                loom.pass(player, payload.result());
            }
        });
    }
}
