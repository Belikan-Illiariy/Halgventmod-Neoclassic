package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: раскрой закончен — столько срывов ножа и такая неровность линии (средний отход от контура,
 * ×10 пикселей). Сервер проверяет всё остальное.
 */
public record CutResultPayload(BlockPos pos, int slips, int wobble) implements CustomPacketPayload {
    public static final Type<CutResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "cut_result"));
    public static final StreamCodec<ByteBuf, CutResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CutResultPayload::pos,
            ByteBufCodecs.VAR_INT, CutResultPayload::slips,
            ByteBufCodecs.VAR_INT, CutResultPayload::wobble,
            CutResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CutResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof LeatherTableBlockEntity table) {
                table.cutDone(player, payload.slips(), payload.wobble());
            }
        });
    }
}
