package com.nrdhvnstd.halgventmod.weight;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Эффект-индикатор перегруза. Сам ничего не делает:
 * штрафы накладывает Encumbrance, а эффект показывает игроку, что происходит.
 */
public class OverweightEffect extends MobEffect {
    public OverweightEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // Молоко и мёд не снимают: эффект держится, пока лишний вес при игроке
    }
}