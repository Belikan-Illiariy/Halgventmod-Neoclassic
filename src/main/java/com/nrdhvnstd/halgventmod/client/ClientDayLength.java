package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.Calendar;
import com.nrdhvnstd.halgventmod.world.DayLength;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Клиент между пакетами от сервера сам двигает время на +1 за тик.
 * Замедляем его так же, как сервер, по той же дате, иначе солнце дёргалось бы.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class ClientDayLength {
    private static final DayLength.Clock CLIENT_CLOCK = new DayLength.Clock();

    private ClientDayLength() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            CLIENT_CLOCK.reset();
            return;
        }
        long now = level.getDayTime();
        long corrected = CLIENT_CLOCK.correct(now, Calendar.dayOfYear(Calendar.clientDay));
        if (corrected != now) {
            level.setDayTime(corrected);
        }
    }
}