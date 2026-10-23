package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.smithing.OreMining;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: удар киркой или лопатой по рудной жиле (ЛКМ) — куда пришёлся (ради искры). */
public record OreStrikePayload(BlockPos pos, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<OreStrikePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "ore_strike"));
    public static final StreamCodec<ByteBuf, OreStrikePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OreStrikePayload::pos,
            ByteBufCodecs.DOUBLE, OreStrikePayload::x,
            ByteBufCodecs.DOUBLE, OreStrikePayload::y,
            ByteBufCodecs.DOUBLE, OreStrikePayload::z,
            OreStrikePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OreStrikePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                OreMining.strike(player, payload.pos(), new Vec3(payload.x(), payload.y(), payload.z()));
            }
        });
    }
}
