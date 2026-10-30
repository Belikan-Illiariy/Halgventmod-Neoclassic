package com.nrdhvnstd.halgventmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.client.anim.CombatClips;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.combat.CombatState;
import com.nrdhvnstd.halgventmod.combat.Phase;
import com.nrdhvnstd.halgventmod.combat.StrikeSide;
import com.nrdhvnstd.halgventmod.combat.SwingPath;
import com.nrdhvnstd.halgventmod.combat.WeaponClass;
import com.nrdhvnstd.halgventmod.combat.WeaponProfile;
import com.nrdhvnstd.halgventmod.combat.WeaponProfiles;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Анимации боя (ТЗ боя, этап 11) — своя система, без библиотек. Поза считается из состояния бойца (CombatStatePayload)
 * и тех же углов траектории, что у трассы клинка (SwingPath.sideAngle, verticalAngle): в фазе удара доля пути — доля
 * фазы, поэтому клинок на экране идёт туда же, куда бьёт трасса. Замах — плавный переход из покоя в начало пути
 * (заряд — дальше назад), удар — путь целиком, возврат — плавно обратно; блок — своя поза на каждую сторону;
 * пинок, сбитый замах и оглушение — свои позы; смена хвата — оборот.
 *
 * От первого лица (ItemInHandRendererMixin) поворачивается вся рука с предметом вокруг плеча. От третьего
 * (HumanoidModelMixin) рука ставится так, чтобы предмет в ней лёг в плоскость удара (у ванильной модели предмет
 * торчит из кулака под прямым углом к руке: наклон руки xRot = −угол клинка), а корпус закручивается вслед за
 * клинком — замах сбоку виден издалека. Углы ⚠️ — подбирать на глаз.
 */
public final class CombatAnimation {
    // Меняйте здесь ⚠️
    private static final float CHARGE_BACK = 18;          // заряд тянет клинок дальше назад, градусов
    private static final float SIDE_ROLL = 65;            // удар сбоку от первого лица — клинок плашмя
    private static final float VERTICAL_ROLL = 15;        // удар сверху и снизу — чуть наискось
    private static final float STAB_PITCH = -55;          // укол — остриём вперёд
    private static final double STAB_REACH = 0.45;        // и вперёд на столько при полном выпаде
    private static final float TWIST = 0.4F;              // корпус поворачивается на такую долю угла клинка
    private static final float SIDE_ARM_RAISE = -0.5F;    // рука при ударе сбоку — чуть вперёд от опущенной
    private static final float LUNGE = 3.0F;              // выпад укола от третьего лица, пикселей модели
    private static final float ALT_FLIP = 180;            // второй хват топора и древкового — обухом вперёд
    private static final float ALT_TILT = 30;             // второй хват меча (полумеч) — клинок наклонён
    private static final float PIVOT_X = 0.35F, PIVOT_Y = -0.35F, PIVOT_Z = -0.25F; // «плечо» от первого лица
    private static final float HAND_X = 0.56F, HAND_Y = -0.52F, HAND_Z = -0.72F;   // кисть от первого лица (ваниль)
    // Сочность
    private static final float ANTICIPATION = 0.15F;      // замах перелетает и оседает (доля позы)
    private static final float FOLLOW = 0.15F;            // после удара клинок проносится дальше пути на эту долю
    private static final int HITSTOP = 2;                 // попал — анимация бьющего замирает на столько тиков
    private static final int RECOIL_TICKS = 7;            // клинок отброшен блоком: столько тиков возвращается
    private static final float RECOIL_DEGREES = 35;       // от первого лица
    private static final float RECOIL_ARM = 0.9F;         // от третьего, радиан
    private static final float TREMBLE_DEGREES = 1.6F;    // дрожь заряженного замаха
    private static final float LEAN = 0.22F;              // наклон корпуса в удар от третьего лица, радиан
    private static final float STEP = 0.45F;              // шаг вперёд при уколе
    // Реакция тела на попадание ⚠️
    private static final float HURT_TICKS = 8;
    private static final float HURT_HEAD = 0.6F, HURT_BODY = 0.35F, HURT_ARM = 0.7F, HURT_LEG = 0.6F;

    private CombatAnimation() {}

    /** Что делает боец сейчас. blend — вес позы против покоя, path — доля пути клинка, shake — сила тряски. */
    public enum Kind { NONE, SWING, GUARD, KICK, STAGGER }

