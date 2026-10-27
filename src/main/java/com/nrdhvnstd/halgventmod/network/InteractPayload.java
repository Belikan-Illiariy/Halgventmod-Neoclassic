package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.npc.Npcs;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: E по НПС (docs/INTERACTION_E_SPEC.md, 4). Игроку E шлёт SocialActionPayload из кругового меню,
 * короткое E по игроку ничего не делает. Дальность, видимость и цель проверяет Npcs.
 */
public record InteractPayload(int entityId, boolean shift) implements CustomPacketPayload {
    public static final Type<InteractPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "interact"));

    public static final StreamCodec<ByteBuf, InteractPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InteractPayload::entityId,
            ByteBufCodecs.BOOL, InteractPayload::shift,
            InteractPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InteractPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Npcs.handle(player, payload.entityId(), payload.shift());
            }
        });
    }
}
