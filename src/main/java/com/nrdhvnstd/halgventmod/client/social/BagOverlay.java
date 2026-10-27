package com.nrdhvnstd.halgventmod.client.social;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.social.Restraint;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * «Мешок на голове» (ТЗ 5.2): обзор как сквозь вырезанную тыкву — ванильная текстура pumpkinblur на весь экран.
 * В отличие от тыквы — и от третьего лица: иначе мешок обходится клавишей F5.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class BagOverlay {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "bag_overlay");
    private static final ResourceLocation PUMPKIN_BLUR = ResourceLocation.withDefaultNamespace("textures/misc/pumpkinblur.png");

    private BagOverlay() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, BagOverlay::render);
    }

    // Как Gui.renderTextureOverlay
    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !Restraint.BAG.on(minecraft.player)) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.blit(PUMPKIN_BLUR, 0, 0, -90, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(),
                graphics.guiWidth(), graphics.guiHeight());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
