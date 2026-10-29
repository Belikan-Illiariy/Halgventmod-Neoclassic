package com.nrdhvnstd.halgventmod.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Броня на НПС — тот же слой, что у игрока (ArmorLayer), по ArmorInventory НПС (ModAttachments.ARMOR). Слоёв два —
 * для толстых и тонких рук; рисуется тот, что подходит НПС. Второй слой скина под бронёй прячется, как у игрока.
 */
public final class NpcArmor {
    private NpcArmor() {}

    /** Добавить к отрисовщику НПС слои брони. */
    public static void addLayers(LivingEntityRenderer<HalgNpc, PlayerModel<HalgNpc>> renderer,
                                 EntityRendererProvider.Context context) {
        renderer.addLayer(new Gate(renderer, context, false));
        renderer.addLayer(new Gate(renderer, context, true));
    }

    /** Показать или скрыть части второго слоя скина по тому, что закрыла броня (как RenderPlayerEvent у игрока). */
    public static void applyCoverage(PlayerModel<HalgNpc> model, HalgNpc npc) {
        model.hat.visible = true;
        model.jacket.visible = true;
        model.rightSleeve.visible = true;
        model.leftSleeve.visible = true;
        model.rightPants.visible = true;
        model.leftPants.visible = true;
        ArmorInventory armor = ArmorLayer.armorOf(npc);
        if (armor == null) {
            return;
        }
        ArmorCompositor.Composite outer = ArmorCompositor.get(armor, ArmorSlot.Plane.OUTER);
        if (outer != null) {
            model.hat.visible = !outer.covers(ArmorCompositor.COVERS_HEAD);
            model.jacket.visible = !outer.covers(ArmorCompositor.COVERS_BODY);
            boolean arms = outer.covers(ArmorCompositor.COVERS_ARMS);
            model.rightSleeve.visible = !arms;
            model.leftSleeve.visible = !arms;
        }
        ArmorCompositor.Composite legs = ArmorCompositor.get(armor, ArmorSlot.Plane.LEGS);
        if (legs != null && legs.covers(ArmorCompositor.COVERS_LEGS)) {
            model.rightPants.visible = false;
            model.leftPants.visible = false;
        }
    }

    // Слой для одного типа рук: рисует, только если НПС такой
    private static final class Gate extends RenderLayer<HalgNpc, PlayerModel<HalgNpc>> {
        private final ArmorLayer<HalgNpc, PlayerModel<HalgNpc>> delegate;
        private final boolean slim;

        Gate(RenderLayerParent<HalgNpc, PlayerModel<HalgNpc>> parent, EntityRendererProvider.Context context, boolean slim) {
            super(parent);
            this.slim = slim;
            this.delegate = new ArmorLayer<>(parent,
                    bake(context, slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR),
                    bake(context, slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR),
                    context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER).getChild("cloak"));
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HalgNpc npc,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (npc.isSlim() == slim) {
                delegate.render(poseStack, buffer, packedLight, npc, limbSwing, limbSwingAmount, partialTick,
                        ageInTicks, netHeadYaw, headPitch);
            }
        }

        private static HumanoidModel<HalgNpc> bake(EntityRendererProvider.Context context, ModelLayerLocation location) {
            return new HumanoidModel<>(context.bakeLayer(location));
        }
    }
}
