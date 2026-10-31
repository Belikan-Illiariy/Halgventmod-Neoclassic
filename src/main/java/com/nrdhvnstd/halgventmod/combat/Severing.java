package com.nrdhvnstd.halgventmod.combat;

import com.nrdhvnstd.halgventmod.network.SeveredHeadPayload;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;
import com.nrdhvnstd.halgventmod.quality.DamageKind;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Голова с плеч: горизонтальный взмах (слева или справа) рубящим или режущим, дошедший до тела, по голове или шее НПС,
 * который от него умер, или по передней
 * половине лежащего поверженного игрока (голова у него впереди, а не наверху) — того он сразу добивает. Голову и тело
 * без головы рисует клиент (client/SeveredHeads). Дробящий и колющий голову не отнимают.
 */
final class Severing {
    private static final double FLY_FROM_HIT = 0.22;  // голова летит прочь от бьющего ⚠️
    private static final double FLY_UP = 0.32;
    private static final double FLY_SPREAD = 0.08;

    private Severing() {}

    /** После нанесения урона; wasDowned — цель лежала повержена до удара. */
    static void check(LivingEntity attacker, LivingEntity target, DamageKind kind, HitZone zone, Vec3 point,
                      DamageSource source, boolean wasDowned) {
        CombatState swing = Combat.state(attacker);
        boolean horizontal = swing.current != null && swing.current.shape() == AttackKind.SLASH
                && (swing.side == StrikeSide.LEFT || swing.side == StrikeSide.RIGHT);
        if (!horizontal || (kind != DamageKind.CHOPPING && kind != DamageKind.CUTTING) || !Blood.bleeds(target)) {
            return;
        }
        if (wasDowned) {
            if (!toHead(target, point)) {
                return;
            }
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(source, target.getMaxHealth() * 100); // отрублена — живым не останется
            }
            if (target.isAlive()) {
                target.kill();
            }
        } else if (!(target instanceof HalgNpc) || !target.isDeadOrDying()
                || (zone != HitZone.CROWN && zone != HitZone.FACE && zone != HitZone.NECK)) {
            return;
        }
        Vec3 away = target.position().subtract(attacker.position()).multiply(1, 0, 1);
        away = away.lengthSqr() > 1.0E-4 ? away.normalize().scale(FLY_FROM_HIT) : Vec3.ZERO;
        var random = target.getRandom();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new SeveredHeadPayload(target.getId(),
                (float) (away.x + (random.nextDouble() - 0.5) * FLY_SPREAD), (float) FLY_UP,
                (float) (away.z + (random.nextDouble() - 0.5) * FLY_SPREAD)));
    }

    // Лежащий ползёт головой вперёд: попали перед серединой тела
    private static boolean toHead(LivingEntity target, Vec3 point) {
        float yaw = target.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 offset = point.subtract(target.position());
        return -offset.x * Mth.sin(yaw) + offset.z * Mth.cos(yaw) > 0;
    }
}
