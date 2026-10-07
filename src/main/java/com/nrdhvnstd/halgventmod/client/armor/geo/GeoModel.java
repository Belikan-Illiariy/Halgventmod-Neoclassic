package com.nrdhvnstd.halgventmod.client.armor.geo;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Модель выступающих деталей брони, загруженная из .geo.json (Blockbench, «Bedrock Entity»).
 * Координаты уже переведены в систему Java-моделей: пиксели, ось Y вниз,
 * отсчёт от точки вращения части тела, к которой деталь прикреплена.
 */
public final class GeoModel {
    /** Часть тела, к которой крепится деталь, и её точка вращения в координатах Bedrock. */
    public enum Attachment {
        HEAD("head", 0, 24, 0, model -> model.head),
        BODY("body", 0, 24, 0, model -> model.body),
        RIGHT_ARM("rightarm", -5, 22, 0, model -> model.rightArm),
        LEFT_ARM("leftarm", 5, 22, 0, model -> model.leftArm),
        RIGHT_LEG("rightleg", -1.9F, 12, 0, model -> model.rightLeg),
        LEFT_LEG("leftleg", 1.9F, 12, 0, model -> model.leftLeg);

        final String boneName; // имя кости в Blockbench без учёта регистра и подчёркиваний
        final Vector3f pivot;
        private final Function<HumanoidModel<?>, ModelPart> part;

        Attachment(String boneName, float x, float y, float z, Function<HumanoidModel<?>, ModelPart> part) {
            this.boneName = boneName;
            this.pivot = new Vector3f(x, y, z);
            this.part = part;
        }

        public ModelPart part(HumanoidModel<?> model) {
            return part.apply(model);
        }

        public boolean isArm() {
            return this == RIGHT_ARM || this == LEFT_ARM;
        }
    }

    /** Четырёхугольная грань: вершины в пикселях, развёртка 0..1, нормаль. */
    record Quad(Vector3f[] positions, float[] u, float[] v, Vector3f normal) {}

    /** Кость: сдвиг от родителя (пиксели), поворот (радианы), свои грани и дочерние кости. */
    record Bone(Vector3f offset, Vector3f rotation, List<Quad> quads, List<Bone> children) {
        void render(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
            poseStack.pushPose();
            // Как ModelPart.translateAndRotate
            poseStack.translate(offset.x / 16.0F, offset.y / 16.0F, offset.z / 16.0F);
            if (rotation.x != 0 || rotation.y != 0 || rotation.z != 0) {
                poseStack.mulPose(new Quaternionf().rotationZYX(rotation.z, rotation.y, rotation.x));
            }
            PoseStack.Pose pose = poseStack.last();
            for (Quad quad : quads) {
                for (int i = 0; i < 4; i++) {
                    Vector3f p = quad.positions[i];
                    buffer.addVertex(pose, p.x / 16.0F, p.y / 16.0F, p.z / 16.0F)
                            .setColor(-1)
                            .setUv(quad.u[i], quad.v[i])
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(pose, quad.normal.x, quad.normal.y, quad.normal.z);
                }
            }
            for (Bone child : children) {
                child.render(poseStack, buffer, light, overlay);
            }
            poseStack.popPose();
        }
    }

    private final Map<Attachment, List<Bone>> bones;

    GeoModel(Map<Attachment, List<Bone>> bones) {
        this.bones = new EnumMap<>(bones);
    }

    /** Части тела, к которым прикреплены детали. */
    public Set<Attachment> attachments() {
        return bones.keySet();
    }

    /** Нарисовать детали одной части тела; poseStack уже повёрнут как эта часть. */
    public void render(Attachment attachment, PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        List<Bone> list = bones.get(attachment);
        if (list != null) {
            for (Bone bone : list) {
                bone.render(poseStack, buffer, light, overlay);
            }
        }
    }
}
