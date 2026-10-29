package com.nrdhvnstd.halgventmod.client.brewing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.brewing.WinePressBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Винт и ворот давильни: части «screw» и «handle» модели Conquest (models/block/wine_press_screw), станину рисует
 * сама модель блока (wine_press_frame). На каждое нажатие ворот с винтом делает часть оборота и винт чуть уходит вниз.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class WinePressRenderer implements BlockEntityRenderer<WinePressBlockEntity> {
    // Меняйте здесь
    private static final float TURN_DEGREES = 120.0F;  // поворот за нажатие (3 нажатия на плод — полный оборот)
    private static final float TURN_TICKS = 10.0F;     // сколько длится поворот
    private static final float PRESS_DEPTH = 1.5F;     // на сколько пикселей винт опускается в середине нажатия

    private static final ModelResourceLocation SCREW = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "block/wine_press_screw"));

    WinePressRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(SCREW);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.WINE_PRESS.get(), WinePressRenderer::new);
    }

    @Override
    public void render(WinePressBlockEntity press, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockState state = press.getBlockState();
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(SCREW);
        float progress = 1.0F;
        if (press.getLevel() != null && press.turns() > 0) {
            progress = Mth.clamp((press.getLevel().getGameTime() - press.turnStart() + partialTick) / TURN_TICKS, 0.0F, 1.0F);
        }
        float eased = progress * progress * (3.0F - 2.0F * progress); // плавно разогнать и остановить
        float angle = (Math.max(0, press.turns() - 1) + eased) * TURN_DEGREES;
        if (press.turns() == 0) {
            angle = 0.0F;
        }
        float sink = Mth.sin(progress * Mth.PI) * PRESS_DEPTH / 16.0F;
        float yaw = state.getValue(StationBlock.FACING).toYRot() + 180;
        pose.pushPose();
        pose.translate(0.5F, -sink, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw - angle));
        pose.translate(-0.5F, 0.0F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(RenderType.cutout()), state, model, 1.0F, 1.0F, 1.0F, packedLight, packedOverlay,
                ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(WinePressBlockEntity press) {
        return new AABB(press.getBlockPos()).expandTowards(0, 1, 0).inflate(0.3);
    }
}
