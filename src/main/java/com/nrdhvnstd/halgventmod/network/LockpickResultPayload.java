package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.lock.Lockpicking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: чем кончился взлом (замок открыт, отмычка сломана, бросили) и сколько тиков он шёл. Проверяет Lockpicking. */
public record LockpickResultPayload(Lockpicking.Outcome outcome, int ticks) implements CustomPacketPayload {
    public static final Type<LockpickResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "lockpick_result"));

    public static final StreamCodec<FriendlyByteBuf, LockpickResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.outcome());
                buf.writeVarInt(payload.ticks());
            },
            buf -> new LockpickResultPayload(buf.readEnum(Lockpicking.Outcome.class), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockpickResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Lockpicking.finish(player, payload.outcome(), payload.ticks());
            }
        });
    }
}
