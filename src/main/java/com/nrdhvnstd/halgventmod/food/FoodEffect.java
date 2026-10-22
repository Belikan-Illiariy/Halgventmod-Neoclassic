package com.nrdhvnstd.halgventmod.food;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Эффект от еды: «Сытость», «Улучшенное насыщение», «Отравление». Сам ничего не делает — что он значит,
 * считают Hunger и Stamina (FoodEffects.fed, wellFed, poisoned). Молоко его не снимает.
 */
public class FoodEffect extends MobEffect {
    public FoodEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        // Проходит само
    }
}
