package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.client.WeaponBloodRender;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Кровь на оружии (client/WeaponBloodRender). Рисуем в самом начале render, раньше предмета: дальше по методу Sodium может
 * забрать отрисовку себе и выйти, и вызов граней не наступит. Пятна чуть ближе граней, так что предмет их не закроет.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void halgventmod$blood(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                   MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        WeaponBloodRender.renderItem(stack, context, leftHand, pose, buffers, light, overlay, model);
    }
}
