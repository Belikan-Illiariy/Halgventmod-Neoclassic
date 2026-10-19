package com.nrdhvnstd.halgventmod.client.sound;

import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.sound.SoundData;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlockEntity;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Вход в клиентскую часть звуков из общих классов (блок, BlockEntity). Вызывать только на клиенте:
 * класс грузится при первом вызове, на сервере до него не доходит.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class SoundClient {
    private SoundClient() {}

    public static void emitterLoaded(SoundEmitterBlockEntity emitter) {
        SoundEmitters.add(emitter);
    }

    public static void emitterRemoved(SoundEmitterBlockEntity emitter) {
        SoundEmitters.remove(emitter);
    }

    public static void openScreen(SoundEmitterBlockEntity emitter) {
        Minecraft.getInstance().setScreen(new SoundEmitterScreen(emitter.getBlockPos(), emitter.scene(), emitter.radius()));
    }

    // Вышли с сервера — его наборы и сцены больше не действуют
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SoundData.receive(Map.of(), Map.of());
    }
}
