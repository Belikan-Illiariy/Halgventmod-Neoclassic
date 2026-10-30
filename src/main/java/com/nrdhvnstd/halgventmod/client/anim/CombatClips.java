package com.nrdhvnstd.halgventmod.client.anim;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.combat.CombatState;
import com.nrdhvnstd.halgventmod.combat.Phase;
import com.nrdhvnstd.halgventmod.combat.WeaponProfile;
import com.nrdhvnstd.halgventmod.combat.WeaponProfiles;
import com.nrdhvnstd.halgventmod.combat.anim.AnimClip;
import com.nrdhvnstd.halgventmod.combat.anim.AnimLibrary;
import com.nrdhvnstd.halgventmod.combat.anim.BladePose;
import com.nrdhvnstd.halgventmod.combat.anim.CombatMoves;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Какая анимация KCC сейчас у бойца и в каком её миге — по нашему состоянию боя (CombatState):
 * <ul>
 *   <li>удар — CombatMoves.attack: замах — от начала до окна удара, удар — окно (кость hitbox), возврат — остаток,
 *       к концу растворяется в обычной позе; заряд держит кадр начала удара;</li>
 *   <li>блок — block_&lt;сторона&gt;: подъём и удержание на середине;</li>
 *   <li>сбит и оглушён — stunded;</li>
 *   <li>в бою (недавно бил или блокировал) — стойка stance_&lt;сторона&gt;&lt;вариант&gt;: видно, откуда будет удар.</li>
 * </ul>
 * Укол, пинок и кулак — наши позы (client/CombatAnimation). Левая рука со щитом анимаций не берёт: держит щит наготове
 * или поднимает его в блоке, правая в блоке щитом стоит в стойке.
 * <p>Перевод в позу ванильной модели — как в KCC (VanillaSkeletonGeckoAnimationApplier, MobPalBodyTransformApplier):
 * поворот X и Z — как есть, Y — к исходному, сдвиг Y — с минусом; кость body — поворот всей модели вокруг пояса;
 * кость item — предмет в кулаке. Сами преобразования — общие с сервером (combat/anim/BladePose).
 */
public final class CombatClips {
    // Меняйте здесь ⚠️
    private static final int STANCE_LINGER = 100;        // тиков стойки после последнего удара или блока
    private static final float STANCE_FADE = 10;         // тиков на вход и выход стойки
    private static final float BLEND_IN = 2;             // тиков на вход удара и блока
    private static final float BLOCK_HOLD = 0.45F;       // блок замирает на этой доле анимации
    private static final float RECOVERY_FADE_FROM = 0.55F; // с этой доли возврата анимация растворяется

    private static final float SWITCH_SLOW = 6;          // тиков перехода в стойку и между сторонами блока
    private static final float SWITCH_FAST = 3;          // тиков перехода в удар, блок и реакцию
    private static final float SHIELD_RAISE = 3;         // тиков на подъём и опускание щита
    // Левая рука со щитом: поза наготове и поднятая (поворот X, Y, радианы)
    private static final float SHIELD_READY_X = -0.5F, SHIELD_READY_Y = 0.2F;
    private static final float SHIELD_UP_X = -1.3F, SHIELD_UP_Y = 0.55F;

    /** Анимация бойца сейчас: какая, миг (с), вес против обычной позы; from — откуда идёт переход, blend — его доля. */
    public record Active(AnimClip clip, float time, float weight, @Nullable Active from, float blend) {
        Active(AnimClip clip, float time, float weight) {
            this(clip, time, weight, null, 1);
        }

        /** Вес с учётом перехода. */
        public float strength() {
            return from == null ? weight : Mth.lerp(blend, from.strength(), weight);
        }
    }

    // Переход бойца: прошлый кадр и начатая смена анимации (только клиент, рисование)
    private static final class Transition {
        @Nullable Active last;
        @Nullable Active from;
        double start;
        float duration;
        float shield;      // щит поднят: 0 — у бедра, 1 — перед собой
        double shieldAt;
    }

