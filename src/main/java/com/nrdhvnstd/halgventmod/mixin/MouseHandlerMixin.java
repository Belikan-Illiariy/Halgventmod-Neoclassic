package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.nrdhvnstd.halgventmod.client.CombatInput;
import com.nrdhvnstd.halgventmod.client.CombatLock;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

/** Захват цели (CombatLock): мышь не крутит камеру — её движение выбирает сторону удара и блока (CombatInput). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void halgventmod$lockedTurn(LocalPlayer player, double yaw, double pitch) {
        if (CombatLock.locked()) {
            CombatInput.virtualTurn(yaw, pitch);
        } else {
            player.turn(yaw, pitch);
        }
    }
}
