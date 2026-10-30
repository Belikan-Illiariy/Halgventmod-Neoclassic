package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.client.Corpses;
import com.nrdhvnstd.halgventmod.client.anim.CombatClips;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/**
 * Бой: кость body анимации KCC — поворот всей модели вокруг пояса (замах корпусом, выпад), сразу после поворота
 * тела — как в Player Animation Library (client/anim/CombatClips.applyRoot).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
    // Лежащее тело не красное: ваниль красит всё, что умирает
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void halgventmod$corpseColor(LivingEntity entity, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        if (Corpses.isBody(entity) && entity.deathTime >= 20) {
            cir.setReturnValue(OverlayTexture.NO_OVERLAY);
        }
    }

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void halgventmod$combatRoot(T entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick,
                                        float scale, CallbackInfo ci) {
        CombatClips.Active active = CombatClips.of(entity, partialTick);
        if (active != null) {
            CombatClips.applyRoot(poseStack, active);
        }
    }
}