    private static void updateShield(LivingEntity entity, Transition transition, double now) {
        float step = (float) Mth.clamp(now - transition.shieldAt, 0, SHIELD_RAISE) / SHIELD_RAISE;
        transition.shieldAt = now;
        transition.shield = Mth.approach(transition.shield, entity.getData(ModAttachments.COMBAT).blocking ? 1 : 0, step);
    }

    private static final Map<LivingEntity, Transition> TRANSITIONS = new WeakHashMap<>();

    private CombatClips() {}

    /** Сейчас — или null: наша поза (или никакой). Смена анимации — плавная: от прошлого кадра к новому. */
    @Nullable
    public static Active of(LivingEntity entity, float partialTick) {
        Active target = target(entity, partialTick);
        Transition transition = TRANSITIONS.computeIfAbsent(entity, key -> new Transition());
        updateShield(entity, transition, entity.level().getGameTime() + partialTick);
        if (target == null) {
            transition.last = null;
            transition.from = null;
            return null;
        }
        double now = entity.level().getGameTime() + partialTick;
        Active last = transition.last;
        // Другая анимация или та же с начала (следующий удар серии)
        if (last != null && (!last.clip().name.equals(target.clip().name)
                || (target.clip().hitStart >= 0 && target.time() < last.time() - RESTART))) {
            transition.from = last.from() == null ? last
                    : new Active(last.clip(), last.time(), last.weight(), withoutFrom(last.from()), last.blend());
            transition.start = now;
            transition.duration = slowSwitch(last.clip().name, target.clip().name) ? SWITCH_SLOW : SWITCH_FAST;
        }
        Active result = target;
        if (transition.from != null) {
            float k = Mth.clamp((float) (now - transition.start) / transition.duration, 0, 1);
            if (k >= 1) {
                transition.from = null;
            } else {
                result = new Active(target.clip(), target.time(), target.weight(), transition.from, k * k * (3 - 2 * k));
            }
        }
        transition.last = result;
        return result;
    }

    private static final float RESTART = 0.05F;          // миг отскочил назад на столько (с) — анимация началась заново

    private static Active withoutFrom(Active active) {
        return new Active(active.clip(), active.time(), active.weight());
    }

    private static boolean slowSwitch(String from, String to) {
        return to.startsWith("stance_") || (from.startsWith("block_") && to.startsWith("block_"));
    }

