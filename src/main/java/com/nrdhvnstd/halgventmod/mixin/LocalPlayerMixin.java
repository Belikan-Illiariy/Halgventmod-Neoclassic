package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.needs.Stamina;

import net.minecraft.client.player.LocalPlayer;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    // Бег: ванильная проверка голода всегда проходит (FoodData стоит на полном), решают наши шкалы.
    // Эта же проверка останавливает уже начатый бег, в том числе бег вплавь
    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("RETURN"), cancellable = true)
    private void halgventmod$needsStopSprint(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (cir.getReturnValueZ() && !self.isPassenger() && !self.getAbilities().mayfly
                && (Hunger.get(self) <= Hunger.NO_SPRINT_AT
                        || Thirst.get(self) <= Thirst.NO_SPRINT_AT
                        || !Stamina.canSprint(self))) {
            cir.setReturnValue(false);
        }
    }
}
