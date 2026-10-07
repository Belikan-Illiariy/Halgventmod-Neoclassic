package com.nrdhvnstd.halgventmod.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nrdhvnstd.halgventmod.registry.ModPaintings;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

@Mixin(Painting.class)
public abstract class PaintingMixin {

    // При поломке картины из мода выпадает её собственный предмет, а не обычная картина
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void halgventmod$dropOwnItem(@Nullable Entity brokenEntity, CallbackInfo ci) {
        Painting self = (Painting) (Object) this;
        Item item = ModPaintings.itemFor(self.getVariant());
        if (item == null) {
            return; // ванильная картина или картина другого мода: всё как обычно
        }

        ci.cancel();
        if (!self.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            return;
        }
        self.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
        if (brokenEntity instanceof Player player && player.hasInfiniteMaterials()) {
            return; // в креативе предмет не выпадает, как у ванильных картин
        }
        self.spawnAtLocation(item);
    }

    // Средняя кнопка мыши по картине в креативе даёт её собственный предмет
    @Inject(method = "getPickResult", at = @At("HEAD"), cancellable = true)
    private void halgventmod$pickOwnItem(CallbackInfoReturnable<ItemStack> cir) {
        Item item = ModPaintings.itemFor(((Painting) (Object) this).getVariant());
        if (item != null) {
            cir.setReturnValue(new ItemStack(item));
        }
    }
}