package com.nrdhvnstd.halgventmod.client.map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.ModKeys;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Метки в мире: значок метки (MapIcon), под ним название и расстояние — видны сквозь стены
 * и не уменьшаются с расстоянием. Дальние метки рисуются на расстоянии MAX_DISTANCE в их сторону.
 * Клавиша B в игре — новая метка там, где стоит игрок.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class WaypointsInWorld {
    private static final double MAX_DISTANCE = 64;   // дальше рисуем на этом расстоянии
    private static final float SIZE_PER_BLOCK = 0.003F; // размер растёт с расстоянием — на экране не меняется
    private static final float MIN_SCALE = 0.02F;
    private static final double HIDE_CLOSER_THAN = 3;  // вплотную метка не мешает
    private static final int TEXT_BACKGROUND = 0x80000000;
    private static final float ICON_SIZE = 16;

    private WaypointsInWorld() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (ModKeys.WAYPOINT.consumeClick()) {
            LocalPlayer player = minecraft.player;
            if (minecraft.screen == null && player != null && Waypoints.available()) {
                minecraft.setScreen(WaypointEditScreen.create(null,
                        player.getBlockX(), player.getBlockY(), player.getBlockZ()));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || Waypoints.all().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;

        for (Waypoint waypoint : Waypoints.all()) {
            Vec3 offset = new Vec3(waypoint.x + 0.5, waypoint.y + 1.5, waypoint.z + 0.5).subtract(eye);
            double distance = offset.length();
            if (distance < HIDE_CLOSER_THAN) {
                continue;
            }
            double shown = Math.min(distance, MAX_DISTANCE);
            Vec3 at = offset.scale(shown / distance);
            float scale = Math.max(MIN_SCALE, (float) shown * SIZE_PER_BLOCK);

            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(camera.rotation());
            pose.scale(scale, -scale, scale);
            Matrix4f matrix = pose.last().pose();

            // Значок над подписью, тоже сквозь стены
            float[] uv = MapIcon.uv(waypoint.cell());
            float half = ICON_SIZE / 2;
            float bottom = -1;
            VertexConsumer icon = buffers.getBuffer(RenderType.textSeeThrough(MapIcon.ATLAS));
            icon.addVertex(matrix, -half, bottom - ICON_SIZE, 0).setColor(-1).setUv(uv[0], uv[1]).setLight(LightTexture.FULL_BRIGHT);
            icon.addVertex(matrix, -half, bottom, 0).setColor(-1).setUv(uv[0], uv[3]).setLight(LightTexture.FULL_BRIGHT);
            icon.addVertex(matrix, half, bottom, 0).setColor(-1).setUv(uv[2], uv[3]).setLight(LightTexture.FULL_BRIGHT);
            icon.addVertex(matrix, half, bottom - ICON_SIZE, 0).setColor(-1).setUv(uv[2], uv[1]).setLight(LightTexture.FULL_BRIGHT);

            Component label = Component.translatable("gui.halgventmod.waypoint.in_world",
                    waypoint.name, (int) distance);
            font.drawInBatch(label, -font.width(label) / 2.0F, 2, 0xFFFFFFFF, false, matrix, buffers,
                    Font.DisplayMode.SEE_THROUGH, TEXT_BACKGROUND, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
