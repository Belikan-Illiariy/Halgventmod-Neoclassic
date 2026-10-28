package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.combat.CombatState;
import com.nrdhvnstd.halgventmod.combat.Phase;
import com.nrdhvnstd.halgventmod.network.CombatHitPayload;
import com.nrdhvnstd.halgventmod.network.CombatStatePayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

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
        state.phaseStart = payload.start();
        state.phaseEnd = payload.start() + payload.length();
        if (payload.attack() == AttackKind.SPRINT && payload.phase() == Phase.RELEASE) {
            state.sprintLockUntil = payload.start() + Combat.SPRINT_LOCK;
        }
    }

    // Пока заглушка вместо крови и искр по материалу: видно, куда пришёлся удар
    public static void onHit(CombatHitPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 point = payload.point();
        boolean wall = payload.outcome() == CombatHitPayload.Outcome.WALL;
        for (int i = 0; i < HIT_PARTICLES; i++) {
            level.addParticle(wall ? ParticleTypes.SMOKE : ParticleTypes.CRIT, point.x, point.y, point.z,
                    (level.random.nextDouble() - 0.5) * PARTICLE_SPEED, level.random.nextDouble() * PARTICLE_SPEED,
                    (level.random.nextDouble() - 0.5) * PARTICLE_SPEED);
        }
    }
}
