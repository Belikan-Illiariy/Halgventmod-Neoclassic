package com.nrdhvnstd.halgventmod.client.armor;

import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.client.armor.geo.GeoModel;
import com.nrdhvnstd.halgventmod.client.armor.geo.GeoModelLoader;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.model.HumanoidModel;
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
import net.minecraft.world.entity.LivingEntity;

/**
 * Броня на игроке и на стойке для брони: две оболочки с геометрией ванильной брони — плоскость ног (как поножи)
 * и внешняя (как нагрудник), каждая со своей склейкой. Толщины броня не добавляет.
 * Поверх — выступающие детали (модели .geo.json), прикреплённые к частям тела.
 */
public class ArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final float CROUCH_CAPE_Z = 1.4F;
    private static final float CROUCH_CAPE_Y = 1.85F;
    private static final float CAPE_TILT = 6.0F; // наклон плаща в покое, как у ванильного

    // Поднятое забрало: поворот кости «visor» по X. Подобран под модель хундсгугеля (hundsgugel_bascinet_visor.geo.json):
    // ось на висках, при -80° забрало ложится над лбом. Угол в знаках Bedrock — загрузчик переносит углы без изменений
    private static final float VISOR_RAISED_DEGREES = -80.0F;
    private static final String VISOR_BONE = "visor";

    private final HumanoidModel<T> legsModel;
    private final HumanoidModel<T> outerModel;
    private final ModelPart cape; // своя копия ванильного плаща игрока: 10×16×1, развёртка 64×32

    public ArmorLayer(RenderLayerParent<T, M> parent, HumanoidModel<T> legsModel, HumanoidModel<T> outerModel,
                      ModelPart cape) {
        super(parent);
        this.legsModel = legsModel;
        this.outerModel = outerModel;
        this.cape = cape;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ArmorInventory armor = armorOf(entity);
        if (armor == null) {
            return;
        }
        renderPlane(poseStack, buffer, packedLight, armor, ArmorSlot.Plane.LEGS, legsModel);
        renderPlane(poseStack, buffer, packedLight, armor, ArmorSlot.Plane.OUTER, outerModel);
        renderDetails(poseStack, buffer, packedLight, armor, null, visorProgress(entity, armor, ageInTicks));
        // Невидимый игрок плащ прячет, как ванильный; невидимая стойка — манекен, плащ на ней виден
        if (!(entity instanceof AbstractClientPlayer) || !entity.isInvisible()) {
            renderCape(poseStack, buffer, packedLight, entity, armor, partialTick);
        }
    }

    /** Надет ли плащ (накидка с плащом) — тогда ванильный плащ из скина не рисуется. */
    public static boolean wearsCape(LivingEntity entity) {
        ArmorInventory armor = armorOf(entity);
        return armor != null && ArmorLook.of(armor).capeTexture != null;
    }

    // Плащ: геометрия и физика ванильного плаща (CapeLayer), текстура — из ArmorLook.
    // На стойке плащ неподвижен и висит с ванильным наклоном.
    private void renderCape(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                            T entity, ArmorInventory armor, float partialTick) {
        ArmorLook look = ArmorLook.of(armor);
        ResourceLocation texture = look.cape();
        if (texture == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.125F);
        float tilt = CAPE_TILT;
        float sway = 0.0F;
        if (entity instanceof AbstractClientPlayer player) {
            double dx = Mth.lerp(partialTick, player.xCloakO, player.xCloak) - Mth.lerp(partialTick, player.xo, player.getX());
            double dy = Mth.lerp(partialTick, player.yCloakO, player.yCloak) - Mth.lerp(partialTick, player.yo, player.getY());
            double dz = Mth.lerp(partialTick, player.zCloakO, player.zCloak) - Mth.lerp(partialTick, player.zo, player.getZ());
            float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            double sin = Mth.sin(bodyYaw * Mth.DEG_TO_RAD);
            double cos = -Mth.cos(bodyYaw * Mth.DEG_TO_RAD);
            float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
            float swing = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
            sway = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
            float bob = Mth.lerp(partialTick, player.oBob, player.bob);
            lift += Mth.sin(Mth.lerp(partialTick, player.walkDistO, player.walkDist) * 6.0F) * 32.0F * bob;
            if (player.isCrouching()) {
                lift += 25.0F;
            }
            tilt += swing / 2.0F + lift;
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(tilt));
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

    // Насколько поднято забрало надетого шлема (0 — опущено, 1 — поднято), с плавным движением (VisorAnimation)
    private static float visorProgress(LivingEntity entity, ArmorInventory armor, float ageInTicks) {
        boolean raised = false;
        for (ArmorLook.Detail detail : ArmorLook.of(armor).details) {
            raised |= detail.visorRaised();
        }
        return VisorAnimation.progress(entity, raised, ageInTicks);
    }

    // Выступающие детали поверх склейки; onlyAttachment — только одна часть тела (рука от первого лица);
    // visorProgress — насколько поднято забрало: кость visor поворачивается на эту долю VISOR_RAISED_DEGREES
    private void renderDetails(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                               ArmorInventory armor, @Nullable GeoModel.Attachment onlyAttachment, float visorProgress) {
        Map<String, Vector3f> extraRotations = visorProgress > 0.0F
                ? Map.of(VISOR_BONE, new Vector3f(VISOR_RAISED_DEGREES * visorProgress * Mth.DEG_TO_RAD, 0.0F, 0.0F))
                : Map.of();
        for (ArmorLook.Detail detail : ArmorLook.of(armor).details) {
            GeoModel model = GeoModelLoader.get(detail.model());
            if (model == null) {
                continue;
            }
            // С отсечением задних граней: детали — плоскости без толщины, их лицевая и обратная грани
            // лежат в одном месте; без отсечения они бы мерцали, а так каждая видна со своей стороны
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(detail.texture()));
            for (GeoModel.Attachment attachment : model.attachments()) {
                if (onlyAttachment != null && attachment != onlyAttachment) {
                    continue;
                }
                // Деталь движется вместе со своей частью тела
                poseStack.pushPose();
                attachment.part(getParentModel()).translateAndRotate(poseStack);
                model.render(attachment, poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                        extraRotations);
                poseStack.popPose();
            }
        }
    }

    private void renderPlane(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                             ArmorInventory armor, ArmorSlot.Plane plane, HumanoidModel<T> model) {
        ArmorCompositor.Composite composite = ArmorCompositor.get(armor, plane);
        if (composite == null) {
            return;
        }
        getParentModel().copyPropertiesTo(model);
        model.setAllVisible(false);
        if (plane == ArmorSlot.Plane.OUTER) {
            // Голова, туловище, руки с кистями; hat — второй слой шлема на полпикселя дальше (накладки, нащёчники)
            model.head.visible = true;
            model.hat.visible = true;
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
                                     T player, HumanoidArm arm) {
        ArmorInventory armor = armorOf(player);
        if (armor == null) {
            return;
        }
        // Поза руки — как у ванильной руки от первого лица (PlayerRenderer.renderHand)
        M parent = getParentModel();
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
                arm == HumanoidArm.RIGHT ? GeoModel.Attachment.RIGHT_ARM : GeoModel.Attachment.LEFT_ARM, 0.0F);
    }

    @Nullable
    static ArmorInventory armorOf(LivingEntity entity) {
        return entity.getExistingDataOrNull(ModAttachments.ARMOR);
    }
}
