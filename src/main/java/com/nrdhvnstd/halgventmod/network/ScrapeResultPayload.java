package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.FleshingBeamBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: скобление закончено или брошено (finished = false). Сервер проверяет всё остальное. */
public record ScrapeResultPayload(BlockPos pos, boolean finished, int cuts, int tears) implements CustomPacketPayload {
    public static final Type<ScrapeResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "scrape_result"));
    public static final StreamCodec<ByteBuf, ScrapeResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScrapeResultPayload::pos,
            ByteBufCodecs.BOOL, ScrapeResultPayload::finished,
            ByteBufCodecs.VAR_INT, ScrapeResultPayload::cuts,
            ByteBufCodecs.VAR_INT, ScrapeResultPayload::tears,
            ScrapeResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScrapeResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof FleshingBeamBlockEntity beam) {
                beam.scraped(player, payload.finished(), payload.cuts(), payload.tears());
            }
        });
    }
}
