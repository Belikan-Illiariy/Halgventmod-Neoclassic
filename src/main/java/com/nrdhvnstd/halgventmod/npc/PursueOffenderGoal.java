package com.nrdhvnstd.halgventmod.npc;

import com.nrdhvnstd.halgventmod.downed.Downed;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Стражник выбирает целью игрока в розыске, если сам видит его не дальше Crimes.WITNESS_RANGE блоков. Бьёт, пока тот
 * не упадёт («Повержен») — лежачего не добивает: цель снимается, розыск снимается (наказан). Подниметься и снова
 * нарушить — новое правонарушение. Сам бой — MeleeAttackGoal в HalgNpc, он идёт через боевую систему и броню.
 */
final class PursueOffenderGoal extends NearestAttackableTargetGoal<Player> {
    private static final int RANDOM_INTERVAL = 10;

    PursueOffenderGoal(HalgNpc guard) {
        super(guard, Player.class, RANDOM_INTERVAL, true, false, target -> target instanceof Player player
                && guard.getRole() == NpcRole.GUARD && Crimes.isWanted(player) && !Downed.isDowned(player)
                && guard.distanceToSqr(player) <= Crimes.WITNESS_RANGE * Crimes.WITNESS_RANGE);
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getTarget() instanceof Player player) {
            if (Downed.isDowned(player) || !Crimes.isWanted(player)
                    || mob.distanceToSqr(player) > Crimes.WITNESS_RANGE * Crimes.WITNESS_RANGE) {
                return false;
            }
        }
        return super.canContinueToUse();
    }

    @Override
    public void stop() {
        if (mob.getTarget() instanceof Player player && Downed.isDowned(player)) {
            Crimes.pardon(player); // сбит с ног — наказан
        }
        super.stop();
    }
}
