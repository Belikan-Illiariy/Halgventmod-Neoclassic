package com.nrdhvnstd.halgventmod.combat;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.network.CombatHitPayload;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.sound.SoundPlay;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Пинок на F (ТЗ боя, 5.4): замах KICK_WINDUP, потом удар ногой по ближайшему в конусе перед собой. По поднятому блоку
 * спереди — пробивает его: блок опущен, цель оглушена. По остальным — немного дробящего урона и толчок. Пинок нельзя
 * ни заблокировать, ни парировать — только отойти.
 */
public final class Kick {
    // Меняйте здесь ⚠️
    public static final int KICK_WINDUP = 6;
    public static final int KICK_RELEASE = 3;       // после пинка — короткий возврат
    public static final double KICK_REACH = 1.8;
    public static final float KICK_CONE = 30;       // градусов от взгляда
    public static final int KICK_STUN = 16;
    public static final float KICK_DAMAGE = 4;
    public static final float KICK_STAMINA = 10;
    public static final double KICK_PUSH = 0.6;

    private Kick() {}

    /** Нажал F: в покое и если может — замах ногой. */
    static void start(ServerPlayer player, CombatState state, long now) {
        if (state.phase != Phase.IDLE || !Combat.canAct(player)) {
            return;
        }
        Stamina.spend(player, KICK_STAMINA);
        state.current = null;
        state.weapon = player.getMainHandItem().getItem();
        Combat.enter(player, state, Phase.KICK, KICK_WINDUP, now);
    }

    /** Конец замаха ногой: кого задели. */
    static void resolve(ServerPlayer player, CombatState state, long now) {
        Combat.enter(player, state, Phase.RECOVERY, KICK_RELEASE, now);
        LivingEntity target = target(player);
        if (target == null || !Combat.allowed(player, target)) {
            return;
        }
        Vec3 point = target.getBoundingBox().getCenter();
        CombatState guard = Combat.state(target);
        SoundPlay.atChest(target, ModSounds.COMBAT_HIT_FIST.get(), SoundSource.PLAYERS, 1.0F, 0.1F);
        if (guard.blocking && Defense.inFront(target, player)) {
            guard.blockLockedUntil = now + KICK_STUN;
            Defense.lower(target, guard);
            Combat.stun(target, guard, KICK_STUN, now);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new CombatHitPayload(player.getId(), target.getId(),
                    CombatHitPayload.Outcome.KICK, HitZone.of(target, point), DamageKind.BLUNT, point));
            Combat.debugBoth(player, target, Component.translatable("combat.halgventmod.debug.kick_guard"));
            return;
        }
        // Толчок — всегда, урон — дробящим сквозь броню корпуса
        target.knockback(KICK_PUSH, Mth.sin(player.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(player.getYRot() * Mth.DEG_TO_RAD));
        Combat.debugBoth(player, target, Component.translatable("combat.halgventmod.debug.kick", target.getDisplayName()));
        Combat.wound(player, target, DamageKind.BLUNT, KICK_DAMAGE, HitZone.CHEST, 0, 0, point, now);
    }

    // Ближайший живой в конусе перед собой, в пределах KICK_REACH от глаз, без стены между
    @Nullable
    private static LivingEntity target(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double minCos = Math.cos(Math.toRadians(KICK_CONE));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(KICK_REACH + 1),
                other -> other != player && other.isAlive() && !other.isSpectator() && other.isPickable())) {
            Vec3 closest = closest(other, eye);
            double distance = eye.distanceTo(closest);
            Vec3 toCenter = other.getBoundingBox().getCenter().subtract(eye).normalize();
            if (distance <= KICK_REACH && distance < bestDistance && look.dot(toCenter) >= minCos && player.hasLineOfSight(other)) {
                best = other;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Vec3 closest(LivingEntity entity, Vec3 from) {
        AABB box = entity.getBoundingBox();
        return new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY),
                Mth.clamp(from.z, box.minZ, box.maxZ));
    }
}
