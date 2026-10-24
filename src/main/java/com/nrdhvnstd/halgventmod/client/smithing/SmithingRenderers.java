package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.smithing.CupelBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.SmithStationBlock;
import com.nrdhvnstd.halgventmod.smithing.MortarBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.SmithingBlocks;
import com.nrdhvnstd.halgventmod.smithing.WorkshopBlockEntity;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Что видно на станциях кузнечества: металл и прочее на наковальне и литейном столе, руда в ступе, свинец на купели, тигель в горне. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class SmithingRenderers {
    private SmithingRenderers() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SmithingBlocks.ANVIL_ENTITY.get(), context -> new LaidRenderer<>(context, 1.0F, 0.28F, true));
        event.registerBlockEntityRenderer(SmithingBlocks.CASTING_ENTITY.get(), context -> new LaidRenderer<>(context, 1.0F, 0.34F, false));
        event.registerBlockEntityRenderer(SmithingBlocks.MORTAR_ENTITY.get(), Inside::new);
        event.registerBlockEntityRenderer(SmithingBlocks.CUPEL_ENTITY.get(), OnCupel::new);
        event.registerBlockEntityRenderer(SmithingBlocks.FORGE_ENTITY.get(), OnForge::new);
    }

    private static int lightAbove(BlockEntity be) {
        Level level = be.getLevel();
        return level == null ? 0xF000F0 : LevelRenderer.getLightColor(level, be.getBlockPos().above());
    }

    /** Выложенное на станцию — плашмя, рядами. */
    static final class LaidRenderer<T extends WorkshopBlockEntity> implements BlockEntityRenderer<T> {
        private final ItemRenderer items;
        private final float top;
        private final float scale;
        private final boolean narrow;

        /** narrow — наковальня: лицо узкое и длинное (вдоль Z модели), вещи — в два ряда вдоль него. */
        LaidRenderer(BlockEntityRendererProvider.Context context, float top, float scale, boolean narrow) {
            this.items = context.getItemRenderer();
            this.top = top;
            this.scale = scale;
            this.narrow = narrow;
        }

        @Override
        public void render(T station, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight,
                           int packedOverlay) {
            List<ItemStack> laid = station.laid();
            int light = lightAbove(station);
            for (int i = 0; i < laid.size(); i++) {
                pose.pushPose();
                pose.translate(0.5F, 0.0F, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState().getValue(StationBlock.FACING).toYRot()));
                float x = narrow ? ((i % 2) - 0.5F) * 0.13F : ((i % 3) - 1) * 0.2F;
                float z = narrow ? (i / 2 - 2) * 0.12F + 0.02F : ((i / 3) % 3 - 1) * 0.14F;
                pose.translate(x, top + i * 0.004F, z);
                pose.mulPose(Axis.YP.rotationDegrees(i * 31));
                lying(items, laid.get(i), pose, buffers, light, station.getLevel(), scale, i);
                pose.popPose();
            }
        }
    }

    /** Ступа: что внутри — горкой посередине. */
    static final class Inside implements BlockEntityRenderer<BlockEntity> {
        private final ItemRenderer items;

        Inside(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight,
                           int packedOverlay) {
            ItemStack inside = be instanceof MortarBlockEntity mortar ? mortar.inside() : ItemStack.EMPTY;
            if (inside.isEmpty()) {
                return;
            }
            float top = 0.42F;
            int light = lightAbove(be);
            for (int i = 0; i < Math.min(4, inside.getCount()); i++) {
                pose.pushPose();
                pose.translate(0.5F + (i % 2 - 0.5F) * 0.1F, top + i * 0.02F, 0.5F + (i / 2 - 0.5F) * 0.1F);
                pose.mulPose(Axis.YP.rotationDegrees(i * 67));
                lying(items, inside, pose, buffers, light, be.getLevel(), 0.28F, i);
                pose.popPose();
            }
        }
    }

    /** Купель: на дне чаши — уголь (сколько подложено), на нём — куски свинца с серебром. */
    static final class OnCupel implements BlockEntityRenderer<CupelBlockEntity> {
        private static final float FLOOR = 0.26F; // дно чаши горелки, блоки
        private static final ItemStack CHARCOAL = new ItemStack(net.minecraft.world.item.Items.CHARCOAL);
        private final ItemRenderer items;

        OnCupel(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(CupelBlockEntity cupel, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight,
                           int packedOverlay) {
            int light = cupel.getBlockState().getValue(SmithStationBlock.Lit.LIT) ? 0xF000F0 : lightAbove(cupel);
            int fuel = Math.min(4, cupel.fuel());
            for (int i = 0; i < fuel; i++) {
                pose.pushPose();
                pose.translate(0.5F + (i % 2 - 0.5F) * 0.16F, FLOOR, 0.5F + (i / 2 - 0.5F) * 0.16F);
                pose.mulPose(Axis.YP.rotationDegrees(i * 97 + 20));
                lying(items, CHARCOAL, pose, buffers, light, cupel.getLevel(), 0.3F, 10 + i);
                pose.popPose();
            }
            ItemStack lead = cupel.lead();
            float top = FLOOR + (fuel > 0 ? 0.035F : 0);
            for (int i = 0; i < Math.min(4, lead.getCount()); i++) {
                pose.pushPose();
                pose.translate(0.5F + (i % 2 - 0.5F) * 0.1F, top + i * 0.015F, 0.5F + (i / 2 - 0.5F) * 0.1F);
                pose.mulPose(Axis.YP.rotationDegrees(i * 67));
                lying(items, lead, pose, buffers, light, cupel.getLevel(), 0.26F, i);
                pose.popPose();
            }
        }
    }

    /** Тигель в кузнечном горне: стоит на углях. */
    static final class OnForge implements BlockEntityRenderer<com.nrdhvnstd.halgventmod.smithing.ForgeBlockEntity> {
        private final ItemRenderer items;

        OnForge(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(com.nrdhvnstd.halgventmod.smithing.ForgeBlockEntity forge, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int packedLight, int packedOverlay) {
            ItemStack crucible = forge.crucible();
            if (crucible.isEmpty()) {
                return;
            }
            pose.pushPose();
            pose.translate(0.5F, 14 / 16.0F + 0.2F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(-forge.getBlockState().getValue(StationBlock.FACING).toYRot()));
            pose.scale(0.5F, 0.5F, 0.5F);
            int light = forge.lit() ? 0xF000F0 : lightAbove(forge);
            items.renderStatic(crucible, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    forge.getLevel(), 0);
            pose.popPose();
        }
    }

    private static void lying(ItemRenderer items, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
                              Level level, float scale, int seed) {
        pose.pushPose();
        if (stack.getItem() instanceof BlockItem) {
            pose.translate(0.0F, scale * 0.35F, 0.0F);
            pose.scale(scale * 0.7F, scale * 0.7F, scale * 0.7F);
        } else {
            pose.translate(0.0F, scale / 16.0F, 0.0F);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(scale, scale, scale);
        }
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, seed);
        pose.popPose();
    }
}
