package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nrdhvnstd.halgventmod.draw.Drawing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;

/**
 * Доставание предмета (Drawing): ваниль опускает старый предмет и поднимает новый за пару тиков —
 * растягиваем подъём на время доставания. Высота руки не выше, чем предмет успел подняться.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private float mainHandHeight;

    @Inject(method = "tick", at = @At("TAIL"))
    private void halgventmod$slowDraw(CallbackInfo ci) {
        if (minecraft.player != null) {
            mainHandHeight = Math.min(mainHandHeight, Drawing.progress(minecraft.player, 0));
        }
    }
}
