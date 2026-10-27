package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.social.Social;
import com.nrdhvnstd.halgventmod.social.SocialAction;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: выбран пункт кругового меню и отпущена E (обыск, мешок, кандалы, наручники, снятие). Всё проверяет Social. */
public record SocialActionPayload(int targetId, SocialAction action) implements CustomPacketPayload {
    public static final Type<SocialActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "social_action"));

    public static final StreamCodec<ByteBuf, SocialActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SocialActionPayload::targetId,
            SocialAction.STREAM_CODEC, SocialActionPayload::action,
            SocialActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SocialActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Social.handle(player, payload.targetId(), payload.action());
            }
        });
    }
}