    @Nullable
    private static Active target(LivingEntity entity, float partialTick) {
        CombatState state = entity.getData(ModAttachments.COMBAT);
        double now = entity.level().getGameTime() + partialTick;
        if (now >= state.hitStopAt && now < state.hitStopAt + 2) {
            now = state.hitStopAt; // попал — анимация на миг замирает (как в CombatAnimation)
        }
        // Отдача неидеального блока и анимация жертвы приёма — поверх всего (и без оружия), к концу растворяется
        AnimClip reaction = state.reaction.isEmpty() ? null : AnimLibrary.get(state.reaction);
        if (reaction != null) {
            float time = (float) (now - state.reactionAt) / 20.0F;
            if (time >= 0 && time < reaction.length) {
                float left = (reaction.length - time) / reaction.length;
                return new Active(reaction, time, Math.min(1, left * 3));
            }
        }
        WeaponProfile profile = WeaponProfiles.of(entity, entity.getMainHandItem());
        String variant = CombatMoves.variant(profile);
        // Блок щитом: правая рука — в стойке, щит поднимает левая (apply). Так и без оружия в основной руке
        if (state.phase == Phase.IDLE && state.blocking && WeaponProfiles.hasShield(entity)) {
            String side = CombatMoves.side(state.side);
            AnimClip clip = AnimLibrary.first("stance_" + side + (variant != null ? variant : ""), "stance_" + side + "_longsword", "stance_" + side);
            if (clip != null) {
                float time = clip.length > 0 ? ((entity.tickCount + partialTick) / 20.0F) % clip.length : 0;
                return new Active(clip, time, Math.min(1, (float) (now - state.guardSince) / BLEND_IN));
            }
        }
        if (variant == null) {
            return null;
        }
        float length = Math.max(1, state.phaseEnd - state.phaseStart);
        float progress = Mth.clamp((float) (now - state.phaseStart) / length, 0, 1);
        float since = (float) (now - state.phaseStart);
        switch (state.phase) {
            case WINDUP, RELEASE -> {
                AnimClip clip = stab(entity, profile, state) ? null : CombatMoves.attack(profile, state.side, state.heavy, state.riposte, state.move);
                if (clip == null) {
                    return null;
                }
                float time = state.phase == Phase.WINDUP
                        ? clip.hitStart * (state.heavy ? Math.min(1, progress * 2) : progress)
                        : CombatMoves.releaseTime(clip, progress);
                float weight = state.phase == Phase.WINDUP ? Math.min(1, since / BLEND_IN) : 1;
                return new Active(clip, time, weight);
            }
            case RECOVERY -> {
                AnimClip clip = state.prevPhase == Phase.KICK || stab(entity, profile, state) ? null
                        : CombatMoves.attack(profile, state.side, state.heavy, state.riposte, state.move);
                if (clip == null) {
                    return null;
                }
                float time = clip.hitEnd + progress * (clip.length - clip.hitEnd);
                float fade = Mth.clamp((progress - RECOVERY_FADE_FROM) / (1 - RECOVERY_FADE_FROM), 0, 1);
                return new Active(clip, time, 1 - fade * fade * (3 - 2 * fade));
            }
            case FLINCH, STUN -> {
                AnimClip clip = AnimLibrary.get("stunded");
                if (clip == null) {
                    return null;
                }
                float time = clip.length > 0 ? (since / 20.0F) % clip.length : 0;
                return new Active(clip, time, state.phase == Phase.FLINCH ? 1 - progress : 1);
            }
            case IDLE -> {
                if (state.blocking) {
                    AnimClip clip = AnimLibrary.get("block_" + CombatMoves.side(state.blockSide));
                    if (clip == null) {
                        return null;
                    }
                    float guard = (float) (now - state.guardSince);
                    return new Active(clip, Math.min(guard / 20.0F, clip.length * BLOCK_HOLD), Math.min(1, guard / BLEND_IN));
                }
                // В захвате цели — стойка всё время; после удара или блока — ещё немного
                float left = state.engaged ? STANCE_LINGER / 2.0F : (float) (state.engagedUntil - now);
                if (left <= 0) {
                    return null;
                }
                String side = CombatMoves.side(state.side);
                AnimClip clip = AnimLibrary.first("stance_" + side + variant, "stance_" + side + "_longsword", "stance_" + side);
                if (clip == null) {
                    return null;
                }
                float fade = Math.min(1, Math.min(left, STANCE_LINGER - left) / STANCE_FADE);
                float time = clip.length > 0 ? ((entity.tickCount + partialTick) / 20.0F) % clip.length : 0;
                return new Active(clip, time, Mth.clamp(fade, 0, 1));
            }
            default -> {
                return null;
            }
        }
    }

    /** Стойка держится столько тиков после боевого действия (ClientCombat). */
    public static int stanceLinger() {
        return STANCE_LINGER;
    }

    // Укол — своя поза (в KCC нет)
    private static boolean stab(LivingEntity entity, @Nullable WeaponProfile profile, CombatState state) {
        WeaponProfile.Attack attack = profile == null ? null : profile.attack(state.attack, Combat.altGrip(entity.getMainHandItem()));
        AttackKind shape = attack != null ? attack.shape() : state.attack;
        return shape == AttackKind.STAB;
    }

    // ---------------------------------------------------------------- наложение

