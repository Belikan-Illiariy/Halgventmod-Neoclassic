package com.nrdhvnstd.halgventmod.client.pottery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.pottery.Vessel;
import com.nrdhvnstd.halgventmod.pottery.VesselGeometry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Рисует сосуд по вокселям: на полке — гранями с текстурой белой терракоты, окрашенной в цвет глины,
 * в окне круга — гранями без текстуры с затенением сторон.
 */
public final class VoxelRender {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("block/white_terracotta");
    // Цвета поверх белой терракоты
    public static final int RAW = 0x9C8E7E;      // сырая глина — тёмная, влажная
    public static final int DRY = 0xEDE4D2;      // высохший сырец — светлый, белёсый
    public static final int FIRED = 0xE8916A;    // черепок
    public static final int BLACK = 0x3E3632;    // чернолощёная
    public static final int UNDER = 0xF0C7A8;    // недожог — бледный
    public static final int OVER = 0xA8553C;     // пережог — тёмный

    private VoxelRender() {}

    /** Цвет посудины или сырца по признакам. */
    public static int color(ItemStack stack) {
        if (Pottery.isGreenware(stack)) {
            return Pottery.has(stack, Pottery.DRIED) || Pottery.has(stack, Pottery.CRACKED) ? DRY : RAW;
        }
        return Pottery.has(stack, Pottery.BLACK) ? BLACK
                : Pottery.has(stack, Pottery.UNDERFIRED) ? UNDER
                : Pottery.has(stack, Pottery.OVERFIRED) ? OVER : FIRED;
    }

    // ---------------------------------------------------------------- в мире

    /** Сосуд целиком в сетке блока (0..1), основанием на y = 0. */
    public static void render(Vessel vessel, int color, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(TEXTURE);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS));
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        pose.pushPose();
        pose.scale(1.0F / VesselGeometry.SIZE, 1.0F / VesselGeometry.SIZE, 1.0F / VesselGeometry.SIZE);
        PoseStack.Pose last = pose.last();
        for (int face : VoxelMesh.of(vessel)) {
            int voxel = face / 6, side = face % 6;
            int x = VesselGeometry.x(voxel), y = VesselGeometry.y(voxel), z = VesselGeometry.z(voxel);
            float[] n = VoxelMesh.NORMALS[side];
            for (int[] c : VoxelMesh.CORNERS[side]) {
                float cx = x + c[0], cy = y + c[1], cz = z + c[2];
                float u, v;
                if (side <= VoxelMesh.DOWN) {
                    u = cx / 16.0F;
                    v = cz / 16.0F;
                } else if (side <= VoxelMesh.SOUTH) {
                    u = cx / 16.0F;
                    v = 1 - cy / 16.0F;
                } else {
                    u = cz / 16.0F;
                    v = 1 - cy / 16.0F;
                }
                buffer.addVertex(last, cx, cy, cz)
                        .setColor(r, g, b, 255)
                        .setUv(sprite.getU(u), sprite.getV(v))
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(last, n[0], n[1], n[2]);
            }
        }
        pose.popPose();
    }

    /**
     * Сосуд из стака в мире: своим размером (масштаб формы, не больше maxScale), основание — в начале координат,
     * ось круга — вертикально через начало.
     */
    public static void renderPlaced(ItemStack stack, float maxScale, PoseStack pose, MultiBufferSource buffers, int light) {
        Vessel vessel = Pottery.vessel(stack);
        float scale = Math.min(vessel.vesselForm().scale(), maxScale);
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(-0.5F, 0, -0.5F);
        render(vessel, color(stack), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    // ---------------------------------------------------------------- в окне

    /**
     * Грани без текстуры для окна (RenderType.gui: цвет и глубина). Каждая грань — с обеих сторон: в окне ось Y
     * перевёрнута, и порядок обхода меняется.
     */
    public static void renderGui(int[] faces, int color, int alpha, PoseStack.Pose pose, VertexConsumer buffer) {
        for (int face : faces) {
            int voxel = face / 6, side = face % 6;
            int x = VesselGeometry.x(voxel), y = VesselGeometry.y(voxel), z = VesselGeometry.z(voxel);
            float shade = VoxelMesh.SHADE[side];
            int r = Math.round((color >> 16 & 0xFF) * shade);
            int g = Math.round((color >> 8 & 0xFF) * shade);
            int b = Math.round((color & 0xFF) * shade);
            int[][] corners = VoxelMesh.CORNERS[side];
            for (int k = 0; k < 4; k++) {
                int[] c = corners[k];
                buffer.addVertex(pose, x + c[0], y + c[1], z + c[2]).setColor(r, g, b, alpha);
            }
            for (int k = 3; k >= 0; k--) {
                int[] c = corners[k];
                buffer.addVertex(pose, x + c[0], y + c[1], z + c[2]).setColor(r, g, b, alpha);
            }
        }
    }
}
