package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nrdhvnstd.halgventmod.food.Hunger;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;

/**
 * Ванильный голод отключён: его заменяет Hunger (0–100). FoodData остаётся на полном,
 * чтобы ванильные проверки (бег, «можно ли есть») не мешали — их решают наши миксины.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    // Чей это голод. FoodData не знает своего игрока, запоминаем его при каждом тике
    @Unique
    private Player halgventmod$owner;

    // Ни траты, ни лечения, ни урона от ванильного голода
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void halgventmod$disableTick(Player player, CallbackInfo ci) {
        halgventmod$owner = player;
        ci.cancel();
    }

    // Еду съедают через Player.eat — её считает PlayerMixin, зная сам предмет
    @Inject(method = "eat(Lnet/minecraft/world/food/FoodProperties;)V", at = @At("HEAD"), cancellable = true)
    private void halgventmod$ignoreFood(FoodProperties food, CallbackInfo ci) {
        ci.cancel();
    }

    // Торт, эффект насыщения и прочее, что кормит напрямую: ванильные единицы ×5
    @Inject(method = "eat(IF)V", at = @At("HEAD"), cancellable = true)
    private void halgventmod$redirectEat(int nutrition, float saturationModifier, CallbackInfo ci) {
        if (halgventmod$owner != null) {
            Hunger.add(halgventmod$owner, nutrition * Hunger.VANILLA_SCALE,
                    FoodConstants.saturationByModifier(nutrition, saturationModifier) * Hunger.VANILLA_SCALE);
        }
        ci.cancel();
    }
}
