package com.nrdhvnstd.halgventmod.client;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.squad.SquadClientState;
import com.nrdhvnstd.halgventmod.squad.SquadMarkers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Отряд на клиенте: при выходе из мира метки товарищей и состояние окна «Отряд» забываются, чтобы не всплыть в другом мире. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class SquadClient {
    private SquadClient() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SquadMarkers.clientMembers = List.of();
        SquadClientState.clear();
    }
}
