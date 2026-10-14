package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nrdhvnstd.halgventmod.client.PickupInput;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Вещь, которую можно подобрать, обведена контуром — только у себя на экране (PickupInput). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void halgventmod$pickupOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == PickupInput.target()) {
            cir.setReturnValue(true);
        }
    }
}
