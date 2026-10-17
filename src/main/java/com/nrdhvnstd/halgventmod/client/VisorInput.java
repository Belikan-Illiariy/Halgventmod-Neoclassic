package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.ToggleVisorPayload;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Клавиша V: поднять или опустить забрало. Есть ли у шлема забрало, решает сервер (ToggleVisorPayload). */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class VisorInput {
    private VisorInput() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        boolean inGame = Minecraft.getInstance().player != null;
        while (ModKeys.VISOR.consumeClick()) {
            if (inGame) {
                PacketDistributor.sendToServer(ToggleVisorPayload.INSTANCE);
            }
        }
    }
}
