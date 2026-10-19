package com.nrdhvnstd.halgventmod.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nrdhvnstd.halgventmod.client.HitboxGuard;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Shadow
    protected abstract void debugWarningTranslated(String message, Object... args);

    // F3+B: хитбоксы — только администраторам. Клавиша считается нажатой (true),
    // чтобы F3 после неё не открыл экран отладки
    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void halgventmod$hitboxesForAdmins(int key, CallbackInfoReturnable<Boolean> cir) {
        if (key == GLFW.GLFW_KEY_B && !HitboxGuard.allowed(minecraft.player)) {
            debugWarningTranslated("debug.halgventmod.hitboxes.no_permission");
            cir.setReturnValue(true);
        }
    }
}
