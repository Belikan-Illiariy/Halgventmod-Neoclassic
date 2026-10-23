package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.carpentry.CarpenterBenchBlockEntity;
import com.nrdhvnstd.halgventmod.carpentry.ChoppingBlockEntity;
import com.nrdhvnstd.halgventmod.carpentry.SawhorseBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Что видно на станциях плотника: бревно на колоде и на козлах, материалы и рубанок на верстаке. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CarpentryRenderers {
    /** Рубанок Conquest Reforged — всегда лежит на верстаке. */
    static final ModelResourceLocation HAND_PLANE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath("conquest", "block/hand_planer"));

    private CarpentryRenderers() {}

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(HAND_PLANE);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CHOPPING_BLOCK.get(), ChoppingRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SAWHORSE.get(), SawhorseRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CARPENTER_BENCH.get(), BenchRenderer::new);
    }

    /** Свет над станцией: сама она непрозрачна снизу, и вещи на ней были бы тёмными. */
    private static int lightAbove(BlockEntity be) {
        Level level = be.getLevel();
        return level == null ? 0xF000F0 : LevelRenderer.getLightColor(level, be.getBlockPos().above());
    }

    /** Повернуть к лицевой стороне станции (центр блока — начало координат). */
    private static void faceFront(PoseStack pose, BlockEntity be) {
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-be.getBlockState().getValue(StationBlock.FACING).toYRot()));
    }

    /** Блок бревна для отрисовки; null — предмет не блок (тогда рисуется как вещь). */
    private static BlockState logState(ItemStack log, Direction.Axis axis) {
        Block block = Block.byItem(log.getItem());
        if (block == Blocks.AIR) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, axis) : state;
    }

    /** Блок в рамке: начало координат — середина его низа. */
    private static void block(BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(-0.5F, 0.0F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        pose.popPose();
    }

    // ---------------------------------------------------------------- колода

    static final class ChoppingRenderer implements BlockEntityRenderer<ChoppingBlockEntity> {
        /** Верх пня (модель Conquest tree_stump) чуть выше блока. */
        private static final float TOP = 1.05F;
        private static final float SIZE = 0.5F;
        private final ItemRenderer items;

        ChoppingRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(ChoppingBlockEntity chopping, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            ItemStack log = chopping.log();
            if (log.isEmpty()) {
                return;
            }
            int light = lightAbove(chopping);
            pose.pushPose();
            faceFront(pose, chopping);
            pose.translate(0.0F, TOP, 0.0F);
            BlockState state = logState(log, Direction.Axis.Y);
            if (state == null) {
                pose.translate(0.0F, 0.25F, 0.0F);
                pose.scale(0.5F, 0.5F, 0.5F);
                items.renderStatic(log, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        chopping.getLevel(), 0);
            } else if (chopping.strikes() == 0) {
                pose.scale(SIZE, SIZE, SIZE);
                block(state, pose, buffers, light);
            } else {
                // Начали колоть: полено расходится на две половины, тем шире, чем больше ударов
                float gap = 0.01F + 0.015F * chopping.strikes();
                for (int side = -1; side <= 1; side += 2) {
                    pose.pushPose();
                    pose.translate(side * (SIZE / 4 + gap), 0.0F, 0.0F);
                    pose.mulPose(Axis.ZP.rotationDegrees(-side * 3.0F * chopping.strikes()));
                    pose.scale(SIZE / 2, SIZE, SIZE);
                    block(state, pose, buffers, light);
                    pose.popPose();
                }
            }
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- козлы

    static final class SawhorseRenderer implements BlockEntityRenderer<SawhorseBlockEntity> {
        /** Верх бруса в модели козел. */
        private static final float TOP = 14.0F / 16.0F;
        private static final float THICK = 0.5F;
        private final ItemRenderer items;

        SawhorseRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(SawhorseBlockEntity sawhorse, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            ItemStack log = sawhorse.log();
            if (log.isEmpty()) {
                return;
            }
            int light = lightAbove(sawhorse);
            pose.pushPose();
            // После поворота к лицевой стороне ось X — вдоль бруса
            faceFront(pose, sawhorse);
            pose.translate(0.0F, TOP, 0.0F);
            BlockState state = logState(log, Direction.Axis.X);
            if (state == null) {
                pose.translate(0.0F, 0.25F, 0.0F);
                pose.scale(0.6F, 0.6F, 0.6F);
                items.renderStatic(log, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        sawhorse.getLevel(), 0);
            } else {
                pose.scale(1.0F, THICK, THICK);
                block(state, pose, buffers, light);
            }
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- верстак

    static final class BenchRenderer implements BlockEntityRenderer<CarpenterBenchBlockEntity> {
        private static final float TOP = 1.0F;
        private final ItemRenderer items;

        BenchRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(CarpenterBenchBlockEntity bench, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            Level level = bench.getLevel();
            int light = lightAbove(bench);

            // Рубанок — в правом заднем углу столешницы, наискосок
            BakedModel plane = Minecraft.getInstance().getModelManager().getModel(HAND_PLANE);
            pose.pushPose();
            faceFront(pose, bench);
            pose.translate(-0.24F, TOP, -0.26F);
            pose.mulPose(Axis.YP.rotationDegrees(25));
            pose.scale(0.6F, 0.6F, 0.6F);
            pose.translate(-0.5F, 0.0F, -0.5F);
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                    buffers.getBuffer(RenderType.cutout()), bench.getBlockState(), plane, 1.0F, 1.0F, 1.0F, light,
                    packedOverlay, ModelData.EMPTY, RenderType.cutout());
            pose.popPose();

            // Доски и прочее — на передней половине
            List<ItemStack> laid = bench.laid();
            for (int i = 0; i < laid.size(); i++) {
                pose.pushPose();
                faceFront(pose, bench);
                float x = ((i % 3) - 1) * 0.26F;
                float z = 0.12F + ((i / 3) % 3) * 0.12F;
                pose.translate(x, TOP + i * 0.004F, z);
                pose.mulPose(Axis.YP.rotationDegrees(i * 29));
                lying(items, laid.get(i), pose, buffers, light, level, 0.36F, i);
                pose.popPose();
            }
        }
    }

    /** Вещь, лежащая плашмя (блок — стоит). Начало координат — точка поверхности, на которой она лежит. */
    private static void lying(ItemRenderer items, ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                              int light, Level level, float scale, int seed) {
        pose.pushPose();
        if (stack.getItem() instanceof BlockItem) {
            pose.translate(0.0F, scale * 0.35F, 0.0F);
            pose.scale(scale * 0.7F, scale * 0.7F, scale * 0.7F);
        } else {
            // Плоский предмет в FIXED толщиной в пиксель по центру — приподнимаем на полтолщины с запасом
            pose.translate(0.0F, scale / 16.0F, 0.0F);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(scale, scale, scale);
        }
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, seed);
        pose.popPose();
    }
}
