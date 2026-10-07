package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.world.entity.player.Player;

@Mixin(Player.class)
public abstract class PlayerMixin {

    // Любая трата голода: перегруз её увеличивает, и столько же уходит в жажду
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float halgventmod$modifyExhaustion(float exhaustion) {
        Player self = (Player) (Object) this;
        float modified = exhaustion * (1.0F + self.getData(ModAttachments.OVERWEIGHT_PENALTY));
        if (!self.level().isClientSide && !self.getAbilities().invulnerable) {
            Thirst.addExhaustion(self, modified);
        }
        return modified;
    }
}