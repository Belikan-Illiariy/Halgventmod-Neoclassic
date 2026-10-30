package com.nrdhvnstd.halgventmod.combat;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/** Травма (combat/Injuries): молоко и мёд не лечат — повязка или время. */
public class InjuryEffect extends MobEffect {
    public InjuryEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // не снимается молоком
    }
}
