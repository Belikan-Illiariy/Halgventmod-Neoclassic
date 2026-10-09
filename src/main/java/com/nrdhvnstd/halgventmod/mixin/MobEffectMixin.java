package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * «Сила» и «Слабость» меняют урон в процентах, а не на ванильные +3 / −4 к каждому удару:
 * так урон в окне «Персонаж» показывается честным процентом, и будущие навыки складываются с ними так же.
 * Значение умножается на уровень эффекта: Сила II = +40%.
 */
@Mixin(MobEffect.class)
public abstract class MobEffectMixin {
    private static final double STRENGTH_PER_LEVEL = 0.2;  // +20% урона
    private static final double WEAKNESS_PER_LEVEL = -0.2; // −20% урона

    private static final ResourceLocation STRENGTH = ResourceLocation.withDefaultNamespace("effect.strength");
    private static final ResourceLocation WEAKNESS = ResourceLocation.withDefaultNamespace("effect.weakness");

    @Inject(method = "addAttributeModifier(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceLocation;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)Lnet/minecraft/world/effect/MobEffect;",
            at = @At("HEAD"), cancellable = true)
    private void halgventmod$percentDamage(Holder<Attribute> attribute, ResourceLocation id, double amount,
                                           AttributeModifier.Operation operation,
                                           CallbackInfoReturnable<MobEffect> cir) {
        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            return; // уже переделанный (и повторный вызов отсюда же)
        }
        double percent;
        if (id.equals(STRENGTH)) {
            percent = STRENGTH_PER_LEVEL;
        } else if (id.equals(WEAKNESS)) {
            percent = WEAKNESS_PER_LEVEL;
        } else {
            return;
        }
        MobEffect self = (MobEffect) (Object) this;
        cir.setReturnValue(self.addAttributeModifier(attribute, id, percent,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
