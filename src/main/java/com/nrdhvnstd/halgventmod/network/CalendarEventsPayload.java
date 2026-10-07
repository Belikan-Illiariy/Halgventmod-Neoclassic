package com.nrdhvnstd.halgventmod.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.Calendar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: все события календаря, чтобы отметить их в окне. */
public record CalendarEventsPayload(Map<Long, List<Calendar.Event>> events) implements CustomPacketPayload {
    public static final Type<CalendarEventsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "calendar_events"));

    private static final StreamCodec<ByteBuf, Calendar.Event> EVENT_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Calendar.Event::title,
            ByteBufCodecs.STRING_UTF8, Calendar.Event::description,
            Calendar.Event::new);

    public static final StreamCodec<ByteBuf, CalendarEventsPayload> STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, Long, List<Calendar.Event>, Map<Long, List<Calendar.Event>>>map(
                            HashMap::new, ByteBufCodecs.VAR_LONG, EVENT_CODEC.apply(ByteBufCodecs.list()))
                    .map(CalendarEventsPayload::new, CalendarEventsPayload::events);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CalendarEventsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Calendar.clientEvents = payload.events());
    }
}