package com.nrdhvnstd.halgventmod.needs;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * «Голодание» и «Жажда» (docs/TZ_downed_and_hp.md, 2): висят, пока голод или жажда на нуле (Starvation).
 * −40 % к скорости, скорости атаки и урону — модификаторы атрибутов (ModEffects). Молоко не снимает.
 */
public class NeedEffect extends MobEffect {
    public NeedEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // Снимает только еда или питьё
    }
}
