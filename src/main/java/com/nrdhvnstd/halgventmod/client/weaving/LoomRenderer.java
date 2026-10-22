package com.nrdhvnstd.halgventmod.client.weaving;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.weaving.Fiber;
import com.nrdhvnstd.halgventmod.weaving.LoomBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Ткань на станке: полотно Conquest (models/block/loom_weave_&lt;волокно&gt;) растёт от верхнего навоя вниз по мере
 * прокидок. Раму, нити основы и грузила рисует сама модель блока.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class LoomRenderer implements BlockEntityRenderer<LoomBlockEntity> {
    /** Верхний край полотна в модели Conquest, в блоках. */
    private static final float WEAVE_TOP = 1.977F;

    LoomRenderer(BlockEntityRendererProvider.Context context) {}

    private static ModelResourceLocation weave(Fiber fiber) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "block/loom_weave_" + fiber.id()));
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (Fiber fiber : Fiber.values()) {
            event.register(weave(fiber));
        }
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LOOM.get(), LoomRenderer::new);
    }

    @Override
    public void render(LoomBlockEntity loom, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Fiber fiber = loom.warp();
        float woven = loom.woven();
        if (fiber == null || woven <= 0) {
            return;
        }
        BlockState state = loom.getBlockState();
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(weave(fiber));
        // Как поворот модели в состоянии блока: north — 0°, east — 90° …
        float yaw = state.getValue(StationBlock.FACING).toYRot() + 180;
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(-0.5F, 0.0F, -0.5F);
        pose.translate(0.0F, WEAVE_TOP, 0.0F);
        pose.scale(1.0F, Math.min(1.0F, woven), 1.0F);
        pose.translate(0.0F, -WEAVE_TOP, 0.0F);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(RenderType.cutout()), state, model, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay,
                ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(LoomBlockEntity loom) {
        return new AABB(loom.getBlockPos()).expandTowards(0, 1, 0).inflate(0.5);
    }
}
