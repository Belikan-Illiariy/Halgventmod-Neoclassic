package com.nrdhvnstd.halgventmod.sound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.combat.CombatDamageTypes;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.armor.ArmorSounds;
import com.nrdhvnstd.halgventmod.draw.Drawing;
import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Звуки боя (docs/sound_spec/TZ_sounds.md, 6.3–6.8): удары, замахи, щит, луки, арбалеты, попадания стрел.
 * Только звук поверх механики — урон, блок и выносливость не трогаются.
 * Всё на сервере, слышат все рядом. Обработчики событий, которые могут отменить другие системы мода
 * (Stamina, Drawing), — с LOWEST и проверкой отмены. Таймеры и запомненные звуки — только на сервере.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class CombatSounds {
    // ===== Громкость и разброс тона — подбираются на слух ⚠️ =====
    public static final float HIT_VOLUME = 1.0F;
    public static final float HIT_PITCH_SPREAD = 0.1F;
    public static final float ARROW_HIT_VOLUME = 1.0F;
    public static final float ARROW_HIT_PITCH_SPREAD = 0.1F;
    public static final float BLOCK_VOLUME = 1.0F;
    public static final float BLOCK_PITCH_SPREAD = 0.1F;
    public static final float BROKEN_PITCH_SPREAD = 0.05F;
    public static final float SHIELD_RAISE_VOLUME = 0.7F;
    public static final float SHIELD_RAISE_PITCH_SPREAD = 0.1F;
    public static final float SWING_LIGHT_VOLUME = 0.6F;
    public static final float SWING_HEAVY_VOLUME = 0.7F;
    public static final float SWING_PITCH_SPREAD = 0.1F;
    public static final float BOW_DRAW_VOLUME = 0.6F;
    public static final float BOW_DRAW_PITCH_SPREAD = 0.08F;
    public static final float BOW_SHOOT_VOLUME = 1.0F;
    public static final float BOW_SHOOT_PITCH_SPREAD = 0.08F;
    public static final float CROSSBOW_LOAD_VOLUME = 0.8F;
    public static final float CROSSBOW_LOAD_PITCH_SPREAD = 0.05F;
    public static final float CROSSBOW_SHOOT_VOLUME = 1.0F;
    public static final float CROSSBOW_SHOOT_PITCH_SPREAD = 0.05F;

    // Замах в воздух — не чаще раза в 0,5 с ⚠️ (клиент считает так же, сервер чуть мягче — на задержку сети)
    public static final int SWING_COOLDOWN_TICKS = 10;
    private static final int SERVER_SWING_COOLDOWN_TICKS = 8;
    // Как далеко от запомненной точки может прозвучать ванильное попадание стрелы, чтобы считаться им ⚠️
    private static final double ARROW_MATCH_DISTANCE = 1.0;

    /** Чем бьют: по тегам sound/*; всё прочее в руке — как дробящее, пустая рука игрока — кулак. */
    public enum Weapon { BLADE, POINT, BLUNT, FIST }

    // Ванильные звуки, которые заменяются или глушатся
    private static final Set<ResourceLocation> PLAYER_ATTACK = Set.of(
            SoundEvents.PLAYER_ATTACK_STRONG.getLocation(), SoundEvents.PLAYER_ATTACK_WEAK.getLocation(),
            SoundEvents.PLAYER_ATTACK_CRIT.getLocation(), SoundEvents.PLAYER_ATTACK_SWEEP.getLocation(),
            SoundEvents.PLAYER_ATTACK_KNOCKBACK.getLocation(), SoundEvents.PLAYER_ATTACK_NODAMAGE.getLocation());
    private static final Set<ResourceLocation> CROSSBOW_LOADING = Set.of(
            SoundEvents.CROSSBOW_LOADING_START.value().getLocation(), SoundEvents.CROSSBOW_QUICK_CHARGE_1.value().getLocation(),
            SoundEvents.CROSSBOW_QUICK_CHARGE_2.value().getLocation(), SoundEvents.CROSSBOW_QUICK_CHARGE_3.value().getLocation());
    private static final Set<ResourceLocation> CROSSBOW_LOADING_SILENT = Set.of(
            SoundEvents.CROSSBOW_LOADING_MIDDLE.value().getLocation(), SoundEvents.CROSSBOW_LOADING_END.value().getLocation());
    private static final Set<ResourceLocation> ARROW_HIT = Set.of(
            SoundEvents.ARROW_HIT.getLocation(), SoundEvents.CROSSBOW_HIT.getLocation());
    private static final Set<ResourceLocation> SHIELD_CLIENT = Set.of(
            SoundEvents.SHIELD_BLOCK.getLocation(), SoundEvents.SHIELD_BREAK.getLocation());

    private static final Set<SoundType> WOOD = Set.of(SoundType.WOOD, SoundType.BAMBOO_WOOD, SoundType.CHERRY_WOOD,
            SoundType.NETHER_WOOD, SoundType.STEM);
    private static final Set<SoundType> STONE = Set.of(SoundType.STONE, SoundType.DEEPSLATE, SoundType.DEEPSLATE_BRICKS,
            SoundType.DEEPSLATE_TILES, SoundType.POLISHED_DEEPSLATE, SoundType.TUFF, SoundType.TUFF_BRICKS,
            SoundType.POLISHED_TUFF, SoundType.NETHER_BRICKS, SoundType.NETHERRACK, SoundType.BASALT, SoundType.CALCITE,
            SoundType.MUD_BRICKS);

    // Только на сервере
    private static final Map<Player, Long> ATTACKED_LIVING = new WeakHashMap<>(); // тик, когда игрок ударил существо
    private static final Map<Player, Long> LAST_SWING = new WeakHashMap<>();
    private record Shot(Level level, Vec3 pos, long tick) {}
    private static final List<Shot> CROSSBOW_SHOTS = new ArrayList<>();
    private record ArrowHit(Level level, Vec3 pos, long tick, SoundEvent sound) {}
    private static final List<ArrowHit> ARROW_HITS = new ArrayList<>();

    private CombatSounds() {}

    // ----- Вид оружия -----

    /** Чем бьёт существо, или null — моб без оружия, его ванильные звуки остаются. */
    @Nullable
    public static Weapon weaponOf(LivingEntity attacker) {
        ItemStack hand = attacker.getMainHandItem();
        if (hand.isEmpty()) {
            return attacker instanceof Player ? Weapon.FIST : null;
        }
        if (hand.is(SoundTags.BLADE)) {
            return Weapon.BLADE;
        }
        if (hand.is(SoundTags.POINT)) {
            return Weapon.POINT;
        }
        return Weapon.BLUNT;
    }

    /**
     * Чем бьют — для звука попадания. Удар боя — по его типу урона (у меча укол звучит остриём, взмах — клинком;
     * дробящий пустой рукой — кулак), прочее — по оружию в руке (weaponOf).
     */
    @Nullable
    private static Weapon weaponOf(DamageSource source, LivingEntity attacker) {
        DamageKind kind = CombatDamageTypes.kindOf(source);
        if (kind == null) {
            return weaponOf(attacker);
        }
        return switch (kind) {
            case CHOPPING, CUTTING -> Weapon.BLADE;
            case PIERCING, PENETRATING -> Weapon.POINT;
            case BLUNT -> attacker.getMainHandItem().isEmpty() ? Weapon.FIST : Weapon.BLUNT;
        };
    }

    /** Замах слышен только у оружия; пустая рука и прочие предметы — без замаха ⚠️. */
    public static boolean swings(ItemStack stack) {
        return stack.is(SoundTags.BLADE) || stack.is(SoundTags.POINT) || stack.is(SoundTags.BLUNT);
    }

    public static SoundEvent swingSound(ItemStack stack) {
        return stack.is(SoundTags.HEAVY_WEAPON) ? ModSounds.COMBAT_SWING_HEAVY.get() : ModSounds.COMBAT_SWING_LIGHT.get();
    }

    public static float swingVolume(ItemStack stack) {
        return stack.is(SoundTags.HEAVY_WEAPON) ? SWING_HEAVY_VOLUME : SWING_LIGHT_VOLUME;
    }

    private static SoundEvent hitSound(Weapon weapon, ArmorSounds.Layer layer) {
        return switch (weapon) {
            case BLADE -> switch (layer) {
                case PLATE -> ModSounds.COMBAT_HIT_BLADE_PLATE.get();
                case MAIL -> ModSounds.COMBAT_HIT_BLADE_MAIL.get();
                case CLOTH -> ModSounds.COMBAT_HIT_BLADE_PADDED.get();
                case BODY -> ModSounds.COMBAT_HIT_BLADE_FLESH.get();
            };
            case POINT -> layer == ArmorSounds.Layer.PLATE || layer == ArmorSounds.Layer.MAIL
                    ? ModSounds.COMBAT_HIT_POINT_ARMOR.get() : ModSounds.COMBAT_HIT_POINT_FLESH.get();
            case BLUNT -> layer == ArmorSounds.Layer.PLATE
                    ? ModSounds.COMBAT_HIT_BLUNT_PLATE.get() : ModSounds.COMBAT_HIT_BLUNT_SOFT.get();
            case FIST -> ModSounds.COMBAT_HIT_FIST.get();
        };
    }

    // ----- Удар и замах по существу -----

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (event.isCanceled() || player.level().isClientSide || !(event.getTarget() instanceof LivingEntity)) {
            return;
        }
        long now = player.level().getGameTime();
        ATTACKED_LIVING.put(player, now); // ванильный звук удара по существу — заглушить в этот тик
        ItemStack hand = player.getMainHandItem();
        if (swings(hand) && !Combat.isOwnHit()) { // у удара боя замах прозвучал в начале удара (Combat)
            LAST_SWING.put(player, now);
            SoundPlay.at(player, swingSound(hand), SoundSource.PLAYERS, swingVolume(hand), SWING_PITCH_SPREAD);
        }
    }

    /** Замах в воздух: клиент проиграл его у себя и прислал SwingPayload, остальные слышат отсюда. */
    public static void swingInAir(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        long now = player.level().getGameTime();
        Long last = LAST_SWING.get(player);
        if (!swings(hand) || player.isSpectator() || !Drawing.ready(player) || Stamina.exhausted(player)
                || (last != null && now - last < SERVER_SWING_COOLDOWN_TICKS)) {
            return;
        }
        LAST_SWING.put(player, now);
        SoundPlay.at(player, player, swingSound(hand), SoundSource.PLAYERS, swingVolume(hand), SWING_PITCH_SPREAD);
    }

    /** Клинок упёрся в стену (бой, трасса клинка): камень — звон, прочее — звук удара по материалу блока. */
    public static void wallHit(Level level, BlockPos pos, BlockState state, Vec3 at) {
        SoundType type = state.getSoundType(level, pos, null);
        SoundEvent sound = STONE.contains(type) ? ModSounds.COMBAT_HIT_DEFLECT.get() : type.getHitSound();
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, HIT_VOLUME,
                SoundPlay.pitch(level.getRandom(), HIT_PITCH_SPREAD));
    }

    // Урон прошёл: звук удара в ближнем бою или запомнить попадание стрелы
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }
        // Золотые сердца поглощения — тоже попадание; ноль — щит или ничего не пробил
        if (event.getNewDamage() + event.getReduction(DamageContainer.Reduction.ABSORPTION) <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        if (direct instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident)) {
            ArmorSounds.Layer layer = ArmorSounds.bodyLayer(target);
            boolean armored = layer == ArmorSounds.Layer.PLATE || layer == ArmorSounds.Layer.MAIL;
            rememberArrowHit(arrow, armored ? ModSounds.COMBAT_HIT_ARROW_ARMOR.get() : ModSounds.COMBAT_HIT_ARROW_FLESH.get());
            return;
        }
        if (!source.is(SoundTags.MELEE) || direct != source.getEntity() || !(direct instanceof LivingEntity attacker)) {
            return;
        }
        Weapon weapon = weaponOf(source, attacker);
        if (weapon != null) {
            SoundPlay.atChest(target, hitSound(weapon, ArmorSounds.bodyLayer(target)), attacker.getSoundSource(),
                    HIT_VOLUME, HIT_PITCH_SPREAD);
        }
    }

    // ----- Щит -----

    // Отмена этого события блок не снимает (щит смотрит только на getBlocked) — поэтому receiveCanceled
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity blocker = event.getEntity();
        if (!event.getBlocked() || blocker.level().isClientSide) {
            return;
        }
        DamageSource source = event.getDamageSource();
        if (source.getDirectEntity() instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident)) {
            SoundPlay.atChest(blocker, ModSounds.COMBAT_HIT_ARROW_WOOD.get(), SoundSource.PLAYERS, ARROW_HIT_VOLUME, ARROW_HIT_PITCH_SPREAD);
            return;
        }
        // Топор выбьет щит (Player.blockUsingShield) — звук пробитой защиты вместо блока ⚠️
        boolean broken = blocker instanceof Player && !source.is(DamageTypeTags.IS_PROJECTILE)
                && source.getDirectEntity() instanceof LivingEntity attacker && attacker.canDisableShield();
        if (broken) {
            SoundPlay.atChest(blocker, ModSounds.COMBAT_BLOCK_BROKEN.get(), SoundSource.PLAYERS, BLOCK_VOLUME, BROKEN_PITCH_SPREAD);
        } else {
            SoundPlay.atChest(blocker, ModSounds.COMBAT_BLOCK_SHIELD_WOOD.get(), SoundSource.PLAYERS, BLOCK_VOLUME, BLOCK_PITCH_SPREAD);
        }
    }

    // ----- Натянуть лук, поднять щит (только игрок: скелеты тоже натягивают лук через это событие) -----

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack.getItem() instanceof BowItem) {
            SoundPlay.at(player, ModSounds.COMBAT_BOW_DRAW.get(), SoundSource.PLAYERS, BOW_DRAW_VOLUME, BOW_DRAW_PITCH_SPREAD);
        } else if (stack.canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            SoundPlay.at(player, ModSounds.COMBAT_SHIELD_RAISE.get(), SoundSource.PLAYERS, SHIELD_RAISE_VOLUME, SHIELD_RAISE_PITCH_SPREAD);
        }
    }

    // ----- Попадание стрелы в блок -----

    // Не отменять: отмена отменяет само попадание, и стрела летит дальше
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide || !(event.getProjectile() instanceof AbstractArrow arrow)
                || arrow instanceof ThrownTrident || !(event.getRayTraceResult() instanceof BlockHitResult hit)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        SoundType type = arrow.level().getBlockState(pos).getSoundType(arrow.level(), pos, arrow);
        if (WOOD.contains(type)) {
            rememberArrowHit(arrow, ModSounds.COMBAT_HIT_ARROW_WOOD.get());
        } else if (STONE.contains(type)) {
            rememberArrowHit(arrow, ModSounds.COMBAT_HIT_ARROW_STONE.get());
        }
        // земля, песок, шерсть — ванильный звук остаётся
    }

    // Ванильный звук попадания звучит уже после урона или удара о блок — его и подменим, своего не запускаем
    private static void rememberArrowHit(AbstractArrow arrow, SoundEvent sound) {
        long now = arrow.level().getGameTime();
        ARROW_HITS.removeIf(hit -> hit.tick != now);
        ARROW_HITS.add(new ArrowHit(arrow.level(), arrow.position(), now, sound));
    }

    @Nullable
    private static SoundEvent takeArrowHit(Level level, Vec3 pos) {
        long now = level.getGameTime();
        ArrowHit nearest = null;
        double nearestDistance = ARROW_MATCH_DISTANCE * ARROW_MATCH_DISTANCE;
        for (ArrowHit hit : ARROW_HITS) {
            double distance = hit.pos.distanceToSqr(pos);
            if (hit.level == level && hit.tick == now && distance <= nearestDistance) {
                nearest = hit;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return null;
        }
        ARROW_HITS.remove(nearest);
        return nearest.sound;
    }

    // С «Тройным выстрелом» ваниль играет выстрел на каждую стрелу: от одного стрелка за тик — только первый
    private static boolean repeatedCrossbowShot(Level level, Vec3 pos) {
        long now = level.getGameTime();
        CROSSBOW_SHOTS.removeIf(shot -> shot.tick != now);
        for (Shot shot : CROSSBOW_SHOTS) {
            if (shot.level == level && shot.pos.distanceToSqr(pos) < 1.0E-4) {
                return true;
            }
        }
        CROSSBOW_SHOTS.add(new Shot(level, pos, now));
        return false;
    }

    // ----- Подмена и заглушение ванильных звуков -----

    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        onSound(event, event.getPosition());
    }

    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        onSound(event, event.getEntity().position());
    }

    private static void onSound(PlayLevelSoundEvent event, Vec3 pos) {
        if (event.getSound() == null) {
            return;
        }
        ResourceLocation id = event.getSound().value().getLocation();
        Level level = event.getLevel();
        if (level.isClientSide) {
            // Ванильный щит слышит только сам игрок со щитом; новый звук приходит с сервера всем
            if (SHIELD_CLIENT.contains(id)) {
                event.setCanceled(true);
            }
            return;
        }

        if (PLAYER_ATTACK.contains(id)) {
            // Только когда игрок ударил существо: по лодке, вагонетке, рамке ванильный звук остаётся
            long now = level.getGameTime();
            for (Map.Entry<Player, Long> attack : ATTACKED_LIVING.entrySet()) {
                Player player = attack.getKey();
                if (attack.getValue() == now && player.level() == level && player.position().distanceToSqr(pos) < 1.0E-4) {
                    event.setCanceled(true);
                    return;
                }
            }
        } else if (id.equals(SoundEvents.ARROW_SHOOT.getLocation())) {
            // Лук игрока; у скелетов свой entity.skeleton.shoot — его не трогаем
            replace(event, ModSounds.COMBAT_BOW_SHOOT.get(), BOW_SHOOT_VOLUME, BOW_SHOOT_PITCH_SPREAD, level);
        } else if (CROSSBOW_LOADING.contains(id)) {
            replace(event, ModSounds.COMBAT_CROSSBOW_LOAD.get(), CROSSBOW_LOAD_VOLUME, CROSSBOW_LOAD_PITCH_SPREAD, level);
        } else if (CROSSBOW_LOADING_SILENT.contains(id)) {
            event.setCanceled(true);
        } else if (id.equals(SoundEvents.CROSSBOW_SHOOT.getLocation())) {
            if (repeatedCrossbowShot(level, pos)) {
                event.setCanceled(true);
            } else {
                replace(event, ModSounds.COMBAT_CROSSBOW_SHOOT.get(), CROSSBOW_SHOOT_VOLUME, CROSSBOW_SHOOT_PITCH_SPREAD, level);
            }
        } else if (ARROW_HIT.contains(id)) {
            SoundEvent hit = takeArrowHit(level, pos);
            if (hit != null) {
                replace(event, hit, ARROW_HIT_VOLUME, ARROW_HIT_PITCH_SPREAD, level);
            }
        }
    }

    // Громкость и тон — свои: иначе останутся ванильные (у лука тон растёт с натяжением, взвод арбалета — на 0,5)
    private static void replace(PlayLevelSoundEvent event, SoundEvent sound, float volume, float spread, Level level) {
        event.setSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound));
        event.setNewVolume(volume);
        event.setNewPitch(SoundPlay.pitch(level.getRandom(), spread));
    }
}
