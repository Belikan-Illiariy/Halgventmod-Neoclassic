package com.nrdhvnstd.halgventmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.needs.Pain;
import com.nrdhvnstd.halgventmod.registry.ModSounds;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Боль на экране (ТЗ боя, 8): шкалы нет. От «Мучительной» (70) — тёмно-красная виньетка, покачивание камеры и
 * сердцебиение, чем больнее — тем сильнее и чаще; на «Пределе» (90) мир размыт (размытие меню из настроек игры).
 * В болевом шоке — всё на полную.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class PainEffects {
    private static final ResourceLocation BLUR_LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "pain_blur");
    private static final ResourceLocation VIGNETTE_LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "pain_vignette");
    private static final ResourceLocation VIGNETTE = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID,
            "textures/misc/fatigue_vignette.png");
    // Меняйте здесь ⚠️
    private static final float VIGNETTE_MIN = 0.3F, VIGNETTE_MAX = 0.85F;
    private static final float SWAY_DEGREES = 2.5F;        // наклон камеры при боли 100
    private static final float SWAY_SPEED = 0.05F;
    private static final int HEARTBEAT_SLOW = 30, HEARTBEAT_FAST = 14; // тиков между ударами при 70 и 100
    private static final float HEARTBEAT_VOLUME = 0.8F;
    private static final float MIN_STRENGTH = 0.05F;      // ровно на 70 эффекты уже есть, хоть и слабые

    private static int nextBeat;

    private PainEffects() {}

    /** Насколько сильны эффекты: 0 ниже 70, 1 — при 100 или в шоке. */
    private static float strength(LocalPlayer player) {
        if (Pain.shocked(player)) {
            return 1.0F;
        }
        float pain = Pain.get(player);
        if (pain < Pain.AGONIZING) {
            return 0;
        }
        return Math.max(MIN_STRENGTH, Mth.clamp((pain - Pain.AGONIZING) / (Pain.MAX - Pain.AGONIZING), 0.0F, 1.0F));
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CAMERA_OVERLAYS, BLUR_LAYER, PainEffects::renderBlur);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, VIGNETTE_LAYER, PainEffects::renderVignette);
    }

    // «Предел» — мир за интерфейсом размыт
    private static void renderBlur(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || (Pain.get(minecraft.player) < Pain.LIMIT && !Pain.shocked(minecraft.player))) {
            return;
        }
        graphics.flush();
        minecraft.gameRenderer.processBlurEffect(delta.getGameTimeDeltaPartialTick(false));
        minecraft.getMainRenderTarget().bindWrite(false);
    }

    private static void renderVignette(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        float strength = strength(minecraft.player);
        if (strength <= 0) {
            return;
        }
        float alpha = Mth.lerp(strength, VIGNETTE_MIN, VIGNETTE_MAX);
        RenderSystem.enableBlend();
        graphics.setColor(0.55F, 0.05F, 0.05F, alpha);
        graphics.blit(VIGNETTE, 0, 0, graphics.guiWidth(), graphics.guiHeight(), 0, 0, 256, 256, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    // Покачивание: камера медленно клонится из стороны в сторону
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float strength = strength(player);
        if (strength > 0) {
            float time = (float) (player.tickCount + event.getPartialTick());
            event.setRoll(event.getRoll() + Mth.sin(time * SWAY_SPEED * Mth.TWO_PI) * SWAY_DEGREES * strength);
        }
    }

    // Сердцебиение: чем больнее, тем чаще
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.isPaused()) {
            return;
        }
        float strength = strength(player);
        if (strength <= 0) {
            nextBeat = 0;
            return;
        }
        if (--nextBeat <= 0) {
            player.playSound(ModSounds.COMBAT_HEARTBEAT.get(), HEARTBEAT_VOLUME, 1.0F);
            nextBeat = Math.round(Mth.lerp(strength, HEARTBEAT_SLOW, HEARTBEAT_FAST));
        }
    }
}
