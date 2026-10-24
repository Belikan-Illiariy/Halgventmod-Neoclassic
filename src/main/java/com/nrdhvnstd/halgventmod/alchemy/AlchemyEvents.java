package com.nrdhvnstd.halgventmod.alchemy;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Алхимия в бою: клинок, намазанный сулемой, отравляет — удар за ударом, пока яд не сотрётся. */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class AlchemyEvents {
    private AlchemyEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        // Только удар в ближнем бою — не стрела и не брошенное
        if (target.level().isClientSide || event.getNewDamage() <= 0 || !(source.getEntity() instanceof LivingEntity attacker)
                || source.getDirectEntity() != attacker) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        int hits = Alchemy.coat(weapon);
        if (hits <= 0) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.POISON, Alchemy.COAT_POISON_TICKS, 1), attacker);
        Alchemy.setCoat(weapon, hits - 1);
        if (hits == 1 && attacker instanceof Player player) {
            Alchemy.say(player, "coat_worn");
        }
    }
}
