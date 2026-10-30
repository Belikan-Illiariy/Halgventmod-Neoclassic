package com.nrdhvnstd.halgventmod.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.armor.ArmorRenderEvents;
import com.nrdhvnstd.halgventmod.combat.anim.BladePose;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Руки от первого лица в бою — как «firstperson» у Kingdom Come Combat: пока у игрока анимация боя (удар, блок,
 * стойка, оглушение — CombatClips), ванильная рука не рисуется, а рисуются обе руки модели игрока с оружием в той же
 * позе, что видят другие, из глаз. Отсюда размах через весь экран, обе руки у двуручного и клинок, идущий лезвием.
 * Модель стоит в пространстве камеры: ступни на высоте глаз ниже, лицом туда, куда смотрим, — руки идут за взглядом.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class FirstPersonCombat {
    // Меняйте здесь ⚠️
    private static final float MODEL_SCALE = BladePose.MODEL_SCALE;
    private static final float FORWARD = BladePose.FORWARD; // руки чуть вперёд из камеры — как клинок на сервере

    private FirstPersonCombat() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isInvisible() || player.isSleeping()) {
            return;
        }
        CombatClips.Active active = CombatClips.of(player, event.getPartialTick());
        if (active == null || active.strength() <= 0.05F) {
            return;
        }
        event.setCanceled(true); // обе ванильные руки — наши
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            render(minecraft, player, active, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                    event.getPartialTick());
        }
    }

    private static void render(Minecraft minecraft, AbstractClientPlayer player, CombatClips.Active active, PoseStack pose,
                               MultiBufferSource buffers, int light, float partialTick) {
        if (!(minecraft.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        // Поза с нуля: никакой ходьбы и взмаха — только анимация боя
        model.attackTime = 0;
        model.crouching = false;
        model.riding = false;
        model.rightArm.resetPose();
        model.leftArm.resetPose();
        model.body.resetPose();
        model.head.resetPose();
        CombatClips.apply(model, player, active);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);

        pose.pushPose();
        // Камера в глазах; ступни — ниже на рост глаз; лицом вперёд (−Z камеры)
        pose.translate(0, -player.getEyeHeight(), FORWARD);
        CombatClips.applyRoot(pose, active); // поворот корпуса — руки идут через весь экран
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        pose.translate(0, -1.501F, 0);

        var skin = player.getSkin().texture();
        for (HumanoidArm arm : HumanoidArm.values()) {
            boolean right = arm == HumanoidArm.RIGHT;
            var buffer = buffers.getBuffer(RenderType.entityTranslucent(skin)); // заново: броня меняет буфер
            (right ? model.rightArm : model.leftArm).render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            (right ? model.rightSleeve : model.leftSleeve).render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            ArmorRenderEvents.renderPosedArm(pose, buffers, light, player, arm); // рукав лат и перчатка
        }
        // Что в руках — как ItemInHandLayer; в главной руке — с костью item анимации
        for (HumanoidArm arm : HumanoidArm.values()) {
            boolean main = arm == player.getMainArm();
            ItemStack stack = main ? player.getMainHandItem() : player.getOffhandItem();
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            model.translateToHand(arm, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            boolean left = arm == HumanoidArm.LEFT;
            pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            if (main) {
                CombatClips.applyItem(pose, active);
            }
            minecraft.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, pose, buffers, light);
            pose.popPose();
        }
        pose.popPose();
    }
}
