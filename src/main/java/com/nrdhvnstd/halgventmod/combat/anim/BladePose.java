package com.nrdhvnstd.halgventmod.combat.anim;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.nrdhvnstd.halgventmod.combat.SwingPath;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Где клинок по кадру анимации KCC — общее для сервера (трасса удара, combat/Combat) и клиента (рисунок рук и
 * оружия, client/anim). Цепочка та же, что у отрисовки от первого лица: глаза и взгляд бойца → модель игрока
 * стоит под глазами лицом по взгляду (руки идут за взглядом) → кость body (поворот всей модели вокруг пояса) →
 * правая рука (плечо, поворот кости rightArm) → кулак → кость item → предмет «в руке» (как у ванильного
 * handheld-предмета от третьего лица) → клинок по диагонали спрайта от хвата к острию. Длина клинка — из профиля
 * оружия (с прибавками особого удара и разбега), а не по рисунку: у разных предметов он разный.
 */
public final class BladePose {
    // Меняйте здесь ⚠️
    public static final float FORWARD = -0.1F;          // модель чуть вперёд от глаз (как руки от первого лица)
    public static final float MODEL_SCALE = 0.9375F;    // игрок (PlayerRenderer.scale)
    private static final float BODY_PIVOT = 12 / 16.0F; // поворот всей модели — вокруг пояса
    private static final float ARM_X = -5, ARM_Y = 2;   // плечо правой руки в модели игрока, пиксели
    // Предмет в руке от третьего лица (minecraft:item/handheld, thirdperson_righthand)
    private static final float HELD_Y = 4 / 16.0F, HELD_Z = 0.5F / 16.0F;
    private static final float HELD_ROT_Y = -90, HELD_ROT_Z = 55;
    private static final float HELD_SCALE = 0.85F;
    // Хват и направление клинка на спрайте предмета (0…1 по рисунку; меч — рукоять внизу слева, остриё вверху справа)
    private static final Vector3f GRIP = new Vector3f(0.3F, 0.3F, 0.5F);
    private static final Vector3f ALONG = new Vector3f(1, 1, 0);

    private BladePose() {}

    /** Кость body: поворот всей модели вокруг пояса (как PlayerRendererMixin в Player Animation Library). */
    public static Matrix4f root(AnimClip clip, float time, float weight) {
        Matrix4f matrix = new Matrix4f();
        Vector3f rotation = clip.rotation("body", time);
        Vector3f position = clip.position("body", time);
        if (rotation == null && position == null) {
            return matrix;
        }
        Vector3f r = rotation == null ? new Vector3f() : rotation.mul(weight);
        Vector3f p = position == null ? new Vector3f() : position.mul(weight);
        return matrix.translate(-p.x / 16.0F, p.y / 16.0F + BODY_PIVOT, p.z / 16.0F)
                .rotateZ(r.z * Mth.DEG_TO_RAD)
                .rotateY(-r.y * Mth.DEG_TO_RAD)
                .rotateX(-r.x * Mth.DEG_TO_RAD)
                .translate(0, -BODY_PIVOT, 0);
    }

    /** Кость item: оружие в кулаке разворачивается (удар лезвием). */
    public static Matrix4f item(AnimClip clip, float time, float weight) {
        Matrix4f matrix = new Matrix4f();
        Vector3f rotation = clip.rotation("item", time);
        Vector3f position = clip.position("item", time);
        if (position != null) {
            matrix.translate(-position.x * weight / 16.0F, -position.z * weight / 16.0F, position.y * weight / 16.0F);
        }
        if (rotation != null) {
            matrix.rotateZ(-rotation.y * weight * Mth.DEG_TO_RAD)
                    .rotateY(-rotation.z * weight * Mth.DEG_TO_RAD)
                    .rotateX(-rotation.x * weight * Mth.DEG_TO_RAD);
        }
        return matrix;
    }

    /** Правая рука: плечо и поворот кости rightArm (как ModelPart.translateAndRotate). */
    private static Matrix4f rightArm(AnimClip clip, float time) {
        Vector3f rotation = clip.rotation("rightArm", time);
        Vector3f position = clip.position("rightArm", time);
        Vector3f p = position == null ? new Vector3f() : position;
        Matrix4f matrix = new Matrix4f().translate((ARM_X + p.x) / 16.0F, (ARM_Y - p.y) / 16.0F, p.z / 16.0F);
        if (rotation != null) {
            matrix.rotateZYX(rotation.z * Mth.DEG_TO_RAD, rotation.y * Mth.DEG_TO_RAD, rotation.x * Mth.DEG_TO_RAD);
        }
        return matrix;
    }

    /**
     * Клинок бойца в миг time анимации clip: плечо, хват, остриё в мире. length — длина клинка (блоки).
     */
    public static SwingPath.Blade blade(LivingEntity entity, AnimClip clip, float time, double length) {
        Vec3 eye = entity.getEyePosition();
        Matrix4f world = new Matrix4f()
                .translate((float) eye.x, (float) eye.y, (float) eye.z)
                .rotateY(-(entity.getYRot() + 180) * Mth.DEG_TO_RAD)
                .rotateX(-entity.getXRot() * Mth.DEG_TO_RAD)
                .translate(0, -entity.getEyeHeight(), FORWARD)
                .mul(root(clip, time, 1))
                .scale(-1, -1, 1)
                .scale(MODEL_SCALE)
                .translate(0, -1.501F, 0);
        Matrix4f arm = new Matrix4f(world).mul(rightArm(clip, time));
        Vector3f shoulder = arm.transformPosition(new Vector3f());
        Matrix4f held = arm
                .rotateX(-90 * Mth.DEG_TO_RAD)
                .rotateY(180 * Mth.DEG_TO_RAD)
                .translate(1 / 16.0F, 0.125F, -0.625F)
                .mul(item(clip, time, 1))
                .translate(0, HELD_Y, HELD_Z)
                .rotateXYZ(0, HELD_ROT_Y * Mth.DEG_TO_RAD, HELD_ROT_Z * Mth.DEG_TO_RAD)
                .scale(HELD_SCALE)
                .translate(-0.5F, -0.5F, -0.5F);
        Vector3f grip = held.transformPosition(new Vector3f(GRIP));
        Vector3f ahead = held.transformPosition(new Vector3f(GRIP).add(ALONG));
        Vector3f direction = ahead.sub(grip).normalize();
        Vec3 hilt = new Vec3(grip.x, grip.y, grip.z);
        Vec3 tip = hilt.add(direction.x * length, direction.y * length, direction.z * length);
        return new SwingPath.Blade(new Vec3(shoulder.x, shoulder.y, shoulder.z), hilt, tip);
    }
}
