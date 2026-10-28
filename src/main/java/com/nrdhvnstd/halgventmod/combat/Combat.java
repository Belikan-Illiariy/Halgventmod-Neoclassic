package com.nrdhvnstd.halgventmod.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.brigadier.Command;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.character.CharacterCreation;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.draw.Drawing;
import com.nrdhvnstd.halgventmod.needs.Pain;
import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.network.CombatHitPayload;
import com.nrdhvnstd.halgventmod.network.CombatInputPayload;
import com.nrdhvnstd.halgventmod.network.CombatStatePayload;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.quality.Dullness;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.social.CuffsRules;
import com.nrdhvnstd.halgventmod.sound.CombatSounds;
import com.nrdhvnstd.halgventmod.sound.SoundPlay;

import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Бой по образцу Chivalry II (docs/TZ_chivalry_combat.md). Здесь — фазы удара на сервере: клиент присылает только
 * намерения (CombatInputPayload), сервер проверяет и ведёт бойца IDLE → WINDUP → RELEASE → RECOVERY → IDLE,
 * рассылая каждую смену фазы (CombatStatePayload). Тикают только бойцы не в покое.
 *
 * Этапы 1–2: удар (ЛКМ, сторона — по движению мыши: слева, справа, сверху, снизу) и укол; заряд удержанием,
 * финт, смена удара в замахе, комбо.
 * Этап 3: попадание — трасса клинка по SwingPath каждый тик удара, стена, до двух целей у взмаха, зоны, древко.
 * Урон из профиля × зона × заряд × качество × затупление, ванильный hurt.
 * Этап 4: блок, парирование, рипост, сбой блока — Defense.
 * Этап 5: контратака (Defense), пинок (Kick), сбивание замаха, оглушение, особые удары, удар с разбега.
 * Этап 6: щит — блок щитом и стрелы (Defense), удар щитом на R.
 * Этап 7: урон сквозь 4 слоя брони пятью типами урона — wound, DamagePipeline.
 * Этап 8: одышка — бить можно при нуле выносливости, но слабее и медленнее (winded).
 * Этап 11: хват X (второй набор ударов); анимации — на клиенте (client/CombatAnimation).
 * Этап 10: удары мобов и стрелы по игроку — MobAttacks (тот же путь через блок, зоны, слои и боль).
 * Этап 9: боль (needs/Pain) — копится от урона по телу, замедляет и ослабляет удар, шок не даёт бить и блокировать. Ванильный удар оружием из профилей (и кулаком) отменяется.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Combat {
    // Меняйте здесь ⚠️
    public static final int INPUT_RATE = 10;         // пакетов ввода в тик от игрока, больше — отбрасываются
    public static final double KNOCKBACK = 0.3;      // отбрасывание при попадании
    // Заряд (4.2): держит кнопку дольше HEAVY_MIN_HOLD — заряженный; замах тянется, пока держит, но не дольше +HEAVY_MAX_EXTRA
    public static final int HEAVY_MIN_HOLD = 6;
    public static final int HEAVY_MAX_EXTRA = 16;
    public static final float HEAVY_DAMAGE = 1.35F;
    public static final float HEAVY_STAMINA = 1.5F;
    // Финт и смена удара (4.4): можно, пока до конца замаха больше FEINT_LOCK тиков
    public static final int FEINT_LOCK = 3;
    public static final float FEINT_STAMINA = 6.0F;
    public static final float MORPH_WINDUP = 0.8F;
    // Комбо (4.5): кнопка удара во время удара или в первые COMBO_WINDOW тиков возврата
    public static final int COMBO_WINDOW = 4;
    public static final float COMBO_WINDUP = 0.75F;
    // Трасса клинка (6.2)
    public static final int BLADE_SAMPLES = 5;       // точек вдоль клинка
    public static final double HIT_INFLATE = 0.1;    // хитбокс цели шире на столько
    public static final int SLASH_TARGETS = 2;       // взмах задевает до двух, удар сверху и укол — одного
    public static final int WALL_RECOVERY = 6;       // отскок от стены — возврат длиннее
    public static final double SHAFT_RANGE = 1.5;    // копьё и древковое ближе — бьют древком (6.4)
    public static final float SHAFT_DAMAGE = 0.4F;
    // Удар с разбега (4.7)
    public static final double SPRINT_REACH = 0.5;
    public static final float SPRINT_DAMAGE = 1.25F;
    public static final float SPRINT_STAMINA = 1.25F;
    public static final int SPRINT_LOCK = 20;        // после него бег закрыт
    // Сбивание замаха и оглушение (5.5)
    public static final int FLINCH_TICKS = 6;
    public static final float STUN_DAMAGE = 10;      // дробящий от стольки — оглушение
    public static final int STUN_TICKS = 20;
    // Подсечка крюком (особый удар древкового)
    public static final int HOOK_TICKS = 40;
    public static final int HOOK_LEVEL = 1;          // Замедление II
    // Хват X (10): смена — столько тиков ни удара, ни блока
    public static final int GRIP_TICKS = 8;
    // Урон сквозь броню (7.4)
    public static final float DEFLECTED = 0.05F;     // меньше этого по телу — погашено бронёй целиком
    public static final float METAL_PAIN = 0.1F;     // боль от урона, погашенного металлом
    public static final float BLUNT_STAMINA = 0.5F;  // дробящий по телу снимает выносливость цели за единицу
    public static final float BLEED_MIN = 3;         // кровотечение — от такого урона по телу
    public static final float SEVERE_CHOPPING = 15;  // рубящий от стольки — сразу сильное
    // Одышка (9): выносливость на нуле — бить можно, но слабее и медленнее; блок пробивается сразу
    public static final float WINDED_DAMAGE = 0.5F;  // урон и пробивание
    public static final float WINDED_SLOW = 1.3F;    // фазы удара длиннее

    // Бойцы не в покое — только их и тикаем
    private static final Set<LivingEntity> ACTIVE = new HashSet<>();
    // Кто смотрит отладку (/combat debug)
    private static final Set<UUID> DEBUG = new HashSet<>();
    // Свой удар: AttackEntityEvent от него не отменяем (по нему проверяют правила другие обработчики)
    private static boolean ownHit;

    private Combat() {}

    public static CombatState state(LivingEntity entity) {
        return entity.getData(ModAttachments.COMBAT);
    }

    // ---------------------------------------------------------------- ввод

    public static void input(ServerPlayer player, CombatInputPayload.Action action, AttackKind kind, StrikeSide side) {
        CombatState state = state(player);
        long now = player.level().getGameTime();
        if (state.inputTick != now) {
            state.inputTick = now;
            state.inputCount = 0;
        }
        if (++state.inputCount > INPUT_RATE) {
            return;
        }
        switch (action) {
            case ATTACK_START -> attackPressed(player, state, kind, side, now);
            case ATTACK_RELEASE -> attackReleased(player, state, now);
            case FEINT -> feint(player, state, now);
            case BLOCK_DOWN -> {
                state.blockHeld = true;
                state.blockSide = side;
                ACTIVE.add(player);
                feint(player, state, now); // ПКМ в замахе — финт, и блок, раз кнопка держится
                sync(player, state);
            }
            case BLOCK_SIDE -> {
                if (state.blockHeld && state.blockSide != side) {
                    state.blockSide = side;
                    sync(player, state);
                    debug(player, Component.translatable("combat.halgventmod.debug.block_side", Component.translatable(side.langKey())));
                }
            }
            case BLOCK_UP -> {
                state.blockHeld = false;
                Defense.lower(player, state);
            }
            case KICK -> Kick.start(player, state, now);
            case GRIP -> grip(player, state, now);
            default -> {
                // остальное — следующие этапы
            }
        }
    }

    // Кнопка удара: в покое — удар; в замахе другая кнопка — смена удара; во время удара и в начале возврата — комбо
    private static void attackPressed(ServerPlayer player, CombatState state, AttackKind kind, StrikeSide side, long now) {
        switch (state.phase) {
            case IDLE -> begin(player, state, kind, side, now, 1.0F, 0, null);
            case WINDUP -> {
                // Смена удара: другая кнопка или та же с другой стороны
                if (!sameAttack(state, kind, side) && canFeint(state, now) && canAct(player)) {
                    Stamina.spend(player, FEINT_STAMINA);
                    begin(player, state, kind, side, now, MORPH_WINDUP, state.combo, "combat.halgventmod.debug.morph");
                }
            }
            case RELEASE -> {
                state.queued = kind;
                state.queuedSide = side;
                state.queuedCharging = true;
            }
            case RECOVERY -> {
                if (state.current != null && now - state.phaseStart < COMBO_WINDOW) { // после пинка — не комбо
                    begin(player, state, kind, side, now, COMBO_WINDUP, state.combo + 1, null);
                }
            }
            default -> {
            }
        }
    }

    // Тот же удар, что идёт: ЛКМ мышью вверх стал ударом сверху (begin) — это тоже он; у укола стороны нет
    private static boolean sameAttack(CombatState state, AttackKind kind, StrikeSide side) {
        boolean sameKind = kind == state.attack || (kind == AttackKind.SLASH && state.attack == AttackKind.OVERHEAD);
        return sameKind && (kind == AttackKind.STAB || side == state.side);
    }

    // Отпустил кнопку: короткое нажатие — обычный удар, долгое — заряженный; в продлённом замахе — бьёт сразу
    private static void attackReleased(ServerPlayer player, CombatState state, long now) {
        if (state.queued != null) {
            state.queuedCharging = false;
            return;
        }
        if (!state.charging) {
            return;
        }
        state.charging = false;
        if (state.phase != Phase.WINDUP) {
            return;
        }
        if (now - state.phaseStart >= HEAVY_MIN_HOLD && !state.heavy) {
            state.heavy = true;
            sync(player, state);
        }
        if (now >= state.windupEnd) {
            toRelease(player, state, now);
        }
    }

    // Финт (4.4): замах отменён, пока не поздно; рипост и контратаку не финтят
    private static void feint(ServerPlayer player, CombatState state, long now) {
        if (state.phase != Phase.WINDUP || state.riposte || state.counter || !canFeint(state, now)) {
            return;
        }
        Stamina.spend(player, FEINT_STAMINA);
        debug(player, Component.translatable("combat.halgventmod.debug.feint"));
        idle(player, state, now);
    }

    private static boolean canFeint(CombatState state, long now) {
        return now < state.phaseEnd - FEINT_LOCK;
    }

    /**
     * Начать замах: windupFactor — доля полного замаха (смена удара, комбо), combo — номер удара в серии.
     * note — ключ пометки для отладки (смена удара) или null.
     */
    private static void begin(ServerPlayer player, CombatState state, AttackKind kind, StrikeSide side, long now,
                              float windupFactor, int combo, @Nullable String note) {
        if (!canAct(player) || (kind == AttackKind.SPRINT && !player.isSprinting())) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        WeaponProfile profile = WeaponProfiles.of(player, weapon);
        // ЛКМ с мышью вверх — удар сверху (свой профиль удара, если есть у оружия)
        if (kind == AttackKind.SLASH && side == StrikeSide.UP && profile != null
                && profile.attack(AttackKind.OVERHEAD, altGrip(weapon)) != null) {
            kind = AttackKind.OVERHEAD;
        }
        // Со щитом во второй руке R — удар щитом (5.6)
        boolean bash = kind == AttackKind.SPECIAL && WeaponProfiles.hasShield(player);
        if (bash) {
            profile = WeaponProfiles.ofClass(player, WeaponClass.SHIELD);
        }
        WeaponProfile.Attack attack = profile == null ? null : profile.attack(kind, !bash && altGrip(weapon));
        if (attack == null) {
            return;
        }
        state.bash = bash;
        boolean fromQueue = state.queued != null;
        boolean riposte = now <= state.riposteUntil; // удар, начатый в окно после парирования (5.2)
        if (riposte) {
            windupFactor *= Defense.RIPOSTE_WINDUP;
            state.riposteUntil = 0;
        }
        state.attack = kind;
        // Удар, основанный на ударе сверху (с разбега, особый), — всегда сверху
        state.side = attack.shape() == AttackKind.OVERHEAD ? StrikeSide.UP : side;
        state.heavy = false;
        state.charging = !fromQueue || state.queuedCharging;
        state.queued = null;
        state.riposte = riposte;
        state.counter = false;
        state.combo = combo;
        state.profile = profile;
        state.current = attack;
        state.weapon = weapon.getItem();
        state.hit.clear();
        if (kind == AttackKind.SPRINT) {
            player.setSprinting(false);
        }
        int windup = ticks(player, profile, Math.max(1, Math.round(attack.windup() * windupFactor)));
        state.windupEnd = now + windup;
        if (note != null) {
            debug(player, Component.translatable(note));
        }
        enter(player, state, Phase.WINDUP, windup, now);
    }

    /** Второй хват у предмета (компонент halgventmod:alt_grip). */
    public static boolean altGrip(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModComponents.ALT_GRIP.get()));
    }

    // X: в покое сменить хват, если у профиля есть второй набор ударов (10); GRIP_TICKS ни удара, ни блока
    private static void grip(ServerPlayer player, CombatState state, long now) {
        ItemStack weapon = player.getMainHandItem();
        WeaponProfile profile = WeaponProfiles.of(player, weapon);
        if (state.phase != Phase.IDLE || busy(player) || weapon.isEmpty() || profile == null || profile.altGrip().isEmpty()) {
            return;
        }
        if (altGrip(weapon)) {
            weapon.remove(ModComponents.ALT_GRIP.get());
        } else {
            weapon.set(ModComponents.ALT_GRIP.get(), true);
        }
        state.gripUntil = now + GRIP_TICKS;
        Defense.lower(player, state);
        SoundPlay.at(player, ModSounds.COMBAT_WEAPON_GRIP.get(), SoundSource.PLAYERS, 0.8F, 0.1F);
        debug(player, Component.translatable(altGrip(weapon) ? "combat.halgventmod.debug.grip_alt" : "combat.halgventmod.debug.grip_main"));
    }

    /** Может ли боец сейчас начать удар (ТЗ боя, 4.8). */
    static boolean canAct(ServerPlayer player) {
        return !busy(player); // и при одышке: удар ослаблен (winded)
    }

    /** Одышка: выносливость на нуле (или идёт изнеможение). */
    public static boolean winded(LivingEntity entity) {
        return entity instanceof Player player && (Stamina.exhausted(player) || Stamina.get(player) <= 0);
    }

    /** Ни бить, ни блокировать: оружие достаётся, повержен, несёт кого-то, создаёт персонажа, в наручниках с оружием. */
    static boolean busy(Player player) {
        return player.isSpectator() || !player.isAlive() || !Drawing.ready(player) || Downed.isDowned(player)
                || Pain.shocked(player) || player.level().getGameTime() < state(player).gripUntil
                || Downed.carried(player) != null
                || (player instanceof ServerPlayer server && CharacterCreation.creating(server))
                || (CuffsRules.isCuffed(player) && CuffsRules.forbidden(player.getMainHandItem()));
    }

    /** Длительность фазы с поправкой на скорость (ТЗ боя, 4.1): сегмент гобелена, одышка. Боль — этап 9. */
    private static int ticks(LivingEntity entity, WeaponProfile profile, int base) {
        float speed = (1.0F - CombatBonuses.swingSpeed(entity, profile.weaponClass()))
                * (winded(entity) ? WINDED_SLOW : 1.0F)
                * (entity instanceof Player player ? Pain.attackSlow(player) : 1.0F);
        return Math.max(1, Math.round(base * speed));
    }

    // ---------------------------------------------------------------- фазы

    static void enter(LivingEntity entity, CombatState state, Phase phase, int length, long now) {
        state.phase = phase;
        state.phaseStart = now;
        state.phaseEnd = now + length;
        if (phase == Phase.IDLE && !state.blockHeld) {
            ACTIVE.remove(entity);
        } else {
            ACTIVE.add(entity);
        }
        if (phase != Phase.IDLE) {
            state.blocking = false;
        }
        sync(entity, state);
        Defense.updateMovement(entity, state);
        debug(entity, Component.translatable("combat.halgventmod.debug.phase", phase.name(),
                Component.translatable(state.attack.langKey()), length, notes(entity, state)));
    }

    // Состояние изменилось внутри фазы (заряд) — разослать заново
    static void sync(LivingEntity entity, CombatState state) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, CombatStatePayload.of(entity, state));
    }

    // « — заряженный, комбо 2, обратный» для отладки
    private static Component notes(LivingEntity entity, CombatState state) {
        MutableComponent result = Component.empty();
        if (state.phase == Phase.IDLE) {
            return result;
        }
        boolean first = true;
        for (Component note : new Component[] {
                state.heavy ? Component.translatable("combat.halgventmod.debug.heavy") : null,
                state.combo > 0 ? Component.translatable("combat.halgventmod.debug.combo", state.combo + 1) : null,
                state.current != null && state.current.shape() != AttackKind.STAB
                        ? Component.translatable(state.side.langKey()) : null,
                state.riposte ? Component.translatable("combat.halgventmod.debug.riposte") : null,
                winded(entity) ? Component.translatable("combat.halgventmod.debug.winded") : null}) {
            if (note != null) {
                result.append(first ? " — " : ", ").append(note);
                first = false;
            }
        }
        return result;
    }

    private static void idle(LivingEntity entity, CombatState state, long now) {
        state.charging = false;
        state.heavy = false;
        state.queued = null;
        state.current = null;
        state.hit.clear();
        enter(entity, state, Phase.IDLE, 0, now);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (LivingEntity entity : new ArrayList<>(ACTIVE)) {
            CombatState state = state(entity);
            if (entity.isRemoved() || !entity.isAlive()) {
                ACTIVE.remove(entity);
                state.phase = Phase.IDLE;
                continue;
            }
            tick(entity, state, entity.level().getGameTime());
        }
    }

    private static void tick(LivingEntity entity, CombatState state, long now) {
        Defense.tickGuard(entity, state, now);
        if (state.phase == Phase.IDLE) {
            if (!state.blockHeld) {
                ACTIVE.remove(entity);
            }
            return;
        }
        // Сменил оружие, упал без сил, повержен — удар пропал
        if (state.current != null && (entity.getMainHandItem().getItem() != state.weapon
                || (entity instanceof Player player && Downed.isDowned(player)))) {
            idle(entity, state, now);
            return;
        }
        if (now < state.phaseEnd) {
            if (state.phase == Phase.RELEASE) {
                trace(entity, state, now);
            }
            return;
        }
        WeaponProfile.Attack attack = state.current;
        switch (state.phase) {
            case WINDUP -> {
                // Кнопка ещё зажата: сначала дождаться HEAVY_MIN_HOLD, потом заряд — до отпускания или предела
                if (state.charging) {
                    long minHold = state.phaseStart + HEAVY_MIN_HOLD;
                    if (now < minHold) {
                        state.phaseEnd = minHold;
                        return;
                    }
                    long cap = state.windupEnd + HEAVY_MAX_EXTRA;
                    if (!state.heavy) {
                        state.heavy = true;
                        state.phaseEnd = cap;
                        sync(entity, state);
                        debug(entity, Component.translatable("combat.halgventmod.debug.charging"));
                    }
                    if (now < cap) {
                        return;
                    }
                    state.charging = false; // предел заряда — отпускает сам
                }
                toRelease(entity, state, now);
            }
            case RELEASE -> {
                // Кнопку нажали во время удара — следующий удар серии без возврата
                if (state.queued != null && entity instanceof ServerPlayer player) {
                    AttackKind next = state.queued;
                    begin(player, state, next, state.queuedSide, now, COMBO_WINDUP, state.combo + 1, null);
                    if (state.phase == Phase.WINDUP && state.attack == next && state.phaseStart == now) {
                        return;
                    }
                    state.queued = null;
                }
                enter(entity, state, Phase.RECOVERY, ticks(entity, state.profile, attack.recovery()), now);
            }
            case KICK -> {
                if (entity instanceof ServerPlayer player) {
                    Kick.resolve(player, state, now);
                } else {
                    idle(entity, state, now);
                }
            }
            default -> idle(entity, state, now);
        }
    }

    /** Можно ли этому бить эту цель — правила те же, что у ванильного удара (AttackEntityEvent). */
    static boolean allowed(LivingEntity attacker, LivingEntity target) {
        if (!(attacker instanceof Player player)) {
            return true;
        }
        ownHit = true;
        try {
            return CommonHooks.onPlayerAttackTarget(player, target);
        } finally {
            ownHit = false;
        }
    }

    /**
     * Нанести урон удара (7): у игрока — сквозь 4 слоя брони (DamagePipeline) своим типом урона боя, у остальных — урон
     * телу по типу, а ванильная броня моба — как в ванили. Погашено целиком — звук отражения. Возвращает урон по телу
     * (0 — не прошёл). Следом: оглушение и сбитый замах, дробящий снимает выносливость, кровотечение.
     */
    static float wound(LivingEntity attacker, LivingEntity target, DamageKind kind, float amount, HitZone zone,
                       float penetration, float accuracy, Vec3 point, long now) {
        boolean layered = target instanceof Player; // слои брони — у игроков (у мобов — ванильная броня)
        DamagePipeline.Result result = through(attacker, target, kind, amount, zone, penetration, accuracy);
        if (result.body() < DEFLECTED) {
            deflected(attacker, target, kind, amount, zone, point);
            return 0;
        }
        target.invulnerableTime = 0; // в Chivalry неуязвимости после удара нет; каждый удар бьёт цель один раз
        DamageSource source = layered ? CombatDamageTypes.source(attacker, kind)
                : attacker instanceof Player player ? attacker.damageSources().playerAttack(player)
                : attacker.damageSources().mobAttack(attacker);
        boolean hurt = target.hurt(source, result.body());
        landed(attacker, target, kind, amount, result, zone, point, now, hurt);
        return hurt ? result.body() : 0;
    }

    /** Урон сквозь броню: у игрока — 4 слоя, у остальных — урон телу по типу. Разбор слоёв — в отладку. */
    static DamagePipeline.Result through(LivingEntity attacker, LivingEntity target, DamageKind kind, float amount,
                                         HitZone zone, float penetration, float accuracy) {
        DamagePipeline.Result result = target instanceof Player
                ? DamagePipeline.apply(target, kind, amount, zone, penetration, accuracy, target.getRandom())
                : new DamagePipeline.Result(amount * kind.body(), kind == DamageKind.BLUNT ? amount * kind.body() : 0,
                        0, amount * kind.body() * kind.pain(), List.of());
        for (Component line : result.lines()) {
            debugBoth(attacker, target, line);
        }
        return result;
    }

    /** Броня погасила удар целиком: звук отражения. */
    static void deflected(LivingEntity attacker, LivingEntity target, DamageKind kind, float amount, HitZone zone, Vec3 point) {
        SoundPlay.atChest(target, ModSounds.COMBAT_HIT_DEFLECT.get(), SoundSource.PLAYERS, 1.0F, 0.1F);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new CombatHitPayload(attacker.getId(), target.getId(),
                CombatHitPayload.Outcome.DEFLECT, zone, kind, point));
        debugBoth(attacker, target, Component.translatable("combat.halgventmod.debug.deflect", String.format("%.1f", amount)));
    }

    /** Урон дошёл до тела: исход клиентам, боль, выносливость от дробящего, кровотечение, оглушение и сбитый замах. */
    static void landed(LivingEntity attacker, LivingEntity target, DamageKind kind, float amount, DamagePipeline.Result result,
                       HitZone zone, Vec3 point, long now, boolean hurt) {
        float body = result.body();
        debugBoth(attacker, target, Component.translatable(hurt ? "combat.halgventmod.debug.hit" : "combat.halgventmod.debug.no_damage",
                target.getDisplayName(), String.format("%.1f", amount), String.format("%.1f", body),
                Component.translatable(kind.langKey()), Component.translatable(zone.langKey()), String.format("%.1f", result.pain())));
        if (!hurt) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(attacker, new CombatHitPayload(attacker.getId(), target.getId(),
                CombatHitPayload.Outcome.HIT, zone, kind, point));
        if (target instanceof Player victim) {
            Pain.add(victim, result.pain());
            Pain.markCombat(victim);
            if (result.bluntBody() > 0) {
                Stamina.spend(victim, result.bluntBody() * BLUNT_STAMINA);
            }
        }
        if (body >= BLEED_MIN && target.getRandom().nextFloat() < kind.bleed()) {
            boolean severe = kind == DamageKind.CUTTING || (kind == DamageKind.CHOPPING && body >= SEVERE_CHOPPING);
            NeoForge.EVENT_BUS.post(new BleedEvent(target, severe));
        }
        onBodyHit(target, result.bluntBody(), now);
    }

    /**
     * Урон дошёл до тела (5.5): дробящий от STUN_DAMAGE после брони — оглушение; иначе замах цели сбит, если она
     * замахивалась (рипост и контратаку не сбивает; удар цели уже в фазе удара — оба проходят, «размен»).
     */
    static void onBodyHit(LivingEntity target, float bluntBody, long now) {
        CombatState state = state(target);
        if (bluntBody >= STUN_DAMAGE) {
            stun(target, state, STUN_TICKS, now);
            SoundPlay.atChest(target, ModSounds.COMBAT_STUN.get(), SoundSource.PLAYERS, 1.0F, 0.1F);
            debug(target, Component.translatable("combat.halgventmod.debug.stun"));
            return;
        }
        if (state.phase == Phase.WINDUP && !state.riposte && !state.counter) {
            state.charging = false;
            state.heavy = false;
            state.queued = null;
            state.current = null;
            enter(target, state, Phase.FLINCH, FLINCH_TICKS, now);
            debug(target, Component.translatable("combat.halgventmod.debug.flinch"));
        }
    }

    /** Сорвать всё, что боец делает (болевой шок): удар пропал, блок опущен. */
    public static void interrupt(LivingEntity entity) {
        CombatState state = state(entity);
        if (state.phase != Phase.IDLE) {
            idle(entity, state, entity.level().getGameTime());
        }
        Defense.lower(entity, state);
    }

    /** Удар принят блоком или парирован: атакующий отлетает в возврат длиннее на extra, серия прервана. */
    static void recoil(LivingEntity attacker, CombatState state, int extra, long now) {
        state.queued = null;
        enter(attacker, state, Phase.RECOVERY, ticks(attacker, state.profile, state.current.recovery()) + extra, now);
    }

    /** Оглушение (5.5): удар пропал, ни бить, ни блокировать, ходьба медленнее. */
    static void stun(LivingEntity entity, CombatState state, int ticks, long now) {
        state.charging = false;
        state.heavy = false;
        state.queued = null;
        state.current = null;
        state.hit.clear();
        enter(entity, state, Phase.STUN, ticks, now);
    }

    static void toRelease(LivingEntity entity, CombatState state, long now) {
        enter(entity, state, Phase.RELEASE, ticks(entity, state.profile, state.current.release()), now);
        release(entity, state, state.current);
        state.bladePrev = samples(blade(entity, state, 0));
        trace(entity, state, now);
    }

    // Начало удара: выносливость, замах слышен всем
    private static void release(LivingEntity entity, CombatState state, WeaponProfile.Attack attack) {
        ItemStack weapon = entity.getMainHandItem();
        if (entity instanceof Player player) {
            Pain.markCombat(player);
            // Контратака оплачена, когда отбила удар (Defense.counter)
            Stamina.spend(player, state.counter ? 0 : attack.stamina() * (state.heavy ? HEAVY_STAMINA : 1.0F)
                    * (state.riposte ? Defense.RIPOSTE_STAMINA : 1.0F)
                    * (state.attack == AttackKind.SPRINT ? SPRINT_STAMINA : 1.0F));
        }
        if (state.attack == AttackKind.SPRINT) {
            state.sprintLockUntil = entity.level().getGameTime() + SPRINT_LOCK;
        }
        if (CombatSounds.swings(weapon) || state.heavy) { // заряженный — тяжёлый замах и у лёгкого оружия
            SoundPlay.at(entity, state.heavy ? ModSounds.COMBAT_SWING_HEAVY.get() : CombatSounds.swingSound(weapon),
                    SoundSource.PLAYERS, state.heavy ? CombatSounds.SWING_HEAVY_VOLUME : CombatSounds.swingVolume(weapon),
                    CombatSounds.SWING_PITCH_SPREAD);
        }
    }

    // ---------------------------------------------------------------- трасса клинка (ТЗ боя, 6.2)

    private static SwingPath.Blade blade(LivingEntity entity, CombatState state, float t) {
        return SwingPath.blade(entity, state.current.shape(), state.side, t, reach(state));
    }

    // Длина клинка удара: профиль + прибавка удара + разбег
    private static double reach(CombatState state) {
        return state.profile.bladeLength() + state.current.reachBonus()
                + (state.attack == AttackKind.SPRINT ? SPRINT_REACH : 0);
    }

    // Плечо и BLADE_SAMPLES точек вдоль клинка от хвата до острия
    private static Vec3[] samples(SwingPath.Blade blade) {
        Vec3[] points = new Vec3[BLADE_SAMPLES + 1];
        points[0] = blade.shoulder();
        for (int i = 0; i < BLADE_SAMPLES; i++) {
            points[i + 1] = blade.hilt().lerp(blade.tip(), (double) i / (BLADE_SAMPLES - 1));
        }
        return points;
    }

    /**
     * Один тик удара: клинок сдвинулся с прошлого положения на нынешнее (доля фазы k / длина). Задетые этим движением
     * цели (каждая — раз за удар, взмах — до SLASH_TARGETS) получают удар; клинок упёрся в стену раньше цели — отскок.
     */
    private static void trace(LivingEntity entity, CombatState state, long now) {
        int limit = state.current.shape() == AttackKind.SLASH ? SLASH_TARGETS : 1;
        if (state.hit.size() >= limit) {
            return;
        }
        long length = Math.max(1, state.phaseEnd - state.phaseStart);
        SwingPath.Blade blade = blade(entity, state, (float) (now - state.phaseStart + 1) / length);
        Vec3[] current = samples(blade);
        Vec3[] previous = state.bladePrev != null ? state.bladePrev : current;
        state.bladePrev = current;
        Level level = entity.level();
        Vec3 shoulder = blade.shoulder();

        // Стена — по руке и клинку сейчас; пол (верхняя грань) не считается: удар сверху вниз в землю не отскакивает
        BlockHitResult wall = level.clip(new ClipContext(shoulder, blade.tip(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        boolean wallHit = wall.getType() == HitResult.Type.BLOCK && wall.getDirection() != Direction.UP
                && level.getBlockState(wall.getBlockPos()).isCollisionShapeFullBlock(level, wall.getBlockPos());
        double wallDistance = wallHit ? shoulder.distanceTo(wall.getLocation()) : Double.MAX_VALUE;

        record Touch(LivingEntity target, Vec3 point, double distance) {}
        List<Touch> touches = new ArrayList<>();
        double radius = SwingPath.ARM + reach(state) + 2;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                other -> other != entity && other.isAlive() && !other.isSpectator() && other.isPickable()
                        && !entity.isPassengerOfSameVehicle(other) && !state.hit.contains(other.getId()))) {
            AABB box = other.getBoundingBox().inflate(HIT_INFLATE);
            Vec3 best = box.clip(shoulder, blade.tip()).orElse(null);
            for (int i = 0; i < current.length; i++) {
                Optional<Vec3> swept = box.clip(previous[i], current[i]);
                if (swept.isPresent() && (best == null || shoulder.distanceToSqr(swept.get()) < shoulder.distanceToSqr(best))) {
                    best = swept.get();
                }
            }
            if (best != null && shoulder.distanceTo(best) < wallDistance) {
                touches.add(new Touch(other, best, shoulder.distanceTo(best)));
            }
        }
        touches.sort(Comparator.comparingDouble(Touch::distance));
        for (Touch touch : touches) {
            if (state.hit.size() >= limit || state.phase != Phase.RELEASE) {
                break;
            }
            state.hit.add(touch.target().getId());
            strike(entity, touch.target(), touch.point(), state.current,
                    state.bash ? entity.getOffhandItem() : entity.getMainHandItem());
        }
        if (wallHit && state.hit.isEmpty() && state.phase == Phase.RELEASE) {
            bounce(entity, state, wall, now);
        }
    }

    // Клинок упёрся в стену: звук по материалу, длинный возврат, серия прервана
    private static void bounce(LivingEntity entity, CombatState state, BlockHitResult wall, long now) {
        Level level = entity.level();
        CombatSounds.wallHit(level, wall.getBlockPos(), level.getBlockState(wall.getBlockPos()), wall.getLocation());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new CombatHitPayload(entity.getId(), -1,
                CombatHitPayload.Outcome.WALL, HitZone.TORSO, state.current.type(), wall.getLocation()));
        debug(entity, Component.translatable("combat.halgventmod.debug.wall"));
        state.queued = null;
        enter(entity, state, Phase.RECOVERY, ticks(entity, state.profile, state.current.recovery()) + WALL_RECOVERY, now);
    }

    /** Нанести удар цели в точке. Правила (кто кого может бить) — через AttackEntityEvent, как у ванильного удара. */
    private static void strike(LivingEntity attacker, LivingEntity target, Vec3 point, WeaponProfile.Attack attack, ItemStack weapon) {
        if (!allowed(attacker, target)) {
            return;
        }
        CombatState state = state(attacker);
        long now = attacker.level().getGameTime();
        HitZone zone = HitZone.of(target, point);
        DamageKind kind = attack.type();
        float factor = zone.damage();
        // Копьё и древковое вплотную бьют древком (6.4)
        WeaponClass weaponClass = state.profile.weaponClass();
        if ((weaponClass == WeaponClass.SPEAR || weaponClass == WeaponClass.POLEARM)
                && attacker.getEyePosition().distanceTo(point) < SHAFT_RANGE) {
            kind = DamageKind.BLUNT;
            factor *= SHAFT_DAMAGE;
        }
        if (Defense.counter(attacker, target, state, kind, point, now)) {
            return;
        }
        float raw = attack.damage() * (state.heavy ? HEAVY_DAMAGE : 1.0F)
                * (state.attack == AttackKind.SPRINT ? SPRINT_DAMAGE : 1.0F)
                * GearQuality.get(weapon).damage() * Dullness.damageFactor(weapon)
                * (1.0F + CombatBonuses.damage(attacker, weaponClass))
                * (winded(attacker) ? WINDED_DAMAGE : 1.0F)
                * (attacker instanceof Player player ? Pain.damageFactor(player) : 1.0F);
        float pass = Defense.defend(attacker, target, state, kind, raw, point, now);
        if (pass <= 0) {
            return;
        }
        // Пробивание и точность (7.3): профиль, качество, особый удар
        float penetration = state.profile.penetration() * (1.0F + GearQuality.get(weapon).penetration())
                * (winded(attacker) ? WINDED_DAMAGE : 1.0F);
        float accuracy = state.profile.accuracy() + attack.accuracyBonus();
        float body = wound(attacker, target, kind, raw * factor * pass, zone, penetration, accuracy, point, now);
        if (body <= 0) {
            return;
        }
        target.knockback(KNOCKBACK, Mth.sin(attacker.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(attacker.getYRot() * Mth.DEG_TO_RAD));
        attacker.setLastHurtMob(target);
        if (attack.effect().equals("hook")) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HOOK_TICKS, HOOK_LEVEL));
        }
        if (weapon.isDamageableItem() && !(attacker instanceof Player player && player.getAbilities().instabuild)) {
            weapon.hurtAndBreak(1, attacker, state.bash ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
        }
    }

    // ---------------------------------------------------------------- ванильный удар

    // Оружие из профилей и кулак бьют только через бой: ванильный удар (и поддельный пакет) отменяем раньше всех
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!ownHit && WeaponProfiles.handles(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Этот AttackEntityEvent — проверка правил для удара боя, а не ванильный удар. */
    public static boolean isOwnHit() {
        return ownHit;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity());
        DEBUG.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------- отладка

    /** Отладка и атакующему, и цели. */
    static void debugBoth(LivingEntity attacker, LivingEntity target, Component message) {
        debug(attacker, message);
        debug(target, message);
    }

    public static void debug(LivingEntity entity, Component message) {
        if (entity instanceof ServerPlayer player && DEBUG.contains(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("combat.halgventmod.debug.prefix").append(message));
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("combat")
                .requires(source -> source.hasPermission(Permissions.ADMIN_LEVEL))
                .then(Commands.literal("debug").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    boolean on = DEBUG.add(player.getUUID()) || !DEBUG.remove(player.getUUID());
                    context.getSource().sendSuccess(() -> Component.translatable(
                            on ? "command.halgventmod.combat.debug_on" : "command.halgventmod.combat.debug_off"), false);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
