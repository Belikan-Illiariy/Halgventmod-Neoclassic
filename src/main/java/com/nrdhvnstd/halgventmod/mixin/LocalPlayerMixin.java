package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nrdhvnstd.halgventmod.food.Thirst;

import net.minecraft.client.player.LocalPlayer;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    // При сильной жажде бегать нельзя — так же, как при сильном голоде
    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("RETURN"), cancellable = true)
    private void halgventmod$thirstStopsSprint(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (cir.getReturnValueZ() && !self.isPassenger() && !self.getAbilities().mayfly
                && Thirst.get(self) <= Thirst.NO_SPRINT_AT) {
            cir.setReturnValue(false);
        }
    }
}