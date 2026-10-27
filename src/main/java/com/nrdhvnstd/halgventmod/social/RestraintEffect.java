package com.nrdhvnstd.halgventmod.social;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * «Мешок на голове», «В кандалах», «В наручниках» (docs/TZ_social_interactions.md, 5): без таймера, пока не снимет
 * другой игрок. Замедление кандалов и ослабление удара в наручниках — модификаторы атрибутов (ModEffects),
 * остальное — Social и CuffsRules.
 */
public class RestraintEffect extends MobEffect {
    public RestraintEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // Молоко и мёд не снимают: только другой игрок (ТЗ 6)
    }
}
