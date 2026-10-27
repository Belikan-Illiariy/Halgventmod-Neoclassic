package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.social.SocialBars;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → жертве и атакующему: шкала атакующего закончилась или отменена — полоска пропадает. */
public record SocialEndPayload(int attackerId) implements CustomPacketPayload {
    public static final Type<SocialEndPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "social_end"));

    public static final StreamCodec<ByteBuf, SocialEndPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SocialEndPayload::attackerId,
            SocialEndPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SocialEndPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SocialBars.end(payload.attackerId()));
    }
}