    /** Положить кости анимации на модель (после ванильной позы). Ноги — только пока боец стоит. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, Active active) {
        // голова — куда смотрит боец (взгляд и прицел важнее), анимацию головы не берём
        bone(model.body, active, "torso", 1);
        bone(model.rightArm, active, "rightArm", 1);
        if (WeaponProfiles.hasShield(entity)) {
            shieldArm(model.leftArm, entity, active.strength());
        } else {
            bone(model.leftArm, active, "leftArm", 1);
        }
        float legs = 1 - Mth.clamp(entity.walkAnimation.speed() * 2.5F, 0, 1); // идёт — ноги шагают сами
        bone(model.rightLeg, active, "rightLeg", legs);
        bone(model.leftLeg, active, "leftLeg", legs);
    }

    // Анимации KCC рассчитаны на клинок в двух руках: левая со щитом их не берёт, щит держится сам
    private static void shieldArm(ModelPart arm, LivingEntity entity, float w) {
        Transition transition = TRANSITIONS.get(entity);
        float raise = transition == null ? 0 : transition.shield;
        raise = raise * raise * (3 - 2 * raise);
        arm.xRot = Mth.lerp(w, arm.xRot, Mth.lerp(raise, SHIELD_READY_X, SHIELD_UP_X));
        arm.yRot = Mth.lerp(w, arm.yRot, Mth.lerp(raise, SHIELD_READY_Y, SHIELD_UP_Y));
        arm.zRot = Mth.lerp(w, arm.zRot, 0);
    }

    // Переход: кость считается для обеих анимаций от одной обычной позы, между ними — доля blend
    private static void bone(ModelPart part, Active active, String name, float scale) {
        if (active.from() == null) {
            single(part, active, name, active.weight() * scale);
            return;
        }
        float xRot = part.xRot, yRot = part.yRot, zRot = part.zRot, x = part.x, y = part.y, z = part.z;
        bone(part, active.from(), name, scale);
        float fromXRot = part.xRot, fromYRot = part.yRot, fromZRot = part.zRot, fromX = part.x, fromY = part.y, fromZ = part.z;
        part.xRot = xRot;
        part.yRot = yRot;
        part.zRot = zRot;
        part.x = x;
        part.y = y;
        part.z = z;
        single(part, active, name, active.weight() * scale);
        float k = active.blend();
        part.xRot = Mth.lerp(k, fromXRot, part.xRot);
        part.yRot = Mth.lerp(k, fromYRot, part.yRot);
        part.zRot = Mth.lerp(k, fromZRot, part.zRot);
        part.x = Mth.lerp(k, fromX, part.x);
        part.y = Mth.lerp(k, fromY, part.y);
        part.z = Mth.lerp(k, fromZ, part.z);
    }

    private static void single(ModelPart part, Active active, String name, float w) {
        if (w <= 0) {
            return;
        }
        PartPose initial = part.getInitialPose();
        Vector3f rotation = active.clip().rotation(name, active.time());
        if (rotation != null) {
            part.xRot = Mth.lerp(w, part.xRot, rotation.x * Mth.DEG_TO_RAD);
            part.yRot = Mth.lerp(w, part.yRot, initial.yRot + rotation.y * Mth.DEG_TO_RAD);
            part.zRot = Mth.lerp(w, part.zRot, rotation.z * Mth.DEG_TO_RAD);
        }
        Vector3f position = active.clip().position(name, active.time());
        if (position != null) {
            part.x = Mth.lerp(w, part.x, initial.x + position.x);
            part.y = Mth.lerp(w, part.y, initial.y - position.y);
            part.z = Mth.lerp(w, part.z, initial.z + position.z);
        }
    }

    /** Кость body — поворот всей модели вокруг пояса (после поворота тела, до разворота модели). */
    public static void applyRoot(PoseStack pose, Active active) {
        pose.mulPose(rootMatrix(active));
    }

    private static Matrix4f rootMatrix(Active active) {
        Matrix4f matrix = BladePose.root(active.clip(), active.time(), active.weight());
        return active.from() == null ? matrix : mix(rootMatrix(active.from()), matrix, active.blend());
    }

    private static Matrix4f itemMatrix(Active active) {
        Matrix4f matrix = BladePose.item(active.clip(), active.time(), active.weight());
        return active.from() == null ? matrix : mix(itemMatrix(active.from()), matrix, active.blend());
    }

    // Между двумя положениями: сдвиг по прямой, поворот по дуге (простое смешивание матриц сплющивало бы модель)
    private static Matrix4f mix(Matrix4f from, Matrix4f to, float k) {
        Vector3f position = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), k);
        Quaternionf rotation = from.getNormalizedRotation(new Quaternionf()).slerp(to.getNormalizedRotation(new Quaternionf()), k);
        return new Matrix4f().translation(position).rotate(rotation);
    }

    /** Кость item — предмет в кулаке (удар лезвием); перед отрисовкой предмета в руке. */
    public static void applyItem(PoseStack pose, Active active) {
        pose.mulPose(itemMatrix(active));
    }
}
