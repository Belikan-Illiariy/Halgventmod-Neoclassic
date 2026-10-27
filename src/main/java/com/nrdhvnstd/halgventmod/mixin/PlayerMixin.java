package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

@Mixin(Player.class)
public abstract class PlayerMixin {

    // Любая трата голода: перегруз её увеличивает, и столько же уходит в жажду
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float halgventmod$modifyExhaustion(float exhaustion) {
        Player self = (Player) (Object) this;
        float modified = exhaustion * (1.0F + self.getData(ModAttachments.OVERWEIGHT_PENALTY));
        if (!self.level().isClientSide && !self.getAbilities().invulnerable) {
            Hunger.addExhaustion(self, modified);
            Thirst.addExhaustion(self, modified);
        }
        return modified;
    }

    // Съел — кормит наш голод (ванильный FoodData.eat отключён в FoodDataMixin)
    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"))
    private void halgventmod$eat(Level level, ItemStack food, FoodProperties properties, CallbackInfoReturnable<ItemStack> cir) {
        if (!level.isClientSide) {
            Hunger.eat((Player) (Object) this, food, properties);
        }
    }

    // Есть можно, когда не сыт по нашему голоду
    @Inject(method = "canEat", at = @At("HEAD"), cancellable = true)
    private void halgventmod$canEat(boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        cir.setReturnValue(self.getAbilities().invulnerable || canAlwaysEat || Hunger.needs(self));
    }

    // Здоровье само не восстанавливается и на мирной сложности (docs/TZ_downed_and_hp.md, 1)
    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void halgventmod$noPeacefulRegen(Player self, float amount) {
    }
}
