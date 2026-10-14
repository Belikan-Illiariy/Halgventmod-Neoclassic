package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nrdhvnstd.halgventmod.pickup.Pickup;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;

/**
 * Стрелы не подбираются касанием (ТЗ 6.1) — только клавишей «Подобрать» (Pickup).
 * Трезубец с «Верностью», который летит к хозяину сквозь стены, ловится как в ванили.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void halgventmod$manualPickup(Player player, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (!self.isNoPhysics() && Pickup.manualOnly(player)) {
            ci.cancel();
        }
    }
}
