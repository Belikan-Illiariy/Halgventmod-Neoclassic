package com.nrdhvnstd.halgventmod.client.armor;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.client.armor.geo.GeoModel;
import com.nrdhvnstd.halgventmod.client.armor.geo.GeoModelLoader;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Броня на игроке: две оболочки с геометрией ванильной брони — плоскость ног (как поножи)
 * и внешняя (как нагрудник), каждая со своей склейкой. Толщины броня не добавляет.
 * Поверх — выступающие детали (модели .geo.json), прикреплённые к частям тела.
 */
public class ArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float CROUCH_CAPE_Z = 1.4F;
    private static final float CROUCH_CAPE_Y = 1.85F;

    private final HumanoidModel<AbstractClientPlayer> legsModel;
    private final HumanoidModel<AbstractClientPlayer> outerModel;
    private final ModelPart cape; // своя копия ванильного плаща игрока: 10×16×1, развёртка 64×32

    public ArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                      HumanoidModel<AbstractClientPlayer> legsModel, HumanoidModel<AbstractClientPlayer> outerModel,
                      ModelPart cape) {
        super(parent);
        this.legsModel = legsModel;
        this.outerModel = outerModel;
        this.cape = cape;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ArmorInventory armor = armorOf(player);
        if (armor == null) {
            return;
        }
        renderPlane(poseStack, buffer, packedLight, armor, ArmorSlot.Plane.LEGS, legsModel);
        renderPlane(poseStack, buffer, packedLight, armor, ArmorSlot.Plane.OUTER, outerModel);
        renderDetails(poseStack, buffer, packedLight, armor, null);
        if (!player.isInvisible()) {
            renderCape(poseStack, buffer, packedLight, player, armor, partialTick);
        }
    }

    /** Надет ли плащ (накидка с плащом) — тогда ванильный плащ из скина не рисуется. */
    public static boolean wearsCape(AbstractClientPlayer player) {
        ArmorInventory armor = armorOf(player);
        return armor != null && ArmorLook.of(armor).capeTexture != null;
    }

    // Плащ: геометрия и физика ванильного плаща (CapeLayer), текстура — из ArmorLook
    private void renderCape(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                            AbstractClientPlayer player, ArmorInventory armor, float partialTick) {
        ArmorLook look = ArmorLook.of(armor);
        ResourceLocation texture = look.cape();
        if (texture == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        double dx = Mth.lerp(partialTick, player.xCloakO, player.xCloak) - Mth.lerp(partialTick, player.xo, player.getX());
        double dy = Mth.lerp(partialTick, player.yCloakO, player.yCloak) - Mth.lerp(partialTick, player.yo, player.getY());
        double dz = Mth.lerp(partialTick, player.zCloakO, player.zCloak) - Mth.lerp(partialTick, player.zo, player.getZ());
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double sin = Mth.sin(bodyYaw * Mth.DEG_TO_RAD);
        double cos = -Mth.cos(bodyYaw * Mth.DEG_TO_RAD);
        float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
        float swing = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
        float sway = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(partialTick, player.oBob, player.bob);
        lift += Mth.sin(Mth.lerp(partialTick, player.walkDistO, player.walkDist) * 6.0F) * 32.0F * bob;
        if (player.isCrouching()) {
            lift += 25.0F;
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + swing / 2.0F + lift));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sway / 2.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - sway / 2.0F));

        // При приседании плащ сдвигается, как в PlayerModel.setupAnim
        boolean crouching = getParentModel().crouching;
        cape.z = crouching ? CROUCH_CAPE_Z : 0.0F;
        cape.y = crouching ? CROUCH_CAPE_Y : 0.0F;
        cape.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(texture)),
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    // Выступающие детали поверх склейки; onlyAttachment — только одна часть тела (рука от первого лица)
    private void renderDetails(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                               ArmorInventory armor, @Nullable GeoModel.Attachment onlyAttachment) {
        for (ArmorLook.Detail detail : ArmorLook.of(armor).details) {
            GeoModel model = GeoModelLoader.get(detail.model());
            if (model == null) {
                continue;
            }
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(detail.texture()));
            for (GeoModel.Attachment attachment : model.attachments()) {
                if (onlyAttachment != null && attachment != onlyAttachment) {
                    continue;
                }
                // Деталь движется вместе со своей частью тела
                poseStack.pushPose();
                attachment.part(getParentModel()).translateAndRotate(poseStack);
                model.render(attachment, poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }

    private void renderPlane(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                             ArmorInventory armor, ArmorSlot.Plane plane, HumanoidModel<AbstractClientPlayer> model) {
        ArmorCompositor.Composite composite = ArmorCompositor.get(armor, plane);
        if (composite == null) {
            return;
        }
        getParentModel().copyPropertiesTo(model);
        model.setAllVisible(false);
        if (plane == ArmorSlot.Plane.OUTER) {
            // Голова, туловище, руки с кистями
            model.head.visible = true;
            model.body.visible = true;
            model.rightArm.visible = true;
            model.leftArm.visible = true;
        } else {
            // Пояс (низ туловища) и ноги со стопами
            model.body.visible = true;
            model.rightLeg.visible = true;
            model.leftLeg.visible = true;
        }
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.armorCutoutNoCull(composite.texture())),
                packedLight, OverlayTexture.NO_OVERLAY);
    }

    /** Рукав и перчатка на руке от первого лица. Вызывается до того, как игра нарисует саму руку. */
    public void renderFirstPersonArm(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                     AbstractClientPlayer player, HumanoidArm arm) {
        ArmorInventory armor = armorOf(player);
        if (armor == null) {
            return;
        }
        // Поза руки — как у ванильной руки от первого лица (PlayerRenderer.renderHand)
        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        parent.attackTime = 0.0F;
        parent.crouching = false;
        parent.swimAmount = 0.0F;
        parent.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        ModelPart source = arm == HumanoidArm.RIGHT ? parent.rightArm : parent.leftArm;
        source.xRot = 0.0F;

        ArmorCompositor.Composite composite = ArmorCompositor.get(armor, ArmorSlot.Plane.OUTER);
        if (composite != null && composite.covers(ArmorCompositor.COVERS_ARMS)) {
            ModelPart target = arm == HumanoidArm.RIGHT ? outerModel.rightArm : outerModel.leftArm;
            target.copyFrom(source);
            target.visible = true;
            target.render(poseStack, buffer.getBuffer(RenderType.armorCutoutNoCull(composite.texture())),
                    packedLight, OverlayTexture.NO_OVERLAY);
        }
        renderDetails(poseStack, buffer, packedLight, armor,
                arm == HumanoidArm.RIGHT ? GeoModel.Attachment.RIGHT_ARM : GeoModel.Attachment.LEFT_ARM);
    }

    @Nullable
    static ArmorInventory armorOf(AbstractClientPlayer player) {
        return player.getExistingDataOrNull(ModAttachments.ARMOR);
    }
}
