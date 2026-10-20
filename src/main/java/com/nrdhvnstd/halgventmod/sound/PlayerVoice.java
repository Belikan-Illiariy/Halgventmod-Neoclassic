package com.nrdhvnstd.halgventmod.sound;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Голос игрока вместо ванильных стонов (docs/sound_spec/TZ_sounds.md, 6.7). Набор — Voices.setOf(игрок).
 * Крик слышат все рядом, включая самого игрока. Таймеры — только на сервере.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class PlayerVoice {
    // ===== Правила — меняйте здесь ⚠️ =====
    public static final float VOLUME = 1.0F;
    public static final float PITCH_SPREAD = 0.05F;
    public static final int MIN_GAP_TICKS = 16;     // перерыв между криками одного игрока: 0,8 с (смерть — без перерыва)
    public static final int FIRE_GAP_TICKS = 40;    // огонь и лава — не чаще раза в 2 с
    public static final int EFFORT_GAP_TICKS = 40;  // кряхтение при ударе и прыжке — не чаще раза в 2 с
    public static final float HEAVY_DAMAGE = 8.0F;  // с такого урона по телу — крик, ниже — стон

    // Ванильные крики игрока — их заменяет голос (ими пользуются только игроки)
    private static final Set<ResourceLocation> VANILLA = Set.of(
            SoundEvents.PLAYER_HURT.getLocation(), SoundEvents.PLAYER_HURT_DROWN.getLocation(),
            SoundEvents.PLAYER_HURT_FREEZE.getLocation(), SoundEvents.PLAYER_HURT_ON_FIRE.getLocation(),
            SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH.getLocation(), SoundEvents.PLAYER_DEATH.getLocation());

    private static final class Timers {
        long cry = Long.MIN_VALUE / 2;
        long fire = Long.MIN_VALUE / 2;
        long effort = Long.MIN_VALUE / 2;
    }

    private static final Map<Player, Timers> TIMERS = new WeakHashMap<>();

    private PlayerVoice() {}

    /** Крикнуть, если перерыв прошёл и в наборе такой крик есть. Только на сервере. */
    public static void cry(Player player, Voices.Cry cry) {
        if (player instanceof ServerPlayer server) {
            play(server, cry, false);
        }
    }

    private static boolean play(ServerPlayer player, Voices.Cry cry, boolean ignoreGap) {
        Timers timers = timers(player);
        long now = player.level().getGameTime();
        if (!ignoreGap && now - timers.cry < MIN_GAP_TICKS) {
            return false;
        }
        Holder<SoundEvent> sound = Voices.sound(Voices.setOf(player), cry);
        if (sound == null) {
            return false; // в наборе такого нет — тишина
        }
        timers.cry = now;
        SoundPlay.at(player, sound, SoundSource.PLAYERS, VOLUME, PITCH_SPREAD);
        return true;
    }

    private static Timers timers(Player player) {
        return TIMERS.computeIfAbsent(player, p -> new Timers());
    }

    // Шаг 1: смерть. LOWEST и не отменено — позже «Повержен» будет её отменять
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            play(player, Voices.Cry.DEATH, true);
        }
    }

    // Шаги 2–7: ранение
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isDeadOrDying()) {
            return; // смертельный удар — крик даст смерть (или тотем)
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.DROWN)) {
            play(player, Voices.Cry.DROWN, false);
            return;
        }
        // Голод и жажда, яд, иссушение ⚠️ — молча (TZ_character_fx.md, раздел 12)
        if (source.is(DamageTypes.STARVE) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER)) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0) {
            return;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            Timers timers = timers(player);
            long now = player.level().getGameTime();
            if (now - timers.fire >= FIRE_GAP_TICKS && play(player, Voices.Cry.HURT_LIGHT, false)) {
                timers.fire = now;
            }
            return;
        }
        play(player, damage >= HEAVY_DAMAGE ? Voices.Cry.HURT_HEAVY : Voices.Cry.HURT_LIGHT, false);
    }

    // Шаг 8: спасся тотемом. Не отменено — иначе прозвучали бы и крик, и смерть
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTotem(LivingUseTotemEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            play(player, Voices.Cry.HURT_HEAVY, false);
        }
    }

    // Усилие: удар по существу или прыжок
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!event.isCanceled() && event.getTarget() instanceof LivingEntity && event.getEntity() instanceof ServerPlayer player) {
            effort(player);
        }
    }

    // Сервер сам замечает прыжок по пакету движения (как Stamina.onJump)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            effort(player);
        }
    }

    private static void effort(ServerPlayer player) {
        Timers timers = timers(player);
        long now = player.level().getGameTime();
        if (now - timers.effort >= EFFORT_GAP_TICKS && play(player, Voices.Cry.EFFORT, false)) {
            timers.effort = now;
        }
    }

    // Ванильные стоны и крик смерти — глушить на обеих сторонах
    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        silenceVanilla(event);
    }

    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        silenceVanilla(event);
    }

    private static void silenceVanilla(PlayLevelSoundEvent event) {
        if (event.getSound() != null && VANILLA.contains(event.getSound().value().getLocation())) {
            event.setCanceled(true);
        }
    }
}
