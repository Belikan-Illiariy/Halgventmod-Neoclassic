package com.nrdhvnstd.halgventmod.client;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Хитбоксы (F3+B) — только администраторам (Permissions).
 * Включить не даёт KeyboardHandlerMixin; здесь хитбоксы гаснут, если права пропали
 * (сняли администратора, перешли из своего мира на сервер).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class HitboxGuard {
    private HitboxGuard() {}

    public static boolean allowed(@Nullable Player player) {
        return Permissions.isAdmin(player);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        if (minecraft.player != null && dispatcher.shouldRenderHitBoxes() && !allowed(minecraft.player)) {
            dispatcher.setRenderHitBoxes(false);
        }
    }
}
