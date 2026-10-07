package com.nrdhvnstd.halgventmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Thirst;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Шкалы на экране: сияние насыщения поверх окорочков и строка жажды над голодом.
 * Значки 9×9: assets/halgventmod/textures/gui/sprites/hud/<имя>.png
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class FoodHud {
    private static final ResourceLocation SATURATION_LAYER = id("saturation");
    private static final ResourceLocation THIRST_LAYER = id("thirst");

    private static final ResourceLocation SATURATION_FULL = id("hud/saturation_full");
    private static final ResourceLocation SATURATION_HALF = id("hud/saturation_half");
    private static final ResourceLocation THIRST_EMPTY = id("hud/thirst_empty");
    private static final ResourceLocation THIRST_HALF = id("hud/thirst_half");
    private static final ResourceLocation THIRST_FULL = id("hud/thirst_full");

    private FoodHud() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, SATURATION_LAYER, FoodHud::renderSaturation);
        event.registerAbove(SATURATION_LAYER, THIRST_LAYER, FoodHud::renderThirst);
    }

    // Сияние поверх окорочков: каждое очко насыщения — половина значка, как у сытости
    private static void renderSaturation(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldRender(minecraft)) {
            return;
        }
        float saturation = minecraft.player.getFoodData().getSaturationLevel();
        if (saturation <= 0) {
            return;
        }
        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - minecraft.gui.rightHeight + 10; // строка голода уже заняла место

        RenderSystem.enableBlend();
        for (int i = 0; i < 10; i++) {
            float left = saturation - i * 2;
            if (left <= 0) {
                break;
            }
            int x = right - i * 8 - 9;
            graphics.blitSprite(left >= 2 ? SATURATION_FULL : SATURATION_HALF, x, y, 9, 9);
        }
        RenderSystem.disableBlend();
    }

    // Строка жажды над голодом, справа налево, как окорочки
    private static void renderThirst(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldRender(minecraft)) {
            return;
        }
        int thirst = Thirst.get(minecraft.player);
        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - minecraft.gui.rightHeight;
        minecraft.gui.rightHeight += 10; // пузыри воздуха встанут над жаждой

        RenderSystem.enableBlend();
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            int value = i * 2 + 1;
            graphics.blitSprite(THIRST_EMPTY, x, y, 9, 9);
            if (value < thirst) {
                graphics.blitSprite(THIRST_FULL, x, y, 9, 9);
            } else if (value == thirst) {
                graphics.blitSprite(THIRST_HALF, x, y, 9, 9);
            }
        }
        RenderSystem.disableBlend();
    }

    // Там же, где рисуется голод: в выживании, без скрытого интерфейса, не верхом на лошади
    private static boolean shouldRender(Minecraft minecraft) {
        return !minecraft.options.hideGui
                && minecraft.player != null
                && minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer()
                && !(minecraft.player.getVehicle() instanceof LivingEntity);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, path);
    }
}