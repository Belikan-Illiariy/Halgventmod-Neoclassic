package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.Calendar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: номер текущего дня, чтобы клиент знал дату и длину дня. */
public record CalendarSyncPayload(long day) implements CustomPacketPayload {
    public static final Type<CalendarSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "calendar_sync"));

    public static final StreamCodec<ByteBuf, CalendarSyncPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(CalendarSyncPayload::new, CalendarSyncPayload::day);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CalendarSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Calendar.clientDay = payload.day());
    }
}