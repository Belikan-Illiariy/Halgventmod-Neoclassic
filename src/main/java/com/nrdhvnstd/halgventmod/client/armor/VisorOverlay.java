package com.nrdhvnstd.halgventmod.client.armor;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Опущенное забрало сужает обзор, как надетая тыква: картинка поверх всего экрана, только от первого лица.
 * Своя картинка шлема — textures/misc/visor/<id шлема>.png (щель забрала, рисует художник); пока её нет — тыква.
 * Забрало поднимается — картинка плавно гаснет вместе с ним (VisorAnimation).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class VisorOverlay {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "visor");
    private static final ResourceLocation PUMPKIN = ResourceLocation.withDefaultNamespace("textures/misc/pumpkinblur.png");
    private static final String FOLDER = "textures/misc/visor/";

    // Шлем → его картинка или тыква; файлы ищутся один раз, после F3+T — заново (ArmorRenderEvents)
    private static final Map<ResourceLocation, ResourceLocation> TEXTURES = new HashMap<>();

    private VisorOverlay() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER, VisorOverlay::render);
    }

    static void clearCache() {
        TEXTURES.clear();
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // Как у тыквы: только от первого лица и не в подзорную трубу
        if (player == null || player.isSpectator() || !minecraft.options.getCameraType().isFirstPerson() || player.isScoping()) {
            return;
        }
        ArmorInventory armor = player.getExistingDataOrNull(ModAttachments.ARMOR);
        ItemStack helmet = armor != null ? armor.get(ArmorSlot.HELMET) : ItemStack.EMPTY;
        ModArmor.ArmorInfo info = ModArmor.get(helmet);
        if (info == null || !info.visor()) {
            return;
        }
        float age = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float closed = 1.0F - VisorAnimation.progress(player, ModArmor.visorRaised(helmet), age);
        if (closed <= 0.0F) {
            return;
        }
        ResourceLocation texture = TEXTURES.computeIfAbsent(BuiltInRegistries.ITEM.getKey(helmet.getItem()), VisorOverlay::find);

        // Как Gui.renderTextureOverlay
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, closed);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.blit(texture, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static ResourceLocation find(ResourceLocation itemId) {
        ResourceLocation own = ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), FOLDER + itemId.getPath() + ".png");
        return Minecraft.getInstance().getResourceManager().getResource(own).isPresent() ? own : PUMPKIN;
    }
}
