package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Список игроков (ванильный Tab, у нас — I, KeyMigration) в сетевой игре — только администраторам (Permissions).
 * В одиночной игре (свой мир, не открытый для сети) список не трогаем.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class PlayerListGuard {
    private PlayerListGuard() {}

    public static boolean allowed(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        boolean singleplayer = server != null && !server.isPublished();
        return singleplayer || Permissions.isAdmin(minecraft.player);
    }

    @SubscribeEvent
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.TAB_LIST) && !allowed(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }
}
