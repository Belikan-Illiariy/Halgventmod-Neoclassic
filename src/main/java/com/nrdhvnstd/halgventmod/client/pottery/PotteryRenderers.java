package com.nrdhvnstd.halgventmod.client.pottery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.pottery.DryingShelfBlockEntity;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.pottery.PotteryWheelBlockEntity;
import com.nrdhvnstd.halgventmod.pottery.Vessel;
import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.VesselGeometry;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Как видна глина на станциях: ком на круге, сырец на полке — своей формой по вокселям. Как предметы сырец и
 * посуда — плоские иконки ({@link PotteryIcons}).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class PotteryRenderers {
    private PotteryRenderers() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.POTTERY_WHEEL.get(), WheelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DRYING_SHELF.get(), ShelfRenderer::new);
    }

    /** Свет над станцией: сама она непрозрачна, и вещи на ней были бы тёмными. */
    private static int lightAbove(BlockEntity be) {
        Level level = be.getLevel();
        return level == null ? 0xF000F0 : LevelRenderer.getLightColor(level, be.getBlockPos().above());
    }

    // ---------------------------------------------------------------- круг

    /** Комья глины на круге: невысокий цилиндр, тем больше, чем больше глины. */
    static final class WheelRenderer implements BlockEntityRenderer<PotteryWheelBlockEntity> {
        /** Голова круга в модели (models/block/pottery_wheel_foot.json): на стыке частей, высота 17,5 пикселя. */
        private static final float TOP = 17.6F / 16.0F;
        private static final Vessel LUMP = new Vessel(VesselForm.POT.id(), VesselGeometry.lump(420), new int[0]);

        WheelRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(PotteryWheelBlockEntity wheel, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            if (wheel.lumps() <= 0) {
                return;
            }
            Direction facing = wheel.getBlockState().getValue(StationBlock.FACING);
            float scale = 0.16F + 0.03F * wheel.lumps();
            pose.pushPose();
            pose.translate(0.5F + facing.getStepX() * 0.5F, TOP, 0.5F + facing.getStepZ() * 0.5F);
            pose.scale(scale, scale, scale);
            pose.translate(-0.5F, 0, -0.5F);
            VoxelRender.render(LUMP, VoxelRender.RAW, pose, buffers, lightAbove(wheel), OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- полка

    /** Сырец на ярусах стеллажа: по два на нижней и верхней доске. */
    static final class ShelfRenderer implements BlockEntityRenderer<DryingShelfBlockEntity> {
        private static final float MAX_SCALE = 0.38F;     // между досками 6,5 пикселя
        private final ItemRenderer items;

        ShelfRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(DryingShelfBlockEntity shelf, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            Direction facing = shelf.getBlockState().getValue(StationBlock.FACING);
            int light = lightAbove(shelf);
            for (int i = 0; i < DryingShelfBlockEntity.SLOTS; i++) {
                ItemStack stack = shelf.slot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                float[] spot = DryingShelfBlockEntity.spot(i);
                float[] world = toWorld(facing, spot[0], spot[2]);
                pose.pushPose();
                pose.translate(world[0], spot[1], world[1]);
                pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
                if (Pottery.isGreenware(stack)) {
                    VoxelRender.renderPlaced(stack, MAX_SCALE, pose, buffers, light);
                } else {
                    // Кирпич-сырец или размокшая глина — лежит
                    pose.translate(0, 0.06F, 0);
                    pose.mulPose(Axis.XP.rotationDegrees(-90));
                    pose.scale(0.35F, 0.35F, 0.35F);
                    items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                            shelf.getLevel(), i);
                }
                pose.popPose();
            }
        }

        /** Точка доски (mx — вдоль, mz — вглубь, как у полки, смотрящей на север) в координатах блока. */
        private static float[] toWorld(Direction facing, float mx, float mz) {
            return switch (facing) {
                case EAST -> new float[] {1 - mz, mx};
                case SOUTH -> new float[] {1 - mx, 1 - mz};
                case WEST -> new float[] {mz, 1 - mx};
                default -> new float[] {mx, mz};
            };
        }
    }
}
