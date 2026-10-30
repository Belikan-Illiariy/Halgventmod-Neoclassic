package com.nrdhvnstd.halgventmod.combat;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;

import com.nrdhvnstd.halgventmod.combat.anim.CombatMoves;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.network.CombatInputPayload;
import com.nrdhvnstd.halgventmod.quality.DamageKind;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Боевой ИИ человека с оружием (по образцу Kingdom Come Combat, HumanoidCombatAiTicker): бьётся по правилам нашего
 * боя — «нажимает» те же кнопки, что игрок (Combat.input), поэтому для него действуют выносливость (Fighter), блок по
 * сторонам, парирование и мастер-удар, приёмы, столкновение клинков.
 * <ul>
 *   <li>держит дистанцию удара: дальше — подходит, вплотную — отступает, в меру — кружит боком; выдохся — отходит;</li>
 *   <li>бьёт не чаще своего промежутка, с разных сторон, иногда уколом и заряженным; с шансом задумывает приём
 *       (ComboMoves) и ведёт его последовательность; после парирования — сразу мастер-удар;</li>
 *   <li>на чужой замах с шансом ставит блок нужной стороны (умелый — иногда в последний миг, парированием),
 *       неумелый может ошибиться стороной.</li>
 * </ul>
 * Умение — Skill: стража, бывалые (поборники, брутальные пиглины, скелеты-иссушители) и прочие.
 */
public class CombatAiGoal extends Goal {
    /**
     * Умение бойца ⚠️ (подбирать в игре). aggression 0…1 — напор: на дистанции удара шагает вперёд, а не только
     * кружит; отходит отдышаться позже (при меньшей выносливости).
     */
    public record Skill(float block, float perfect, float wrongSide, int minInterval, int maxInterval, float combo,
                        float heavy, float stab, float aggression) {}

    public static final Skill GUARD = new Skill(0.75F, 0.3F, 0.1F, 10, 18, 0.5F, 0.25F, 0.1F, 0.7F);
    public static final Skill VETERAN = new Skill(0.55F, 0.15F, 0.2F, 18, 30, 0.2F, 0.25F, 0.1F, 0.4F);
    public static final Skill COMMON = new Skill(0.3F, 0.03F, 0.35F, 22, 40, 0.05F, 0.15F, 0.05F, 0.2F);

    /**
     * Стражник со своим нравом (HalgNpc.aggression, 0…1): напористый бьёт чаще, чаще приёмы и заряженные, давит и отходит
     * позже, но реже ставит блок; осторожный — наоборот. Мастерство (парирование, точность стороны) у стражи одно.
     */
    public static Skill guard(float aggression) {
        float a = Math.max(0, Math.min(1, aggression));
        return new Skill(0.85F - 0.2F * a, GUARD.perfect(), GUARD.wrongSide(), Math.round(16 - 8 * a), Math.round(28 - 12 * a),
                0.3F + 0.3F * a, 0.15F + 0.15F * a, GUARD.stab(), a);
    }

    // Меняйте здесь ⚠️
    private static final double CHASE_RANGE = 24;
    private static final double REACH_MARGIN = 0.3;     // бьёт с дистанции клинка чуть меньше полной
    private static final double TOO_CLOSE = 0.55;       // доля дистанции удара — ближе отступает
    private static final double PRESS_FROM = 0.7;       // дальше этой доли — напористый шагает вперёд
    private static final float PRESS_STEP = 0.6F;
    private static final double TIRED_DISTANCE = 4.5;
    private static final float TIRED_STAMINA = 20;
    private static final float BLOCK_STAMINA = 12;
    private static final int HEAVY_HOLD = 14;
    private static final int COMBO_STEP = 6;            // между ударами приёма
    private static final float STRAFE_SPEED = 0.45F;

    private final PathfinderMob mob;
    private final Skill skill;
    private final double speed;
    private final ArrayDeque<StrikeSide> plan = new ArrayDeque<>();
    private int cooldown;
    private int hold = -1;
    private long enemySwing = Long.MIN_VALUE;
    private boolean blockPlanned;
    private boolean perfect;
    private StrikeSide blockSide = StrikeSide.UP;
    private int strafe = 1;
    private int strafeTicks;

