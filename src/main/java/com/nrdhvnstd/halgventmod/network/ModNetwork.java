package com.nrdhvnstd.halgventmod.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Пакеты между клиентом и сервером. */
public final class ModNetwork {
    private static final String VERSION = "4"; // 4: режим ников над головами, очистка инвентаря, отряды

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
        registrar.playToClient(NameTagsSyncPayload.TYPE, NameTagsSyncPayload.STREAM_CODEC, NameTagsSyncPayload::handle);
        registrar.playToClient(SquadMembersPayload.TYPE, SquadMembersPayload.STREAM_CODEC, SquadMembersPayload::handle);
        registrar.playToClient(SquadStatePayload.TYPE, SquadStatePayload.STREAM_CODEC, SquadStatePayload::handle);
        registrar.playToClient(SquadResultPayload.TYPE, SquadResultPayload.STREAM_CODEC, SquadResultPayload::handle);
        registrar.playToServer(SquadActionPayload.TYPE, SquadActionPayload.STREAM_CODEC, SquadActionPayload::handle);
        registrar.playToServer(DrinkWaterPayload.TYPE, DrinkWaterPayload.STREAM_CODEC, DrinkWaterPayload::handle);
        registrar.playToServer(ClearInventoryPayload.TYPE, ClearInventoryPayload.STREAM_CODEC, ClearInventoryPayload::handle);
        registrar.playToServer(OpenInventoryPayload.TYPE, OpenInventoryPayload.STREAM_CODEC, OpenInventoryPayload::handle);
        registrar.playToServer(RenameKeyPayload.TYPE, RenameKeyPayload.STREAM_CODEC, RenameKeyPayload::handle);
        registrar.playToServer(MapTeleportPayload.TYPE, MapTeleportPayload.STREAM_CODEC, MapTeleportPayload::handle);
    }
}