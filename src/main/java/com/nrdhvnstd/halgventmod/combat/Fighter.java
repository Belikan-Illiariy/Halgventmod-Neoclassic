package com.nrdhvnstd.halgventmod.combat;

import com.nrdhvnstd.halgventmod.needs.Stamina;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Боец — игрок или нет: выносливость и занятость для боевой системы. У игрока — наша выносливость (needs/Stamina) и
 * его правила (Combat.busy); у НПС и мобов с ИИ боя (CombatAi) — своя шкала в CombatState: тратится так же,
 * восстанавливается после паузы, на нуле — одышка на {@link #NPC_WINDED} тиков.
 */
public final class Fighter {
    // Меняйте здесь ⚠️
    public static final float NPC_MAX = 100;
    public static final float NPC_REGEN = 0.6F;         // за тик
    public static final int NPC_REGEN_DELAY = 20;
    public static final int NPC_WINDED = 60;

    private Fighter() {}

    public static float stamina(LivingEntity entity) {
        return entity instanceof Player player ? Stamina.get(player) : Combat.state(entity).stamina;
    }

    public static boolean exhausted(LivingEntity entity) {
        return entity instanceof Player player ? Stamina.exhausted(player)
                : entity.level().getGameTime() < Combat.state(entity).windedUntil;
    }

    public static void spend(LivingEntity entity, float amount) {
        if (entity instanceof Player player) {
            Stamina.spend(player, amount);
            return;
        }
        if (amount <= 0 || exhausted(entity)) {
            return;
        }
        CombatState state = Combat.state(entity);
        state.stamina -= amount;
        state.staminaDelay = NPC_REGEN_DELAY;
        if (state.stamina <= 0) {
            state.stamina = 0;
            state.windedUntil = entity.level().getGameTime() + NPC_WINDED;
        }
    }

    /** Восстановление шкалы не-игрока (каждый тик, пока он в бою — CombatAi). */
    public static void regen(LivingEntity entity) {
        CombatState state = Combat.state(entity);
        if (state.blocking) {
            spend(entity, Defense.BLOCK_DRAIN); // поднятый блок тает, как у игрока
        } else if (state.staminaDelay > 0) {
            state.staminaDelay--;
        } else {
            state.stamina = Math.min(NPC_MAX, state.stamina + NPC_REGEN * Stamina.COMBAT_REGEN); // он всегда в бою
        }
    }

    /** Ни бить, ни блокировать. */
    public static boolean busy(LivingEntity entity) {
        return entity instanceof Player player ? Combat.busy(player)
                : !entity.isAlive() || entity.level().getGameTime() < Combat.state(entity).gripUntil;
    }
}