    public CombatAiGoal(PathfinderMob mob, Skill skill, double speed) {
        this.mob = mob;
        this.skill = skill;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** С оружием, у которого есть анимации боя (кулак и щит — нет). */
    public static boolean armed(LivingEntity entity) {
        return CombatMoves.variant(WeaponProfiles.of(entity, entity.getMainHandItem())) != null;
    }

    /** Умение сейчас (стражник — со своим нравом, HalgNpc). */
    protected Skill skill() {
        return skill;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && armed(mob);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && armed(mob) && mob.distanceToSqr(target) < CHASE_RANGE * CHASE_RANGE;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        cooldown = 10;
        plan.clear();
        input(CombatInputPayload.Action.STANCE_ON, AttackKind.SLASH, StrikeSide.RIGHT);
    }

    @Override
    public void stop() {
        if (Combat.state(mob).blockHeld) {
            input(CombatInputPayload.Action.BLOCK_UP, AttackKind.SLASH, blockSide);
        }
        if (hold >= 0) {
            input(CombatInputPayload.Action.ATTACK_RELEASE, AttackKind.SLASH, StrikeSide.RIGHT);
            hold = -1;
        }
        input(CombatInputPayload.Action.STANCE_OFF, AttackKind.SLASH, StrikeSide.RIGHT);
        mob.getNavigation().stop();
        mob.getMoveControl().strafe(0, 0);
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        RandomSource random = mob.getRandom();
        CombatState me = Combat.state(mob);
        CombatState them = Combat.state(target);
        long now = mob.level().getGameTime();
        Fighter.regen(mob);
        face(target);
        WeaponProfile profile = WeaponProfiles.of(mob, mob.getMainHandItem());
        double reach = (profile != null ? profile.bladeLength() : 1.0) + SwingPath.ARM - REACH_MARGIN;
        double distance = mob.distanceTo(target);
        boolean tired = Fighter.exhausted(mob) || Fighter.stamina(mob) < TIRED_STAMINA * (1 - skill().aggression()); // напористый отходит позже

        // ---- защита: на чужой замах — блок нужной стороны (или в последний миг — парирование)
        boolean incoming = (them.phase == Phase.WINDUP || them.phase == Phase.RELEASE) && them.current != null
                && distance < reach + 2 && Defense.inFront(mob, target) && facing(target, mob);
        if (incoming && them.lastStrikeAt != enemySwing) {
            enemySwing = them.lastStrikeAt;
            blockPlanned = me.phase == Phase.IDLE && random.nextFloat() < skill().block()
                    && Fighter.stamina(mob) >= BLOCK_STAMINA;
            perfect = random.nextFloat() < skill().perfect();
            blockSide = them.current.shape() == AttackKind.STAB ? StrikeSide.DOWN : them.side.blockedBy();
            if (random.nextFloat() < skill().wrongSide()) {
                blockSide = StrikeSide.values()[random.nextInt(StrikeSide.values().length)];
            }
        }
        if (blockPlanned && incoming && me.phase == Phase.IDLE && !me.blockHeld
                && (!perfect || them.phase == Phase.RELEASE)) {
            input(CombatInputPayload.Action.BLOCK_DOWN, AttackKind.SLASH, blockSide);
        }
        if (!incoming && me.blockHeld) {
            input(CombatInputPayload.Action.BLOCK_UP, AttackKind.SLASH, blockSide);
            blockPlanned = false;
        }

        // ---- нападение
        if (hold >= 0 && --hold < 0) {
            input(CombatInputPayload.Action.ATTACK_RELEASE, AttackKind.SLASH, StrikeSide.RIGHT);
        }
        if (now <= me.riposteUntil) {
            cooldown = 0; // парировал — сразу мастер-удар
        }
        if (cooldown > 0) {
            cooldown--;
        }
        // Лежачего добивают сверху; клинок по такой низкой цели может и пройти поверху — поэтому удар засчитывается сам
        boolean finishing = target instanceof Player lying && Downed.isDowned(lying);
        if (finishing) {
            plan.clear();
            if (me.phase == Phase.RELEASE && distance <= reach + 0.5 && target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(CombatDamageTypes.source(mob, DamageKind.CHOPPING), target.getMaxHealth() * 100);
            }
        }
        if (me.phase == Phase.IDLE && !me.blockHeld && hold < 0 && cooldown <= 0 && distance <= reach && !tired) {
            if (plan.isEmpty() && profile != null && random.nextFloat() < skill().combo()) {
                List<ComboMoves.Combo> combos = ComboMoves.forWeapon(profile.weaponClass());
                if (!combos.isEmpty()) {
                    plan.addAll(combos.get(random.nextInt(combos.size())).sequence());
                }
            }
            boolean planned = !plan.isEmpty();
            StrikeSide side = finishing ? StrikeSide.UP : planned ? plan.poll() : StrikeSide.values()[random.nextInt(StrikeSide.values().length)];
            input(CombatInputPayload.Action.ATTACK_START, AttackKind.SLASH, side);
            hold = !planned && !finishing && random.nextFloat() < skill().heavy() ? HEAVY_HOLD : 0;
            cooldown = !plan.isEmpty() ? COMBO_STEP
                    : skill().minInterval() + random.nextInt(Math.max(1, skill().maxInterval() - skill().minInterval()));
        }

        // ---- движение
        if (tired && distance < TIRED_DISTANCE) {
            Vec3 away = mob.position().subtract(target.position()).normalize().scale(3).add(mob.position());
            mob.getNavigation().moveTo(away.x, away.y, away.z, speed);
        } else if (distance > reach + 0.5) {
            mob.getNavigation().moveTo(target, speed);
        } else {
            mob.getNavigation().stop();
            if (--strafeTicks <= 0) {
                strafeTicks = 20 + random.nextInt(30);
                strafe = random.nextBoolean() ? 1 : -1;
            }
            // Вплотную — отступает; напористый в меру дистанции давит вперёд
            float forward = distance < reach * TOO_CLOSE ? -0.5F : distance > reach * PRESS_FROM ? PRESS_STEP * skill().aggression() : 0;
            mob.getMoveControl().strafe(forward, strafe * STRAFE_SPEED);
        }
    }

    // Лицом и взглядом — на грудь противника (по взгляду идёт клинок, BladePose)
    private void face(LivingEntity target) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.7, 0).subtract(mob.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setXRot(Mth.clamp(pitch, -60, 60));
        mob.getLookControl().setLookAt(target, 30, 30);
    }

    private static boolean facing(LivingEntity attacker, LivingEntity at) {
        Vec3 to = at.position().subtract(attacker.position()).normalize();
        return attacker.getViewVector(1.0F).dot(to) > 0.3;
    }

    private void input(CombatInputPayload.Action action, AttackKind kind, StrikeSide side) {
        Combat.input(mob, action, kind, side);
    }
}
