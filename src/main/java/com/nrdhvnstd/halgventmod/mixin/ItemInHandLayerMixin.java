package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.client.anim.CombatClips;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Бой: кость item анимации KCC — оружие в кулаке разворачивается под удар лезвием (client/anim/CombatClips). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void halgventmod$combatItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                        PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (arm != entity.getMainArm()) {
            return;
        }
        CombatClips.Active active = CombatClips.of(entity, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        if (active != null) {
            CombatClips.applyItem(poseStack, active);
        }
    }
}
