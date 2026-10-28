package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nrdhvnstd.halgventmod.client.CombatAnimation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Бой от третьего лица: после ванильной позы руки и нога модели встают по анимации удара, блока, пинка
 * (client/CombatAnimation). У игрока рукава и штанины копируются с рук и ног уже после — PlayerModel.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void halgventmod$combatPose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                        float netHeadYaw, float headPitch, CallbackInfo ci) {
        CombatAnimation.thirdPerson((HumanoidModel<?>) (Object) this, entity, ageInTicks);
    }
}
