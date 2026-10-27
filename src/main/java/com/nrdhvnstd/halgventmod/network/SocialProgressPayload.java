package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.social.SocialBars;
import com.nrdhvnstd.halgventmod.social.SocialAction;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → жертве и атакующему: шкала применения, 0–1 (полоска над шкалами у жертвы, над головой жертвы — у атакующего). */
public record SocialProgressPayload(int attackerId, int targetId, SocialAction action, float progress)
        implements CustomPacketPayload {
    public static final Type<SocialProgressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "social_progress"));

    public static final StreamCodec<ByteBuf, SocialProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SocialProgressPayload::attackerId,
            ByteBufCodecs.VAR_INT, SocialProgressPayload::targetId,
            SocialAction.STREAM_CODEC, SocialProgressPayload::action,
            ByteBufCodecs.FLOAT, SocialProgressPayload::progress,
            SocialProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SocialProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SocialBars.progress(payload.attackerId(), payload.targetId(), payload.action(), payload.progress()));
    }
}
