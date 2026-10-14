package com.nrdhvnstd.halgventmod.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.client.armor.geo.GeoModel;
import com.nrdhvnstd.halgventmod.client.armor.geo.GeoModelLoader;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Надетое снаряжение на игроке (концепт инвентаря, этап 6): рюкзак и сумка, пояс, подсумки, фонарь, колчан.
 * Тот же механизм, что у выступающих деталей брони (GeoModelLoader):
 *   модель   — assets/<мод>/models/gear/<id>.geo.json, кости крепятся к частям тела (body, right_leg...);
 *   текстура — assets/<мод>/textures/gear/<id>.png.
 * Модель рисуется для подсумка 1 (правый бок); во втором подсумке — зеркально, на левом боку.
 * Нет модели — вещь на теле не видна (кольца, браслеты).
 */
public class GearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final String MODEL_FOLDER = "models/gear/";
    private static final String MODEL_EXTENSION = ".geo.json";
    private static final String TEXTURE_FOLDER = "textures/gear/";
    private static final String TEXTURE_EXTENSION = ".png";

    public GearLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        GearInventory gear = player.getExistingDataOrNull(ModAttachments.GEAR);
        if (gear == null || player.isInvisible()) {
            return;
        }
        for (GearSlot slot : GearSlot.values()) {
            ItemStack stack = gear.get(slot);
            if (!stack.isEmpty()) {
                renderGear(poseStack, buffer, packedLight, stack, slot == GearSlot.POUCH_2);
            }
        }
    }

    private void renderGear(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ItemStack stack, boolean mirrored) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        GeoModel model = GeoModelLoader.find(id.withPath(MODEL_FOLDER + id.getPath() + MODEL_EXTENSION));
        if (model == null) {
            return;
        }
        ResourceLocation texture = id.withPath(TEXTURE_FOLDER + id.getPath() + TEXTURE_EXTENSION);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        for (GeoModel.Attachment attachment : model.attachments()) {
            // Снаряжение движется вместе со своей частью тела
            poseStack.pushPose();
            attachment.part(getParentModel()).translateAndRotate(poseStack);
            if (mirrored) {
                poseStack.scale(-1.0F, 1.0F, 1.0F);
            }
            model.render(attachment, poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }
}
