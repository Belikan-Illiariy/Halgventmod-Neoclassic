package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.Corpses;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → всем, кто видит: игрок или НПС умер, клиент кладёт на землю его тело (combat/Bodies, client/Corpses). */
public record CorpsePayload(int entityId) implements CustomPacketPayload {
    public static final Type<CorpsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "corpse"));

    public static final StreamCodec<ByteBuf, CorpsePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(CorpsePayload::new, CorpsePayload::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CorpsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Corpses.onDeath(payload));
    }
}
