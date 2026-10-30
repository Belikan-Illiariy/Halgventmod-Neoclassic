package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.client.anim.CombatClips;
import com.nrdhvnstd.halgventmod.combat.anim.CombatMoves;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.combat.CombatState;
import com.nrdhvnstd.halgventmod.combat.Phase;
import com.nrdhvnstd.halgventmod.network.CombatHitPayload;
import com.nrdhvnstd.halgventmod.network.CombatStatePayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Клиентская копия состояния бойцов (CombatStatePayload) — для анимаций и интерфейса; исходы ударов (CombatHitPayload) —
 * пока искры в точке попадания, кровь — отдельная задача.
 */
public final class ClientCombat {
    private static final int HIT_PARTICLES = 6;
    private static final double PARTICLE_SPEED = 0.3;
    // Искры от металла (число при полной доле металла) ⚠️
    private static final int SPARKS_DEFLECT = 70;
    private static final int SPARKS_GLANCE = 35;
    private static final int SPARKS_BLOCK = 30;
    private static final int SPARKS_PARRY = 60;
    private static final double SPARK_SPEED = 0.45;
    private static final double SPARK_SPREAD = 0.75;

    private ClientCombat() {}

    public static void onState(CombatStatePayload payload) {
        if (Minecraft.getInstance().level == null
                || !(Minecraft.getInstance().level.getEntity(payload.entityId()) instanceof LivingEntity entity)) {
            return;
        }
        CombatState state = entity.getData(ModAttachments.COMBAT);
        if (payload.phase() != state.phase) {
            state.prevPhase = state.phase;
        }
        boolean blocking = (payload.flags() & CombatStatePayload.BLOCKING) != 0;
        if (blocking || payload.phase() != Phase.IDLE) {
            state.engagedUntil = entity.level().getGameTime() + CombatClips.stanceLinger(); // в стойке ещё немного
        }
        if (blocking && !state.blocking) {
            state.guardSince = entity.level().getGameTime();
        }
        state.phase = payload.phase();
        state.attack = payload.attack();
        state.side = payload.side();
        state.blockSide = payload.blockSide();
        state.heavy = (payload.flags() & CombatStatePayload.HEAVY) != 0;
        state.riposte = (payload.flags() & CombatStatePayload.RIPOSTE) != 0;
        state.blocking = (payload.flags() & CombatStatePayload.BLOCKING) != 0;
        state.engaged = (payload.flags() & CombatStatePayload.ENGAGED) != 0;
        state.move = payload.move();
        state.phaseStart = payload.start();
        state.phaseEnd = payload.start() + payload.length();
        if (payload.attack() == AttackKind.SPRINT && payload.phase() == Phase.RELEASE) {
            state.sprintLockUntil = payload.start() + Combat.SPRINT_LOCK;
        }
    }

    // Отдача исхода: попал — замирание и толчок камеры бьющему, тряска задетому; блок и парирование — клинок отбросило
    private static void feel(ClientLevel level, CombatHitPayload payload) {
        long now = level.getGameTime();
        LivingEntity attacker = level.getEntity(payload.attackerId()) instanceof LivingEntity living ? living : null;
        LivingEntity target = payload.targetId() >= 0 && level.getEntity(payload.targetId()) instanceof LivingEntity living
                ? living : null;
        LivingEntity me = Minecraft.getInstance().player;
        switch (payload.outcome()) {
            case HIT, COUNTER -> {
                if (attacker != null) {
                    attacker.getData(ModAttachments.COMBAT).hitStopAt = now;
                }
                if (attacker == me) {
                    CombatCamera.punch(CombatCamera.HIT_PUNCH);
                }
                if (target == me) {
                    CombatCamera.shake(CombatCamera.HURT_SHAKE);
                }
                if (target != null) {
                    CombatState hurt = target.getData(ModAttachments.COMBAT);
                    hurt.hurtZone = payload.zone(); // тело дёрнется от удара по этой части (CombatAnimation)
                    hurt.hurtAt = now;
                    // Приём с анимацией жертвы (KCC): её и играет задетый
                    String victim = attacker == null ? "" : CombatMoves.victim(attacker.getData(ModAttachments.COMBAT).move);
                    if (!victim.isEmpty()) {
                        hurt.reaction = victim;
                        hurt.reactionAt = now;
                    }
                }
            }
            case BLOCK, PARRY, DEFLECT, GUARD_BREAK -> {
                float power = payload.outcome() == CombatHitPayload.Outcome.PARRY ? 1.0F
                        : payload.outcome() == CombatHitPayload.Outcome.DEFLECT ? 0.5F : 0.75F;
                recoil(attacker, now, power);
                recoil(target, now, power * 0.6F);
                if (attacker == me || target == me) {
                    CombatCamera.shake(CombatCamera.BLOCK_SHAKE * power);
                }
                // Неидеальный блок — защитника качнуло (анимация KCC block_unprefect); парирование — без этого
                if (target != null && (payload.outcome() == CombatHitPayload.Outcome.BLOCK
                        || payload.outcome() == CombatHitPayload.Outcome.GUARD_BREAK)) {
                    CombatState guard = target.getData(ModAttachments.COMBAT);
                    guard.reaction = "block_unprefect_" + (1 + level.random.nextInt(2));
                    guard.reactionAt = now;
                }
            }
            case CLASH -> {
                // Клинки встретились: обоих отбросило
                recoil(attacker, now, 0.9F);
                recoil(target, now, 0.9F);
                if (attacker == me || target == me) {
                    CombatCamera.shake(CombatCamera.BLOCK_SHAKE * 1.2F);
                }
            }
            case WALL -> {
                recoil(attacker, now, 0.8F);
                if (attacker == me) {
                    CombatCamera.shake(CombatCamera.BLOCK_SHAKE);
                }
            }
            case KICK -> {
                if (target == me) {
                    CombatCamera.shake(CombatCamera.HURT_SHAKE * 0.7F);
                }
            }
        }
    }

