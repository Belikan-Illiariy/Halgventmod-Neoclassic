package com.nrdhvnstd.halgventmod.client.anim;

import javax.annotation.Nullable;

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
 * Укол, пинок, кулак и щит — наши позы (client/CombatAnimation).
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

    /** Анимация бойца сейчас: какая, миг (с), вес против обычной позы. */
    public record Active(AnimClip clip, float time, float weight) {}

    private CombatClips() {}

    /** Сейчас — или null: наша поза (или никакой). */
    @Nullable
    public static Active of(LivingEntity entity, float partialTick) {
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
                    if (clip == null || WeaponProfiles.hasShield(entity)) {
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
        float w = active.weight();
        // голова — куда смотрит боец (взгляд и прицел важнее), анимацию головы не берём
        bone(model.body, active, "torso", w);
        bone(model.rightArm, active, "rightArm", w);
        bone(model.leftArm, active, "leftArm", w);
        float legs = w * (1 - Mth.clamp(entity.walkAnimation.speed() * 2.5F, 0, 1)); // идёт — ноги шагают сами
        bone(model.rightLeg, active, "rightLeg", legs);
        bone(model.leftLeg, active, "leftLeg", legs);
    }

    private static void bone(ModelPart part, Active active, String name, float w) {
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
        pose.mulPose(BladePose.root(active.clip(), active.time(), active.weight()));
    }

    /** Кость item — предмет в кулаке (удар лезвием); перед отрисовкой предмета в руке. */
    public static void applyItem(PoseStack pose, Active active) {
        pose.mulPose(BladePose.item(active.clip(), active.time(), active.weight()));
    }
}
