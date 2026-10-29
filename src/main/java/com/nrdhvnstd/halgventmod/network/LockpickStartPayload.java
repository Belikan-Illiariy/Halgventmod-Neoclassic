package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.lock.LockpickScreen;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть мини-игру взлома (Lockpicking.start). Расположение штифтов клиент выводит из seed, так что
 * сервер их не пересылает; pins — штифтов, window — ширина «окна» штифта (доля хода), durability — запас прочности
 * отмычки в условных единицах.
 */
public record LockpickStartPayload(long seed, int pins, float window) implements CustomPacketPayload {
    public static final Type<LockpickStartPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "lockpick_start"));

    public static final StreamCodec<FriendlyByteBuf, LockpickStartPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeLong(payload.seed());
                buf.writeVarInt(payload.pins());
                buf.writeFloat(payload.window());
            },
            buf -> new LockpickStartPayload(buf.readLong(), buf.readVarInt(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockpickStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LockpickScreen.open(payload.seed(), payload.pins(), payload.window()));
    }
}
