package com.nrdhvnstd.halgventmod.needs;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.registry.ModEffects;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Голод и жажда на нуле (docs/TZ_downed_and_hp.md, 2): урона нет, вместо него эффекты «Голодание» и «Жажда».
 * Каждый — −40 % к скорости, скорости атаки и урону и −40 % к лечению; оба сразу — −80 %.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Starvation {
    public static final double PENALTY = 0.4;  // за каждый эффект ⚠️
    private static final int CHECK_EVERY = 20; // раз в секунду

    private Starvation() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_EVERY == 0) {
            keep(player, ModEffects.STARVATION, Hunger.get(player) <= 0);
            keep(player, ModEffects.DEHYDRATION, Thirst.get(player) <= 0);
        }
    }

    // Пока причина есть — эффект без таймера, без частиц; ушла — снят
    private static void keep(ServerPlayer player, Holder<MobEffect> effect, boolean active) {
        if (active && !player.hasEffect(effect)) {
            player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        } else if (!active && player.hasEffect(effect)) {
            player.removeEffect(effect);
        }
    }

    /** Сколько лечения доходит: 1, с одним эффектом 0,6, с обоими 0,2. */
    public static float healFactor(Player player) {
        int count = (player.hasEffect(ModEffects.STARVATION) ? 1 : 0) + (player.hasEffect(ModEffects.DEHYDRATION) ? 1 : 0);
        return (float) Math.max(0, 1 - PENALTY * count);
    }

    // Здоровье само не растёт, так что любое лечение — от предмета: повязка, мазь, зелье
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setAmount(event.getAmount() * healFactor(player));
        }
    }
}
