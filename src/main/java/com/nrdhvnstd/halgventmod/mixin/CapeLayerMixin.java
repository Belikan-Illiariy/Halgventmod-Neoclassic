package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.client.armor.ArmorLayer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;

@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    // Под плащом из брони ванильный плащ из скина не рисуется
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void halgventmod$hideUnderArmorCape(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch,
                                                CallbackInfo ci) {
        if (ArmorLayer.wearsCape(player)) {
            ci.cancel();
        }
    }
}
