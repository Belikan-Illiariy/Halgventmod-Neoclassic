package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.draw.Drawing;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Доставание предмета на стороне клиента: пока предмет не достан, ЛКМ не бьёт и не ломает,
 * рука не взмахивает. Использование (ПКМ) гасят события Drawing — они срабатывают и на клиенте,
 * а дверь и сундук открыть можно.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class DrawInput {
    private DrawInput() {}

    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isAttack() && minecraft.player != null && !Drawing.ready(minecraft.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }
}
