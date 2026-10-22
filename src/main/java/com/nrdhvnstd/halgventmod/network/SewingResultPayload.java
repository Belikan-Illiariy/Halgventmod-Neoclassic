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

/** Клиент → сервер: шитьё на шитейном столе закончено с таким числом промахов. Сервер проверяет всё остальное. */
public record SewingResultPayload(BlockPos pos, int misses) implements CustomPacketPayload {
    public static final Type<SewingResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "sewing_result"));
    public static final StreamCodec<ByteBuf, SewingResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SewingResultPayload::pos,
            ByteBufCodecs.VAR_INT, SewingResultPayload::misses,
            SewingResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SewingResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof LeatherTableBlockEntity table) {
                table.sewn(player, payload.misses());
            }
        });
    }
}