    /**
     * Искры (по образцу KCC): удар в латы — сноп из точки удара назад, к бьющему, веером и вниз; сколько — по доле,
     * погашенной металлом; клинок о клинок в блоке — тоже; парирование — щедрее. Дробящий высекает меньше, укол — мало.
     */
    private static void sparks(ClientLevel level, CombatHitPayload payload) {
        int count = switch (payload.outcome()) {
            case DEFLECT -> Math.round(SPARKS_DEFLECT * (0.4F + 0.6F * payload.metal()));
            case HIT, COUNTER -> Math.round(SPARKS_GLANCE * payload.metal());
            case BLOCK, GUARD_BREAK -> SPARKS_BLOCK;
            case PARRY, CLASH -> SPARKS_PARRY;
            default -> 0;
        };
        count = Math.round(count * switch (payload.kind()) {
            case BLUNT -> 0.4F;
            case PIERCING, PENETRATING -> 0.6F;
            default -> 1.0F;
        });
        if (count <= 0) {
            return;
        }
        Vec3 point = payload.point();
        Vec3 normal = level.getEntity(payload.attackerId()) instanceof LivingEntity attacker
                ? attacker.getEyePosition().subtract(point) : new Vec3(0, 1, 0);
        normal = normal.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : normal.normalize();
        var random = level.random;
        for (int i = 0; i < count; i++) {
            // Веер вокруг направления к бьющему, разлёт шире, чем узкий конус
            Vec3 spread = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.7 + 0.35, random.nextGaussian())
                    .scale(SPARK_SPREAD);
            Vec3 velocity = normal.add(spread).normalize().scale(SPARK_SPEED * (0.4 + random.nextDouble() * 0.9));
            level.addParticle(ModParticles.COMBAT_SPARK.get(), point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        }
    }

    private static void recoil(LivingEntity entity, long now, float power) {
        if (entity != null) {
            CombatState state = entity.getData(ModAttachments.COMBAT);
            state.recoilAt = now;
            state.recoilPower = power;
        }
    }

    // Пока заглушка вместо крови и искр по материалу: видно, куда пришёлся удар
    public static void onHit(CombatHitPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        feel(level, payload);
        sparks(level, payload);
        Vec3 point = payload.point();
        boolean wall = payload.outcome() == CombatHitPayload.Outcome.WALL;
        if (payload.metal() > 0 || payload.outcome() == CombatHitPayload.Outcome.BLOCK
                || payload.outcome() == CombatHitPayload.Outcome.PARRY || payload.outcome() == CombatHitPayload.Outcome.CLASH) {
            return; // вместо крит-звёздочек — искры
        }
        for (int i = 0; i < HIT_PARTICLES; i++) {
            level.addParticle(wall ? ParticleTypes.SMOKE : ParticleTypes.CRIT, point.x, point.y, point.z,
                    (level.random.nextDouble() - 0.5) * PARTICLE_SPEED, level.random.nextDouble() * PARTICLE_SPEED,
                    (level.random.nextDouble() - 0.5) * PARTICLE_SPEED);
        }
    }
}
