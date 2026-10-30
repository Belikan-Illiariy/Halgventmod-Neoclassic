package com.nrdhvnstd.halgventmod.combat;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.sound.SoundTags;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Мобы и стрелы против игрока (ТЗ боя, 7.5, этап 10); стрелы — и против НПС и мобов (части тела, у НПС — слои брони). Удар моба и стрела бьют как в ванили — тот же урон, тот же
 * источник (стрела втыкается, моб злится, сообщение о смерти ванильное), — но урон пересчитывается через бой:
 * блок и парирование (окно — от момента ванильного удара), зона, 4 слоя брони, боль. Ванильная броня для них
 * обнуляется — её работу делают слои.
 *
 * Тип урона: моб с оружием из профилей — тип его взмаха; паук и волк — режущий; прочие без оружия — дробящий;
 * стрела и болт — проникающий (с качеством лука). Игрок по мобу бьёт трассой (Combat) — у мобов слоёв нет.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class MobAttacks {
    // Меняйте здесь ⚠️
    public static final float ARROW_PENETRATION = 2.0F;   // гранёный наконечник: латы пробивает с трудом
    public static final float ARROW_ACCURACY = 0.05F;
    public static final float UNARMED_PENETRATION = 0.5F; // зубы и когти
    public static final float MOB_ACCURACY = 0.05F;

    private MobAttacks() {}

    /**
     * Этот урон бой пересчитывает: по игроку — удар моба в ближнем бою или стрела; по НПС и мобам — стрела (части тела,
     * у НПС — слои брони, искры, кровь, травмы). Стойка для брони — не цель.
     */
    public static boolean redirected(LivingEntity target, DamageSource source) {
        if (CombatDamageTypes.kindOf(source) != null || target instanceof ArmorStand) {
            return false;
        }
        AbstractArrow arrow = arrow(source);
        return arrow != null ? arrow.getOwner() != target : target instanceof Player && melee(source) != null;
    }

    private static AbstractArrow arrow(DamageSource source) {
        return source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof LivingEntity ? arrow : null;
    }

    private static LivingEntity melee(DamageSource source) {
        return source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player)
                && source.getDirectEntity() == attacker && source.is(SoundTags.MELEE) ? attacker : null;
    }

    // После щита (Defense, HIGH) и качества лука (QualityEvents): урон уже со щитом и луком
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !redirected(target, event.getSource())) {
            return;
        }
        long now = target.level().getGameTime();
        float amount = event.getAmount();
        AbstractArrow arrow = arrow(event.getSource());
        LivingEntity attacker;
        DamageKind kind;
        float penetration;
        float accuracy;
        Vec3 point;
        if (arrow != null) {
            attacker = (LivingEntity) arrow.getOwner();
            kind = DamageKind.PENETRATING;
            ItemStack bow = arrow.getWeaponItem();
            penetration = ARROW_PENETRATION * (1.0F + (bow != null ? GearQuality.get(bow).penetration() : 0));
            accuracy = ARROW_ACCURACY;
            point = arrow.position();
        } else {
            attacker = melee(event.getSource());
            ItemStack weapon = attacker.getMainHandItem();
            WeaponProfile profile = weapon.isEmpty() ? null : WeaponProfiles.of(attacker, weapon);
            WeaponProfile.Attack swing = profile == null ? null : profile.attacks().values().stream()
                    .filter(attack -> attack.shape() == AttackKind.SLASH).findFirst()
                    .orElse(profile.attacks().values().stream().findFirst().orElse(null));
            kind = swing != null ? swing.type()
                    : attacker instanceof Spider || attacker instanceof Wolf ? DamageKind.CUTTING : DamageKind.BLUNT;
            penetration = profile != null ? profile.penetration() * (1.0F + GearQuality.get(weapon).penetration())
                    : UNARMED_PENETRATION;
            accuracy = profile != null ? profile.accuracy() : MOB_ACCURACY;
            point = closest(target.getBoundingBox(), attacker.getEyePosition());
            float pass = Defense.defendMob(attacker, (Player) target, kind, amount, point, now); // ближний — только по игроку
            if (pass <= 0) {
                event.setCanceled(true);
                return;
            }
            amount *= pass;
        }
        HitZone zone = HitZone.of(target, point);
        amount *= zone.damage();
        DamagePipeline.Result result = Combat.through(attacker, target, kind, amount, zone, penetration, accuracy);
        if (result.body() < Combat.DEFLECTED) {
            Combat.deflected(attacker, target, kind, amount, zone, point, Combat.metalShare(result, amount));
            event.setCanceled(true);
            return;
        }
        event.setAmount(result.body());
        // Ванильная броня второй раз не гасит — это сделали слои (у мобов слоёв нет — их ванильная броня работает)
        if (Combat.layered(target)) {
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0);
        }
        Combat.landed(attacker, target, kind, amount, result, zone, point, now, true);
    }

    private static Vec3 closest(AABB box, Vec3 from) {
        return new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY),
                Mth.clamp(from.z, box.minZ, box.maxZ));
    }
}
