package com.nrdhvnstd.halgventmod.alchemy;

import com.nrdhvnstd.halgventmod.brewing.Intoxication;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Нашатырь: ПКМ — понюхать (кристалл тратится). Резкий дух приводит в чувство: хмель −{@link Alchemy#SNIFF_SOBER},
 * проходят тошнота, слепота и тьма.
 */
public class SalAmmoniacItem extends Item {
    public SalAmmoniacItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN; // поднести к носу
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 16;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.removeEffect(MobEffects.CONFUSION);
            entity.removeEffect(MobEffects.BLINDNESS);
            entity.removeEffect(MobEffects.DARKNESS);
            if (entity instanceof Player player) {
                Intoxication.sober(player, Alchemy.SNIFF_SOBER);
                Alchemy.say(player, "sniffed");
            }
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FOX_SNIFF, SoundSource.PLAYERS,
                    0.8F, 1.4F);
            if (!(entity instanceof Player player && player.getAbilities().instabuild)) {
                stack.shrink(1);
            }
        }
        return stack;
    }
}
