package com.nrdhvnstd.halgventmod.alchemy;

import com.nrdhvnstd.halgventmod.food.DrinkItem;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Снадобье в склянке: пьётся всегда (даже сытым и без жажды), глоток — его действие ({@link Alchemy.Remedy}). */
public class RemedyItem extends DrinkItem {
    private final Alchemy.Remedy remedy;

    public RemedyItem(Properties properties, Alchemy.Remedy remedy) {
        super(properties);
        this.remedy = remedy;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            remedy.apply(entity, stack);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
