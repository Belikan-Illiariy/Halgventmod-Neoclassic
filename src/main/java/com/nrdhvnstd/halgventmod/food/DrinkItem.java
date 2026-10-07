package com.nrdhvnstd.halgventmod.food;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Питьё: пьётся, а не ешется. Можно выпить, если хочется пить,
 * или если напиток сытный, а игрок голоден.
 */
public class DrinkItem extends Item {
    public DrinkItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ModFoods.FoodInfo info = ModFoods.get(stack);
        FoodProperties food = stack.get(DataComponents.FOOD);

        boolean quenches = info != null && info.thirst() > 0 && Thirst.needs(player);
        boolean feeds = food != null && food.nutrition() > 0 && player.getFoodData().needsFood();
        if (quenches || feeds || player.isCreative()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }
}