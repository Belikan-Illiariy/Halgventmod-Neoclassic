package com.nrdhvnstd.halgventmod.combat;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.needs.Pain;
import com.nrdhvnstd.halgventmod.registry.ModEffects;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Травмы (по образцу Kingdom Come Combat, InjuryStatusEffect): сильный удар, дошедший до тела, оставляет травму той
 * части, куда пришёлся, — на минуты; очень сильный — тяжёлую (вдвое сильнее, дольше). Повязка (Social.heal)
 * снимает самую тяжёлую; остальное заживает само.
 * <ul>
 *   <li><b>сотрясение</b> (голова, лицо, шея): в глазах темнеет время от времени, выносливость меньше;</li>
 *   <li><b>рана руки</b> (рука, кисть): удар медленнее, блок дороже;</li>
 *   <li><b>рана корпуса</b> (грудь, живот): выносливость меньше;</li>
 *   <li><b>рана ноги</b>: шаг медленнее (атрибут), тяжёлая — бег закрыт.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Injuries {
    // Меняйте здесь ⚠️
    public static final float INJURY_DAMAGE = 8;        // урона по телу за удар — травма
    public static final float SEVERE_DAMAGE = 16;       // — тяжёлая
    public static final int DURATION = 20 * 60 * 5;     // 5 минут, тяжёлая — вдвое
    public static final float ARM_SLOW = 0.15F;         // за уровень: фазы удара длиннее
    public static final float ARM_BLOCK = 0.2F;         // за уровень: блок дороже
    public static final float STAMINA_PENALTY = 15;     // за уровень (корпус и голова)
    public static final int HEAD_DARK_EVERY = 30;       // раз в сколько секунд темнеет (в среднем)
    public static final float PAIN = 6;                 // боль от новой травмы

    private Injuries() {}

    private static Holder<MobEffect> effect(HitZone zone) {
        return switch (zone) {
            case CROWN, FACE, NECK -> ModEffects.INJURY_HEAD;
            case ARM, HAND -> ModEffects.INJURY_ARM;
            case CHEST, ABDOMEN -> ModEffects.INJURY_TORSO;
            case THIGH, KNEE, SHIN, FOOT -> ModEffects.INJURY_LEG;
        };
    }

    /** Удар дошёл до тела (Combat.landed): сильный — травма части. */
    static void onWound(LivingEntity target, HitZone zone, float body) {
        if (body < INJURY_DAMAGE) {
            return;
        }
        Holder<MobEffect> effect = effect(zone);
        MobEffectInstance had = target.getEffect(effect);
        int level = body >= SEVERE_DAMAGE || had != null ? 1 : 0; // повторная травма той же части — тяжёлая
        int duration = DURATION * (level + 1);
        if (had != null && had.getAmplifier() >= level && had.getDuration() >= duration) {
            return;
        }
        target.addEffect(new MobEffectInstance(effect, duration, level, false, true, true));
        if (target instanceof Player player) {
            Pain.add(player, PAIN);
            player.displayClientMessage(Component.translatable("message.halgventmod.injury." + (level > 0 ? "severe" : "new"),
                    Component.translatable(effect.value().getDescriptionId())), true);
        }
    }

    /** Уровень травмы (0 — нет, 1 — лёгкая, 2 — тяжёлая). */
    public static int level(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    /** Множитель длительности фаз удара (рана руки). */
    public static float swingSlow(LivingEntity entity) {
        return 1.0F + ARM_SLOW * level(entity, ModEffects.INJURY_ARM);
    }

    /** Множитель цены блока (рана руки). */
    public static float blockCost(LivingEntity entity) {
        return 1.0F + ARM_BLOCK * level(entity, ModEffects.INJURY_ARM);
    }

    /** Насколько меньше выносливости (корпус и голова). */
    public static float staminaPenalty(LivingEntity entity) {
        return STAMINA_PENALTY * (level(entity, ModEffects.INJURY_TORSO) + level(entity, ModEffects.INJURY_HEAD));
    }

    /** Тяжёлая рана ноги — бег закрыт. */
    public static boolean noSprint(LivingEntity entity) {
        return level(entity, ModEffects.INJURY_LEG) >= 2;
    }

    /** Есть что перевязать: кровь течёт, травма или неперевязанная рана. */
    public static boolean needsCare(LivingEntity entity) {
        return Bleeding.level(entity) > 0 || level(entity, ModEffects.INJURY_HEAD) + level(entity, ModEffects.INJURY_ARM)
                + level(entity, ModEffects.INJURY_TORSO) + level(entity, ModEffects.INJURY_LEG) > 0
                || Wounds.of(entity).stream().anyMatch(wound -> !wound.bandaged());
    }

    /** Повязка: снять самую тяжёлую травму. true — было что лечить. */
    public static boolean treat(LivingEntity entity) {
        Wounds.bandage(entity); // раны перевязаны (видно, заживают быстрее)
        boolean stopped = Bleeding.stop(entity); // кровь остановлена
        Holder<MobEffect> worst = null;
        int worstLevel = 0;
        for (Holder<MobEffect> effect : List.of(ModEffects.INJURY_HEAD, ModEffects.INJURY_TORSO, ModEffects.INJURY_ARM,
                ModEffects.INJURY_LEG)) {
            int level = level(entity, effect);
            if (level > worstLevel) {
                worstLevel = level;
                worst = effect;
            }
        }
        if (worst == null) {
            return stopped;
        }
        entity.removeEffect(worst);
        return true;
    }

    // Сотрясение: в глазах иногда темнеет
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        int head = level(player, ModEffects.INJURY_HEAD);
        if (head > 0 && player.getRandom().nextInt(Math.max(1, HEAD_DARK_EVERY / head)) == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
        }
        if (noSprint(player) && player.isSprinting()) {
            player.setSprinting(false);
        }
    }
}