    public record Pose(Kind kind, AttackKind shape, StrikeSide side, float path, float blend, float charge,
                       float shake, boolean twoHanded, float recoil) {
        static final Pose NONE = new Pose(Kind.NONE, AttackKind.SLASH, StrikeSide.RIGHT, 0, 0, 0, 0, false, 0);

        Pose(Kind kind, AttackKind shape, StrikeSide side, float path, float blend, float charge, float shake,
             boolean twoHanded) {
            this(kind, shape, side, path, blend, charge, shake, twoHanded, 0);
        }

        Pose withRecoil(float recoil) {
            return recoil <= 0 || kind == Kind.NONE ? this
                    : new Pose(kind, shape, side, path, blend, charge, shake, twoHanded, recoil);
        }
    }

    public static Pose of(LivingEntity entity, float partialTick) {
        CombatState state = entity.getData(ModAttachments.COMBAT);
        double now = entity.level().getGameTime() + partialTick;
        if (now >= state.hitStopAt && now < state.hitStopAt + HITSTOP) {
            now = state.hitStopAt; // попал — клинок на миг увяз
        }
        float sinceRecoil = (float) (now - state.recoilAt) / RECOIL_TICKS;
        float recoil = sinceRecoil >= 0 && sinceRecoil < 1 ? state.recoilPower * (1 - sinceRecoil) * (1 - sinceRecoil) : 0;
        return pose(entity, state, now).withRecoil(recoil);
    }

    private static Pose pose(LivingEntity entity, CombatState state, double now) {
        float length = Math.max(1, state.phaseEnd - state.phaseStart);
        float progress = Mth.clamp((float) (now - state.phaseStart) / length, 0, 1);
        WeaponProfile profile = WeaponProfiles.of(entity, entity.getMainHandItem());
        boolean twoHanded = profile != null && profile.twoHanded();
        AttackKind shape = shape(entity, profile, state.attack);
        StrikeSide side = state.side;
        return switch (state.phase) {
            case WINDUP -> state.heavy
                    // Заряд: замах дошёл и тянется дальше, пока держат (дрожит от напряжения)
                    ? new Pose(Kind.SWING, shape, side, 0, 1, Math.min(1, progress * 2), 0, twoHanded)
                    // Замах с перелётом: рука уходит чуть дальше и оседает — видно, что сейчас ударит
                    : new Pose(Kind.SWING, shape, side, 0, overshoot(progress), 0, 0, twoHanded);
            case RELEASE -> new Pose(Kind.SWING, shape, side, progress, 1, 0, 0, twoHanded);
            case RECOVERY -> state.prevPhase == Phase.KICK
                    ? new Pose(Kind.KICK, shape, side, 0, 1 - ease(progress), 0, 0, twoHanded)
                    // Проносит: клинок по инерции уходит дальше конца пути и только потом возвращается
                    : new Pose(Kind.SWING, shape, side, 1 + FOLLOW * ease(Math.min(1, progress * 3)),
                            1 - ease(progress), 0, 0, twoHanded);
            case KICK -> new Pose(Kind.KICK, shape, side, 0, ease(progress), 0, 0, twoHanded);
            case FLINCH -> new Pose(Kind.STAGGER, shape, side, 0, 1 - progress, 0, 0.6F * (1 - progress), twoHanded);
            case STUN -> new Pose(Kind.STAGGER, shape, side, 0, 1, 0, 1 - progress * 0.5F, twoHanded);
            case IDLE -> state.blocking
                    ? new Pose(Kind.GUARD, shape, state.blockSide, 0,
                            ease(Mth.clamp((float) (now - state.guardSince) / 3, 0, 1)), 0, 0, twoHanded)
                    : Pose.NONE;
        };
    }

