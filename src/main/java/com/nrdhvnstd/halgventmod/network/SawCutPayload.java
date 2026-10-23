package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.carpentry.SawhorseBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: один пропил в окне распила, итог — Carpentry.CUT_*, и сколько раз зажало пилу. */
public record SawCutPayload(BlockPos pos, int result, int jams) implements CustomPacketPayload {
    public static final Type<SawCutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "saw_cut"));
    public static final StreamCodec<ByteBuf, SawCutPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SawCutPayload::pos,
            ByteBufCodecs.VAR_INT, SawCutPayload::result,
            ByteBufCodecs.VAR_INT, SawCutPayload::jams,
            SawCutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SawCutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof SawhorseBlockEntity sawhorse) {
                sawhorse.cut(player, payload.result(), payload.jams());
            }
        });
    }
}
