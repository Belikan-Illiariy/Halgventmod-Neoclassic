package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.client.anim.CombatClips;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Бой: кость body анимации KCC — поворот всей модели вокруг пояса (замах корпусом, выпад), сразу после поворота
 * тела — как в Player Animation Library (client/anim/CombatClips.applyRoot).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void halgventmod$combatRoot(T entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick,
                                        float scale, CallbackInfo ci) {
        CombatClips.Active active = CombatClips.of(entity, partialTick);
        if (active != null) {
            CombatClips.applyRoot(poseStack, active);
        }
    }
}
