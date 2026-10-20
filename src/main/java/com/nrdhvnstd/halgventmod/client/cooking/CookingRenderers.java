package com.nrdhvnstd.halgventmod.client.cooking;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.Cookery;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
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
}
