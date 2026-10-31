package com.nrdhvnstd.halgventmod.client.leather;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.FleshingBeamBlockEntity;
import com.nrdhvnstd.halgventmod.leather.LeatherPatterns;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlock;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.leather.Solution;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.leather.TanningVatBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Что видно на станциях кожевника: шкура на колоде, раствор и шкуры в чане, кожа и выкройка на столе. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class LeatherRenderers {
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");

    private LeatherRenderers() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.FLESHING_BEAM.get(), BeamRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TANNING_VAT.get(), VatRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LEATHER_TABLE.get(), TableRenderer::new);
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

    // ---------------------------------------------------------------- колода

    static final class BeamRenderer implements BlockEntityRenderer<FleshingBeamBlockEntity> {
        /** Верх пня (модель Conquest large_stump) — ровно 1 блок, шкуру кладём с запасом над корой. */
        private static final float TOP = 1.02F;
        private static final float HIDE_SCALE = 0.85F;
        private final ItemRenderer items;

        BeamRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(FleshingBeamBlockEntity beam, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            if (beam.hide().isEmpty()) {
                return;
            }
            int light = lightAbove(beam);
            pose.pushPose();
            faceFront(pose, beam);
            pose.translate(0.0F, TOP, 0.0F);
            lying(items, beam.hide(), pose, buffers, light, beam.getLevel(), HIDE_SCALE, 1);
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- чан

    static final class VatRenderer implements BlockEntityRenderer<TanningVatBlockEntity> {
        private static final float SURFACE = 9.0F / 16.0F;
        /** Дно лохани Conquest изнутри — 1.5 пикселя. */
        private static final float BOTTOM = 1.5F / 16.0F;
        /** Шкуры в растворе — чуть под поверхностью, раствор поверх них. */
        private static final float SOAKED = SURFACE - 0.06F;
        /** С какой скоростью шкуры оседают на дно, когда раствор ушёл (блоков в секунду). */
        private static final float SINK_SPEED = 0.6F;
        /** Лохань Conquest круглая: внешний радиус 0.625 блока, внутренний — с запасом на стенку. */
        private static final float RADIUS = 0.53F;
        private static final int SEGMENTS = 32;
        private final ItemRenderer items;
        /** Где сейчас видны шкуры в каждом чане. */
        private final Map<TanningVatBlockEntity, Depth> depths = new WeakHashMap<>();

        private static final class Depth {
            float height;
            long time;

            Depth(float height, long time) {
                this.height = height;
                this.time = time;
            }
        }

        VatRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(TanningVatBlockEntity vat, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            List<ItemStack> inside = vat.items();
            int light = lightAbove(vat);
            float height = height(vat);
            float[][] spots = {{-0.17F, -0.17F}, {0.17F, 0.17F}, {0.17F, -0.17F}, {-0.17F, 0.17F}};
            for (int i = 0; i < inside.size() && i < spots.length; i++) {
                pose.pushPose();
                // Чуть разная высота — чтобы края соседних шкур на дне не мерцали
                pose.translate(0.5F + spots[i][0], height + i * 0.004F, 0.5F + spots[i][1]);
                pose.mulPose(Axis.YP.rotationDegrees(i * 70));
                lying(items, inside.get(i), pose, buffers, light, vat.getLevel(), 0.42F, i);
                pose.popPose();
            }
            if (vat.solution() != Solution.NONE) {
                liquid(vat.solution(), pose, buffers, light);
            }
        }

        /** Без раствора шкуры лежат на дне; ушёл раствор — плавно оседают, а не висят в воздухе. */
        private float height(TanningVatBlockEntity vat) {
            float target = vat.solution() == Solution.NONE ? BOTTOM : SOAKED;
            long now = Util.getMillis();
            Depth depth = depths.get(vat);
            if (depth == null) {
                // Чан только что попал в поле зрения — сразу на своём месте
                depths.put(vat, new Depth(target, now));
                return target;
            }
            float step = SINK_SPEED * Math.min(now - depth.time, 250L) / 1000.0F;
            depth.height = depth.height > target ? Math.max(target, depth.height - step)
                    : Math.min(target, depth.height + step);
            depth.time = now;
            return depth.height;
        }

        /** Круглая поверхность раствора: анимированная текстура воды, окрашенная в цвет раствора. */
        private static void liquid(Solution solution, PoseStack pose, MultiBufferSource buffers, int light) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
            PoseStack.Pose last = pose.last();
            int color = solution.color();
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;
            int a = solution == Solution.WATER ? 190 : 225;
            // Веер из треугольников: каждый — вырожденный четырёхугольник (центр, край, край, край)
            for (int i = 0; i < SEGMENTS; i++) {
                float a0 = Mth.TWO_PI * i / SEGMENTS;
                float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
                float x0 = 0.5F + RADIUS * Mth.cos(a0), z0 = 0.5F + RADIUS * Mth.sin(a0);
                float x1 = 0.5F + RADIUS * Mth.cos(a1), z1 = 0.5F + RADIUS * Mth.sin(a1);
                vertex(buffer, last, sprite, 0.5F, 0.5F, r, g, b, a, light);
                vertex(buffer, last, sprite, x1, z1, r, g, b, a, light);
                vertex(buffer, last, sprite, x0, z0, r, g, b, a, light);
                vertex(buffer, last, sprite, x0, z0, r, g, b, a, light);
            }
        }

        private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, TextureAtlasSprite sprite, float x,
                                   float z, int r, int g, int b, int a, int light) {
            buffer.addVertex(pose, x, SURFACE, z)
                    .setColor(r, g, b, a)
                    .setUv(sprite.getU(x), sprite.getV(z))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0, 1, 0);
        }
    }

    // ---------------------------------------------------------------- стол

    static final class TableRenderer implements BlockEntityRenderer<LeatherTableBlockEntity> {
        private static final float TOP = 1.01F;
        private final ItemRenderer items;

        TableRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(LeatherTableBlockEntity table, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int packedLight, int packedOverlay) {
            Level level = table.getLevel();
            int light = lightAbove(table);
            Direction right = LeatherTableBlock.toRight(table.getBlockState());
            float yaw = -table.getBlockState().getValue(StationBlock.FACING).toYRot();

            // Кожа и фурнитура — на правой половине стола
            List<ItemStack> laid = table.laid();
            for (int i = 0; i < laid.size(); i++) {
                pose.pushPose();
                pose.translate(0.5F + right.getStepX(), 0.0F, 0.5F + right.getStepZ());
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                float x = ((i % 3) - 1) * 0.28F;
                float z = ((i / 3) % 3 - 1) * 0.28F;
                pose.translate(x, TOP + i * 0.004F, z);
                pose.mulPose(Axis.YP.rotationDegrees(i * 37));
                lying(items, laid.get(i), pose, buffers, light, level, 0.4F, i);
                pose.popPose();
            }

            // Раскроенная заготовка — на левой половине (до раскроя там пусто: выкройка видна в окне)
            if (table.pattern() >= 0 && table.isCut() && level != null) {
                ItemStack result = table.current().result().copyWithCount(1);
                pose.pushPose();
                pose.translate(0.5F, TOP, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                lying(items, result, pose, buffers, light, level, 0.7F, 98);
                pose.popPose();
            }
        }

        @Override
        public AABB getRenderBoundingBox(LeatherTableBlockEntity table) {
            // Стол в два блока: правую половину тоже рисует эта блок-сущность
            return new AABB(table.getBlockPos()).inflate(1.0);
        }
    }
}
