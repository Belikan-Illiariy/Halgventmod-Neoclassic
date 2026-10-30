package com.nrdhvnstd.halgventmod.client.anim;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.anim.AnimLibrary;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Клиент перечитывает анимации боя вместе с ресурсами (ресурс-паки, F3+T). */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class AnimReload {
    private AnimReload() {}

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) AnimLibrary::reload);
    }
}
