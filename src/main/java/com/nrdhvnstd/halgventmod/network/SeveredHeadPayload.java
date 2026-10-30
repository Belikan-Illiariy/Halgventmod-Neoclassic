package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.SeveredHeads;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → всем, кто видит цель: голова отрублена (combat/Severing), с какой скоростью летит. */
public record SeveredHeadPayload(int entityId, float vx, float vy, float vz) implements CustomPacketPayload {
    public static final Type<SeveredHeadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "severed_head"));

    public static final StreamCodec<ByteBuf, SeveredHeadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SeveredHeadPayload::entityId,
            ByteBufCodecs.FLOAT, SeveredHeadPayload::vx,
            ByteBufCodecs.FLOAT, SeveredHeadPayload::vy,
            ByteBufCodecs.FLOAT, SeveredHeadPayload::vz,
            SeveredHeadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeveredHeadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SeveredHeads.onSevered(payload));
    }
}
