package com.nrdhvnstd.halgventmod.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Пакеты между клиентом и сервером. */
public final class ModNetwork {
    private static final String VERSION = "3"; // 3: телепортация по карте

    private ModNetwork() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(NeedsSyncPayload.TYPE, NeedsSyncPayload.STREAM_CODEC, NeedsSyncPayload::handle);
        registrar.playToClient(CalendarSyncPayload.TYPE, CalendarSyncPayload.STREAM_CODEC, CalendarSyncPayload::handle);
        registrar.playToClient(CalendarEventsPayload.TYPE, CalendarEventsPayload.STREAM_CODEC, CalendarEventsPayload::handle);
        registrar.playToClient(LocksChunkPayload.TYPE, LocksChunkPayload.STREAM_CODEC, LocksChunkPayload::handle);
        registrar.playToServer(DrinkWaterPayload.TYPE, DrinkWaterPayload.STREAM_CODEC, DrinkWaterPayload::handle);
        registrar.playToServer(OpenInventoryPayload.TYPE, OpenInventoryPayload.STREAM_CODEC, OpenInventoryPayload::handle);
        registrar.playToServer(RenameKeyPayload.TYPE, RenameKeyPayload.STREAM_CODEC, RenameKeyPayload::handle);
        registrar.playToServer(MapTeleportPayload.TYPE, MapTeleportPayload.STREAM_CODEC, MapTeleportPayload::handle);
    }
}