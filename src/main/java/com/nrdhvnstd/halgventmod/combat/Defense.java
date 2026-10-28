package com.nrdhvnstd.halgventmod.combat;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.network.CombatHitPayload;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.sound.SoundPlay;

import java.util.function.IntConsumer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Защита (ТЗ боя, 5.1–5.2): блок на ПКМ, парирование и рипост, сбой блока. Блок держит удары спереди в секторе 180°
 * и стоит выносливости; блок оружием — только со своей стороны (StrikeSide: по движению мыши, как удар), щит — любой
 * спереди. Удары мобов держит блок любой стороны. Поднятый перед самым ударом блок — парирование: дешевле, атакующий отлетает, а парировавший
 * получает окно рипоста. Не хватило выносливости — сбой: часть урона проходит, блок отключён, оглушение.
 *
 * Щит во второй руке (5.6, 5.7): блок щитом дешевле (профиль щита), изнашивает щит и единственный держит стрелы и
 * болты — спереди, без парирования. Ванильный блок щитом у игроков отключён.
 *
 * Кнопка блока (blockHeld) и поднятый блок (blocking) — разное: блок поднимается только в покое, за raise тиков
 * из профиля оружия, и опускается, когда начинается удар, оглушение или кнопку отпустили.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Defense {
    // Меняйте здесь ⚠️
    public static final float BLOCK_COST = 0.6F;          // цена блока = урон до брони × 0.6 × ...
    public static final float BLUNT_COST = 1.5F;          // дробящий
    public static final float AXE_COST = 1.25F;           // топоры
    public static final float HEAVY_BLOCK = 1.5F;         // заряженный удар
    public static final int PARRY_WINDOW = 5;             // тиков с подъёма блока — парирование
    public static final float PARRY_COST = 0.5F;
    public static final int RIPOSTE_WINDOW = 10;          // тиков после парирования удар — рипост
    public static final float RIPOSTE_WINDUP = 0.6F;
    public static final float RIPOSTE_STAMINA = 0.5F;
    public static final int BLOCKED_RECOVERY = 4;         // атакующему, чей удар принял блок
    public static final int PARRIED_RECOVERY = 8;         // атакующему, которого парировали
    public static final int GUARD_BREAK_LOCK = 60;        // сбой блока: 3 с без блока
    public static final int GUARD_BREAK_STUN = 16;
    public static final double BLOCK_MOVE = -0.3;         // ходьба в блоке ×0.7
    public static final double STUN_MOVE = -0.5;          // в оглушении ×0.5
    public static final float BLOCK_REGEN = 0.5F;         // восстановление выносливости в блоке
    // Контратака (5.3): тот же удар в ответ, начатый не раньше COUNTER_WINDOW тиков назад
    public static final int COUNTER_WINDOW = 4;
    public static final float COUNTER_STAMINA = 8;
    // Щит (5.6): цена блока — из профиля shield.json (block.cost); прочность щита — цена блока × SHIELD_WEAR
    public static final float SHIELD_WEAR = 0.5F;
    // Удар моба принят блоком — моба отбрасывает (этап 10)
    public static final double MOB_BLOCK_PUSH = 0.4;
    public static final double MOB_PARRY_PUSH = 0.8;

    private static final ResourceLocation MOVE_ID = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "combat_move");

    private Defense() {}

    // ---------------------------------------------------------------- подъём и опускание

    /** Каждый тик бойца: поднять блок, если кнопка зажата и можно; опустить, если нельзя. */
    static void tickGuard(LivingEntity entity, CombatState state, long now) {
        boolean can = state.blockHeld && state.phase == Phase.IDLE && now >= state.blockLockedUntil
                && !(entity instanceof Player player && Combat.busy(player));
        if (can && !state.blocking) {
            WeaponProfile profile = entity instanceof Player player ? guardProfile(player) : null;
            state.blocking = true;
            state.blockStart = now + (profile != null ? profile.blockRaise() : 0); // с этого тика блок держит
            entity.setSprinting(false);
            Combat.sync(entity, state);
            updateMovement(entity, state);
        } else if (!can && state.blocking) {
            lower(entity, state);
        }
    }

    static void lower(LivingEntity entity, CombatState state) {
        if (state.blocking) {
            state.blocking = false;
            Combat.sync(entity, state);
            updateMovement(entity, state);
        }
    }

    /** Замедление: в блоке ×0.7, в оглушении ×0.5. */
    static void updateMovement(LivingEntity entity, CombatState state) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double slow = state.phase == Phase.STUN ? STUN_MOVE : state.blocking ? BLOCK_MOVE : 0;
        if (slow == 0) {
            speed.removeModifier(MOVE_ID);
        } else {
            speed.addOrUpdateTransientModifier(new AttributeModifier(MOVE_ID, slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ---------------------------------------------------------------- удар в блок

    /**
     * Удар attacker по target пришёл в точку. Возвращает долю урона, которая проходит в тело: 1 — защиты не было,
     * 0 — удар принят блоком или парирован, между — сбой блока (остаток прошёл). raw — урон удара до брони и зоны.
     */
    static float defend(LivingEntity attacker, LivingEntity target, CombatState attack, DamageKind kind, float raw,
                        Vec3 point, long now) {
        CombatState guard = Combat.state(target);
        if (!guard.blocking || now < guard.blockStart || !(target instanceof Player defender) || !inFront(target, attacker)) {
            return 1.0F;
        }
        // Удар, сбивающий блок (щитом — этап 6): как пинок по блоку
        if (attack.current.effect().equals("guard_break")) {
            guard.blockLockedUntil = now + Kick.KICK_STUN;
            lower(target, guard);
            Combat.stun(target, guard, Kick.KICK_STUN, now);
            outcome(attacker, target, CombatHitPayload.Outcome.GUARD_BREAK, kind, point);
            Combat.recoil(attacker, attack, 0, now);
            Combat.debugBoth(attacker, target, Component.translatable("combat.halgventmod.debug.kick_guard"));
            return 0;
        }
        // Блок оружием держит только удар со своей стороны (щит — любой спереди)
        StrikeSide needed = attack.current.shape() == AttackKind.STAB ? StrikeSide.DOWN : attack.side.blockedBy();
        if (!WeaponProfiles.hasShield(defender) && guard.blockSide != needed) {
            Combat.debugBoth(attacker, target, Component.translatable("combat.halgventmod.debug.wrong_side",
                    Component.translatable(guard.blockSide.langKey()), Component.translatable(needed.langKey())));
            return 1.0F;
        }
        boolean parry = now - guard.blockStart < PARRY_WINDOW;
        float cost = cost(attacker, defender, attack, kind, raw) * (parry ? PARRY_COST : 1.0F);
        return resolve(attacker, defender, guard, cost, parry, kind, point, now,
                extra -> Combat.recoil(attacker, attack, extra, now));
    }

    /**
     * Удар моба по игроку (этап 10) — блок и парирование, как от игрока; окно парирования — от ванильного удара.
     * Цена — по урону моба и типу (дробящий ×1.5); моба, чей удар принят, отбрасывает. Доля урона в тело, как у defend.
     */
    static float defendMob(LivingEntity attacker, Player defender, DamageKind kind, float raw, Vec3 point, long now) {
        CombatState guard = Combat.state(defender);
        if (!guard.blocking || now < guard.blockStart || !inFront(defender, attacker)) {
            return 1.0F;
        }
        boolean parry = now - guard.blockStart < PARRY_WINDOW;
        WeaponProfile own = guardProfile(defender);
        float cost = raw * BLOCK_COST * (kind == DamageKind.BLUNT ? BLUNT_COST : 1.0F)
                * (own != null ? own.blockCost() : 1.0F) * (parry ? PARRY_COST : 1.0F);
        return resolve(attacker, defender, guard, cost, parry, kind, point, now, extra -> {
            Vec3 away = attacker.position().subtract(defender.position());
            attacker.knockback(extra >= PARRIED_RECOVERY ? MOB_PARRY_PUSH : MOB_BLOCK_PUSH, -away.x, -away.z);
        });
    }

    // Хватило выносливости — удар принят (атакующий отлетает: recoil(лишние тики возврата)); нет — сбой блока
    private static float resolve(LivingEntity attacker, Player defender, CombatState guard, float cost, boolean parry,
                                 DamageKind kind, Vec3 point, long now, IntConsumer recoil) {
        LivingEntity target = defender;
        float stamina = Stamina.exhausted(defender) ? 0 : Stamina.get(defender);
        if (stamina >= cost) {
            Stamina.spend(defender, cost);
            wearShield(defender, cost);
            outcome(attacker, target, parry ? CombatHitPayload.Outcome.PARRY : CombatHitPayload.Outcome.BLOCK, kind, point);
            SoundPlay.atChest(target, blockSound(defender), SoundSource.PLAYERS, 1.0F, 0.1F);
            if (parry) {
                guard.riposteUntil = now + RIPOSTE_WINDOW;
            }
            recoil.accept(parry ? PARRIED_RECOVERY : BLOCKED_RECOVERY);
            Combat.debugBoth(attacker, target, Component.translatable(parry ? "combat.halgventmod.debug.parry"
                    : "combat.halgventmod.debug.block", String.format("%.1f", cost)));
            return 0;
        }
        // Сбой блока: выносливость кончилась, остаток урона — в тело, блок отключён, оглушение
        Stamina.spend(defender, cost);
        wearShield(defender, cost);
        guard.blockLockedUntil = now + GUARD_BREAK_LOCK;
        lower(target, guard);
        Combat.stun(target, guard, GUARD_BREAK_STUN, now);
        outcome(attacker, target, CombatHitPayload.Outcome.GUARD_BREAK, kind, point);
        SoundPlay.atChest(target, ModSounds.COMBAT_BLOCK_BROKEN.get(), SoundSource.PLAYERS, 1.0F, 0.1F);
        float pass = cost <= 0 ? 1.0F : 1.0F - stamina / cost;
        Combat.debugBoth(attacker, target, Component.translatable("combat.halgventmod.debug.guard_break",
                String.format("%.0f", pass * 100)));
        return pass;
    }

    /**
     * Контратака (5.3): цель замахивается тем же ударом (с той же стороны, укол на укол), начала не
     * раньше COUNTER_WINDOW тиков назад, атакующий спереди и выносливости хватает — удар отбит, удар цели сразу идёт.
     */
    static boolean counter(LivingEntity attacker, LivingEntity target, CombatState attack, DamageKind kind, Vec3 point, long now) {
        CombatState guard = Combat.state(target);
        boolean stab = attack.current.shape() == AttackKind.STAB;
        if (!(target instanceof Player defender) || guard.phase != Phase.WINDUP || guard.current == null
                || (guard.current.shape() == AttackKind.STAB) != stab || (!stab && guard.side != attack.side)
                || now - guard.phaseStart > COUNTER_WINDOW
                || !inFront(target, attacker) || Stamina.exhausted(defender) || Stamina.get(defender) < COUNTER_STAMINA) {
            return false;
        }
        Stamina.spend(defender, COUNTER_STAMINA);
        guard.counter = true;
        guard.charging = false;
        outcome(attacker, target, CombatHitPayload.Outcome.COUNTER, kind, point);
        SoundPlay.atChest(target, ModSounds.COMBAT_BLOCK_WEAPON.get(), SoundSource.PLAYERS, 1.0F, 0.1F);
        Combat.debugBoth(attacker, target, Component.translatable("combat.halgventmod.debug.counter"));
        Combat.recoil(attacker, attack, BLOCKED_RECOVERY, now);
        Combat.toRelease(target, guard, now);
        return true;
    }

    /** Цена блока (5.1): урон × 0.6 × тип × заряд × оружие блокирующего × особый удар (булава ×2) × (1 − сегмент). */
    static float cost(LivingEntity attacker, Player defender, CombatState attack, DamageKind kind, float raw) {
        WeaponClass weaponClass = attack.profile.weaponClass();
        float type = kind == DamageKind.BLUNT ? BLUNT_COST
                : weaponClass == WeaponClass.AXE || weaponClass == WeaponClass.GREATAXE ? AXE_COST : 1.0F;
        WeaponProfile own = guardProfile(defender);
        return raw * BLOCK_COST * type * (attack.heavy ? HEAVY_BLOCK : 1.0F) * (own != null ? own.blockCost() : 1.0F)
                * attack.current.blockCost();
    }

    /** Атакующий спереди: в полусфере взгляда цели (по горизонтали). */
    static boolean inFront(LivingEntity target, LivingEntity attacker) {
        return inFront(target, attacker.position());
    }

    static boolean inFront(LivingEntity target, Vec3 from) {
        Vec3 look = target.getViewVector(1.0F).multiply(1, 0, 1);
        Vec3 toAttacker = from.subtract(target.position()).multiply(1, 0, 1);
        return look.dot(toAttacker) > 0;
    }

    /** Чем блокирует: щит во второй руке, иначе оружие в основной. */
    static WeaponProfile guardProfile(Player player) {
        WeaponProfile shield = WeaponProfiles.hasShield(player) ? WeaponProfiles.ofClass(player, WeaponClass.SHIELD) : null;
        return shield != null ? shield : WeaponProfiles.of(player, player.getMainHandItem());
    }

    private static SoundEvent blockSound(Player player) {
        if (!WeaponProfiles.hasShield(player)) {
            return ModSounds.COMBAT_BLOCK_WEAPON.get();
        }
        return player.getOffhandItem().is(Items.SHIELD) ? ModSounds.COMBAT_BLOCK_SHIELD_WOOD.get()
                : ModSounds.COMBAT_BLOCK_SHIELD_METAL.get();
    }

    // Щит принял удар — теряет прочность по цене блока; разбился — звук
    private static void wearShield(Player player, float cost) {
        ItemStack shield = player.getOffhandItem();
        if (!WeaponProfiles.hasShield(player) || !shield.isDamageableItem() || player.getAbilities().instabuild) {
            return;
        }
        shield.hurtAndBreak(Math.max(1, Math.round(cost * SHIELD_WEAR)), player, EquipmentSlot.OFFHAND);
        if (shield.isEmpty()) {
            SoundPlay.atChest(player, ModSounds.ARMOR_BREAK.get(), SoundSource.PLAYERS, 1.0F, 0.05F);
        }
    }

    // ---------------------------------------------------------------- стрелы и ванильный щит

    // Стрелы и болты держит только щит (5.7): спереди, по цене блока, без парирования
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player defender) || defender.level().isClientSide
                || !(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }
        CombatState guard = Combat.state(defender);
        long now = defender.level().getGameTime();
        if (!guard.blocking || now < guard.blockStart || !WeaponProfiles.hasShield(defender)
                || !inFront(defender, projectile.position())) {
            return;
        }
        WeaponProfile shield = guardProfile(defender);
        float cost = event.getAmount() * BLOCK_COST * (shield != null ? shield.blockCost() : 1.0F);
        float stamina = Stamina.exhausted(defender) ? 0 : Stamina.get(defender);
        Stamina.spend(defender, cost);
        wearShield(defender, cost);
        if (stamina >= cost) {
            SoundPlay.atChest(defender, blockSound(defender), SoundSource.PLAYERS, 1.0F, 0.1F);
            Combat.debug(defender, Component.translatable("combat.halgventmod.debug.arrow_block", String.format("%.1f", cost)));
            event.setCanceled(true); // стрела отскакивает
            return;
        }
        guard.blockLockedUntil = now + GUARD_BREAK_LOCK;
        lower(defender, guard);
        Combat.stun(defender, guard, GUARD_BREAK_STUN, now);
        SoundPlay.atChest(defender, ModSounds.COMBAT_BLOCK_BROKEN.get(), SoundSource.PLAYERS, 1.0F, 0.1F);
        float pass = cost <= 0 ? 1.0F : 1.0F - stamina / cost;
        Combat.debug(defender, Component.translatable("combat.halgventmod.debug.guard_break", String.format("%.0f", pass * 100)));
        event.setAmount(event.getAmount() * pass);
    }

    // Ванильный блок щитом у игроков не работает — щитом блокирует бой
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setBlocked(false);
        }
    }

    private static void outcome(LivingEntity attacker, LivingEntity target, CombatHitPayload.Outcome outcome,
                                DamageKind kind, Vec3 point) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new CombatHitPayload(attacker.getId(), target.getId(),
                outcome, HitZone.of(target, point), kind, point));
    }
}