    // Укол или удар: у особого и с разбега — того удара, на котором они основаны
    private static AttackKind shape(LivingEntity entity, WeaponProfile profile, AttackKind kind) {
        WeaponProfile.Attack attack = profile == null ? null : profile.attack(kind, Combat.altGrip(entity.getMainHandItem()));
        AttackKind shape = attack != null ? attack.shape() : kind;
        return shape == AttackKind.STAB ? AttackKind.STAB : AttackKind.SLASH;
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    // Как ease, но с перелётом на ANTICIPATION в середине и возвратом к 1 в конце
    private static float overshoot(float t) {
        return ease(t) + Mth.sin(t * Mth.PI) * t * ANTICIPATION;
    }

    // Дрожь напряжения: две частоты, чтобы не было ровным
    private static float tremble(LivingEntity entity, Pose pose) {
        if (pose.charge() <= 0) {
            return 0;
        }
        float time = entity.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        return (Mth.sin(time * 4.1F) + 0.5F * Mth.sin(time * 9.7F)) * pose.charge();
    }

    private static boolean vertical(StrikeSide side) {
        return side == StrikeSide.UP || side == StrikeSide.DOWN;
    }

    // Угол клинка с отводом заряда: сбоку — дальше в свою сторону, сверху — выше, снизу — ниже
    private static float angle(Pose pose) {
        float back = pose.charge() * CHARGE_BACK;
        return switch (pose.side()) {
            case LEFT -> SwingPath.sideAngle(pose.side(), pose.path()) + back;
            case RIGHT -> SwingPath.sideAngle(pose.side(), pose.path()) - back;
            case UP -> SwingPath.verticalAngle(pose.side(), pose.path()) + back;
            case DOWN -> SwingPath.verticalAngle(pose.side(), pose.path()) - back;
        };
    }

    // ---------------------------------------------------------------- от первого лица

    /** Повернуть руку с предметом от первого лица (до ванильной раскладки руки). */
    public static void firstPerson(PoseStack pose, LivingEntity player, float partialTick) {
        gripTurn(pose, player, partialTick);
        Pose now = of(player, partialTick);
        if (now.kind() == Kind.NONE) {
            return;
        }
        float w = now.blend();
        pose.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        switch (now.kind()) {
            case SWING -> {
                if (now.shape() == AttackKind.STAB) {
                    double reach = Mth.lerp(now.path(), -0.15 - now.charge() * 0.15, STAB_REACH);
                    pose.translate(0, 0, -reach * w);
                    pose.mulPose(Axis.XP.rotationDegrees(STAB_PITCH * w));
                } else if (vertical(now.side())) {
                    float roll = now.side() == StrikeSide.UP ? VERTICAL_ROLL : -VERTICAL_ROLL;
                    pose.mulPose(Axis.XP.rotationDegrees(angle(now) * w));
                    pose.mulPose(Axis.ZP.rotationDegrees(roll * w));
                } else {
                    float roll = now.side() == StrikeSide.LEFT ? -SIDE_ROLL : SIDE_ROLL;
                    pose.mulPose(Axis.YP.rotationDegrees(angle(now) * w));
                    pose.mulPose(Axis.ZP.rotationDegrees(roll * w));
                }
            }
            case GUARD -> {
                switch (now.side()) {
                    // Сверху — клинок поперёк над головой; слева и справа — стоймя у своего бока; снизу — остриём вниз
                    case UP -> {
                        pose.translate(-0.2 * w, 0.28 * w, 0);
                        pose.mulPose(Axis.ZP.rotationDegrees(85 * w));
                    }
                    case LEFT -> {
                        pose.translate(-0.4 * w, 0.05 * w, 0);
                        pose.mulPose(Axis.ZP.rotationDegrees(-10 * w));
                    }
                    case RIGHT -> {
                        pose.translate(0.05 * w, 0.05 * w, 0);
                        pose.mulPose(Axis.ZP.rotationDegrees(15 * w));
                    }
                    case DOWN -> {
                        pose.translate(-0.15 * w, -0.12 * w, 0);
                        pose.mulPose(Axis.XP.rotationDegrees(-45 * w));
                        pose.mulPose(Axis.ZP.rotationDegrees(30 * w));
                    }
                }
            }
            case KICK -> pose.translate(0, -0.18 * w, 0.05 * w);
            case STAGGER -> {
                float time = player.tickCount + partialTick;
                pose.translate(0, -0.12 * now.shake(), 0);
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 1.3F) * 8 * now.shake()));
            }
            default -> {
            }
        }
        if (now.kind() == Kind.SWING) {
            pose.mulPose(Axis.ZP.rotationDegrees(tremble(player, now) * TREMBLE_DEGREES));
        }
        if (now.recoil() > 0) {
            // Клинок отбросило: рука уходит вверх и назад, к себе
            pose.translate(0, 0.08 * now.recoil(), 0.12 * now.recoil());
            pose.mulPose(Axis.XP.rotationDegrees(RECOIL_DEGREES * now.recoil()));
        }
        pose.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
    }

    // Хват X: смена — оборот кисти; второй хват держится — топоры и древковое обухом вперёд, мечи — наклон клинка
    private static void gripTurn(PoseStack pose, LivingEntity player, float partialTick) {
        CombatState state = player.getData(ModAttachments.COMBAT);
        ItemStack hand = player.getMainHandItem();
        boolean alt = Combat.altGrip(hand);
        double now = player.level().getGameTime() + partialTick;
        if (alt != state.gripSeen) {
            state.gripSeen = alt;
            state.gripChangedAt = player.level().getGameTime();
        }
        float turn = Mth.clamp((float) (now - state.gripChangedAt) / Combat.GRIP_TICKS, 0, 1);
        WeaponProfile profile = WeaponProfiles.of(player, hand);
        boolean flip = profile != null && (profile.weaponClass() == WeaponClass.AXE || profile.weaponClass() == WeaponClass.GREATAXE
                || profile.weaponClass() == WeaponClass.POLEARM || profile.weaponClass() == WeaponClass.GREATHAMMER);
        float altAngle = flip ? ALT_FLIP : ALT_TILT;
        float angle = Mth.lerp(ease(turn), alt ? 0 : altAngle, alt ? altAngle : 0); // из прежнего хвата в новый
        if (angle == 0) {
            return;
        }
        pose.translate(HAND_X, HAND_Y, HAND_Z);
        pose.mulPose(flip ? Axis.YP.rotationDegrees(angle) : Axis.ZP.rotationDegrees(angle));
        pose.translate(-HAND_X, -HAND_Y, -HAND_Z);
    }

    // ---------------------------------------------------------------- от третьего лица

    /** Поставить руки, корпус и ногу модели (после ванильной позы). */
    public static void thirdPerson(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        pose(model, entity, ageInTicks);
        hurt(model, entity, Mth.clamp(ageInTicks - entity.tickCount, 0, 1));
    }

    /**
     * Тело дёргается от попадания — по части тела (по образцу KCC ClientHitReactionState), поверх любой позы, гаснет за
     * HURT_TICKS: голову откидывает, от удара в корпус сгибается, рука отлетает, нога подгибается.
     */
    private static void hurt(HumanoidModel<?> model, LivingEntity entity, float partialTick) {
        CombatState state = entity.getData(ModAttachments.COMBAT);
        float since = (float) (entity.level().getGameTime() + partialTick - state.hurtAt);
        if (state.hurtZone == null || since < 0 || since > HURT_TICKS) {
            return;
        }
        float k = (float) Math.sin(Math.min(1, since / 2.0F) * Math.PI / 2) * (1 - since / HURT_TICKS); // резко и плавно назад
        switch (state.hurtZone) {
            case CROWN, FACE, NECK -> {
                model.head.xRot -= HURT_HEAD * k;
                model.body.xRot -= HURT_BODY * 0.5F * k;
            }
            case CHEST, ABDOMEN -> {
                model.body.xRot += HURT_BODY * k;
                model.head.xRot += HURT_BODY * 0.6F * k;
                model.rightArm.xRot -= 0.3F * k;
                model.leftArm.xRot -= 0.3F * k;
            }
            case ARM, HAND -> {
                model.rightArm.zRot += HURT_ARM * k;
                model.leftArm.zRot -= HURT_ARM * 0.5F * k;
            }
            case THIGH, KNEE, SHIN, FOOT -> {
                model.rightLeg.xRot += HURT_LEG * k;
                model.body.xRot += HURT_BODY * 0.5F * k;
            }
        }
    }

    private static void pose(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        float partialTick = Mth.clamp(ageInTicks - entity.tickCount, 0, 1);
        Pose now = of(entity, partialTick);
        // Есть анимация KCC (удар, блок, стойка, оглушение) — она; наши позы — для укола, пинка, кулака и щита
        CombatClips.Active clip = CombatClips.of(entity, partialTick);
        if (clip != null) {
            CombatClips.apply(model, entity, clip);
            if (now.recoil() > 0) {
                model.rightArm.xRot -= RECOIL_ARM * now.recoil();
                model.leftArm.xRot -= RECOIL_ARM * now.recoil();
            }
            return;
        }
        if (now.kind() == Kind.NONE) {
            return;
        }
        float w = now.blend();
        float look = model.head.xRot;
        switch (now.kind()) {
            case SWING -> {
                if (now.shape() == AttackKind.STAB) {
                    // Рука вдоль взгляда — предмет из кулака смотрит вперёд; замах — назад, удар — выпад
                    arm(model.rightArm, w, look, 0.1F, 0);
                    model.rightArm.z += Mth.lerp(now.path(), 2.0F + now.charge() * 2, -LUNGE) * w;
                    twist(model, 0.15F * (1 - now.path()) * w);
                } else if (vertical(now.side())) {
                    // Наклон руки = −угол клинка: предмет ложится в вертикальную плоскость удара
                    float angle = angle(now) * Mth.DEG_TO_RAD;
                    arm(model.rightArm, w, -angle + (now.side() == StrikeSide.UP ? -0.35F : 0.2F) + look * 0.5F,
                            -0.15F, now.side() == StrikeSide.DOWN ? 0.2F : 0);
                    twist(model, (now.side() == StrikeSide.UP ? -0.25F : 0.25F) * (0.5F - now.path()) * w);
                } else {
                    // Рука почти опущена и чуть вперёд — предмет почти горизонтален; поворот руки — угол клинка
                    float angle = angle(now) * Mth.DEG_TO_RAD;
                    arm(model.rightArm, w, SIDE_ARM_RAISE + look, angle, 0);
                    twist(model, angle * TWIST * w);
                }
                // Корпус подаётся в удар: сверху, снизу и укол — наклон вперёд на середине пути; укол — шаг
                float arc = Mth.sin(Mth.clamp(now.path(), 0, 1) * Mth.PI) * w;
                if (now.shape() == AttackKind.STAB || vertical(now.side())) {
                    model.body.xRot += LEAN * arc;
                    model.head.xRot += LEAN * 0.5F * arc;
                }
                if (now.shape() == AttackKind.STAB) {
                    model.leftLeg.xRot = Mth.lerp(arc, model.leftLeg.xRot, -STEP);
                    model.rightLeg.xRot = Mth.lerp(arc, model.rightLeg.xRot, STEP * 0.6F);
                }
                model.rightArm.zRot += tremble(entity, now) * 0.03F;
                if (now.twoHanded()) {
                    model.leftArm.xRot = model.rightArm.xRot;
                    model.leftArm.yRot = model.rightArm.yRot + 0.55F;
                    model.leftArm.zRot = model.rightArm.zRot;
                }
            }
            case GUARD -> {
                if (WeaponProfiles.hasShield(entity)) {
                    arm(model.leftArm, w, -1.3F, 0.55F, 0);
                } else {
                    switch (now.side()) {
                        case UP -> arm(model.rightArm, w, -2.5F, -0.4F, 0.2F);
                        case LEFT -> {
                            arm(model.rightArm, w, -1.1F, 0.9F, 0);
                            twist(model, 0.2F * w);
                        }
                        case RIGHT -> {
                            arm(model.rightArm, w, -0.9F, -0.5F, 0.3F);
                            twist(model, -0.15F * w);
                        }
                        case DOWN -> arm(model.rightArm, w, -0.25F, 0.35F, 0);
                    }
                    if (now.twoHanded()) {
                        arm(model.leftArm, w, model.rightArm.xRot, model.rightArm.yRot + 0.55F, -model.rightArm.zRot);
                    }
                }
            }
            case KICK -> {
                model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -1.35F);
                model.leftArm.xRot = Mth.lerp(w, model.leftArm.xRot, -0.5F); // руки для равновесия
                model.rightArm.xRot = Mth.lerp(w, model.rightArm.xRot, 0.3F);
            }
            case STAGGER -> {
                float time = entity.tickCount + partialTick;
                float wobble = Mth.sin(time * 1.3F) * 0.25F * now.shake();
                model.rightArm.xRot += 0.3F * now.shake() + wobble;
                model.leftArm.xRot += 0.3F * now.shake() - wobble;
                model.head.zRot += wobble * 0.5F;
                twist(model, wobble * 0.4F);
            }
            default -> {
            }
        }
        if (now.recoil() > 0) {
            // Отбросило: рука с оружием вскинута назад, корпус откинут
            model.rightArm.xRot -= RECOIL_ARM * now.recoil();
            if (now.twoHanded()) {
                model.leftArm.xRot -= RECOIL_ARM * now.recoil();
            }
            model.body.xRot -= 0.15F * now.recoil();
        }
    }

    private static void arm(ModelPart arm, float w, float xRot, float yRot, float zRot) {
        arm.xRot = Mth.lerp(w, arm.xRot, xRot);
        arm.yRot = Mth.lerp(w, arm.yRot, yRot);
        arm.zRot = Mth.lerp(w, arm.zRot, zRot);
    }

    // Корпус поворачивается, плечи (точки крепления рук) идут за ним — как в ванильном взмахе
    private static void twist(HumanoidModel<?> model, float yRot) {
        if (yRot == 0) {
            return;
        }
        model.body.yRot += yRot;
        model.rightArm.z += Mth.sin(model.body.yRot) * 5.0F; // «+=»: выпад укола уже сдвинул руку вперёд
        model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0F;
        model.leftArm.z += -Mth.sin(model.body.yRot) * 5.0F;
        model.leftArm.x = Mth.cos(model.body.yRot) * 5.0F;
        model.rightArm.yRot += yRot;
        model.leftArm.yRot += yRot;
    }
}
