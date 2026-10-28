package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.client.CombatAnimation;
import com.nrdhvnstd.halgventmod.draw.Drawing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Доставание предмета (Drawing): ваниль опускает старый предмет и поднимает новый за пару тиков —
 * растягиваем подъём на время доставания. Высота руки не выше, чем предмет успел подняться.
 * Бой: рука с оружием в основной руке поворачивается по анимации удара, блока и прочего (client/CombatAnimation).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private float mainHandHeight;

    // Сразу после pushPose в renderArmWithItem — до ванильной раскладки руки: поворот вокруг «плеча»
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void halgventmod$combatPose(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
                                        float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack,
                                        MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (hand == InteractionHand.MAIN_HAND) {
            CombatAnimation.firstPerson(poseStack, player, partialTick);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void halgventmod$slowDraw(CallbackInfo ci) {
        if (minecraft.player != null) {
            mainHandHeight = Math.min(mainHandHeight, Drawing.progress(minecraft.player, 0));
        }
    }
}
