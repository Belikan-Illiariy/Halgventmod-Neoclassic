package com.nrdhvnstd.halgventmod.client.cooking;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.Cookery;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.cooking.KitchenTableBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Что видно на очаге: куски на углях — сырые, прожаренные или сгоревшие (по верхней стороне). */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CookingRenderers {
    private CookingRenderers() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.HEARTH.get(), HearthRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.KITCHEN_TABLE.get(), KitchenRenderer::new);
    }

    static final class HearthRenderer implements BlockEntityRenderer<HearthBlockEntity> {
        /** Верх углей в модели жаровни Conquest. */
        private static final float COALS = 0.39F;
        private static final float SCALE = 0.32F;
        private final ItemRenderer items;
        private final ItemStack burnt = new ItemStack(ModItems.BURNT_FOOD.get());

        HearthRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(HearthBlockEntity hearth, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            Level level = hearth.getLevel();
            if (level == null || hearth.hasPot()) {
                return;
            }
            int light = LevelRenderer.getLightColor(level, hearth.getBlockPos().above());
            for (int cell = 0; cell < HearthBlockEntity.CELLS; cell++) {
                ItemStack raw = hearth.piece(cell);
                if (raw.isEmpty()) {
                    continue;
                }
                float up = hearth.upSide(cell);
                ItemStack shown = up >= HearthBlockEntity.BURNT ? burnt : raw;
                if (up >= HearthBlockEntity.READY && up < HearthBlockEntity.BURNT) {
                    ItemStack cooked = Cookery.grilled(raw, level);
                    shown = cooked.isEmpty() ? raw : cooked;
                }
                pose.pushPose();
                pose.translate(0.5F + HearthBlockEntity.cellOffset(cell % 2), COALS + SCALE / 16.0F,
                        0.5F + HearthBlockEntity.cellOffset(cell / 2));
                pose.mulPose(Axis.YP.rotationDegrees(cell * 67));
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.scale(SCALE, SCALE, SCALE);
                items.renderStatic(shown, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        level, cell);
                pose.popPose();
            }
        }
    }

    /** Разделочный стол: выложенное на доске сеткой и готовое посередине. */
    static final class KitchenRenderer implements BlockEntityRenderer<KitchenTableBlockEntity> {
        private static final float TOP = 1.0F;
        private final ItemRenderer items;

        KitchenRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(KitchenTableBlockEntity table, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            Level level = table.getLevel();
            if (level == null) {
                return;
            }
            int light = LevelRenderer.getLightColor(level, table.getBlockPos().above());
            java.util.List<ItemStack> laid = table.laid();
            for (int i = 0; i < laid.size(); i++) {
                float x = ((i % 3) - 1) * 0.25F;
                float z = ((i / 3) % 3 - 1) * 0.25F;
                flat(laid.get(i), pose, buffers, light, level, 0.5F + x, TOP + i * 0.003F, 0.5F + z, 0.35F, i * 41, i);
            }
            if (!table.result().isEmpty()) {
                flat(table.result(), pose, buffers, light, level, 0.5F, TOP + 0.02F, 0.5F, 0.5F, 20, 99);
            }
        }

        private void flat(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level,
                          float x, float y, float z, float scale, float yaw, int seed) {
            pose.pushPose();
            pose.translate(x, y + scale / 16.0F, z);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(scale, scale, scale);
            items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, seed);
            pose.popPose();
        }
    }
}
