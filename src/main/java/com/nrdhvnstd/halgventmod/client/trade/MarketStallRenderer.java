package com.nrdhvnstd.halgventmod.client.trade;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.trade.MarketStallBlockEntity;
import com.nrdhvnstd.halgventmod.trade.StallKind;
import com.nrdhvnstd.halgventmod.trade.TradeBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Товар на прилавках виден в мире. Оружейная стойка — 4 вещи на крюках одна над другой, лёжа поперёк, в размере
 * «как в руке». Витрина — 8 вещей на трёх наклонных полках (2 сверху, по 3 ниже). Манекен — стойка для брони без
 * подставки, на ней броня из 12 ячеек тем же слоем, что у игроков (ArmorLayer). Бочонок с краном — кружки торговца
 * на бочке. Прилавок товаров ничего не показывает.
 * Числа раскладки сверены с моделями блоков (models/block/*_stall.json) ⚠️ подбирались без игры.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public class MarketStallRenderer implements BlockEntityRenderer<MarketStallBlockEntity> {
    // Оружейная стойка: высота крюков (пикселей), глубина от фасада и наклон вещи (спрайт меча — по диагонали)
    private static final float[] RACK_HEIGHTS = {13.5F, 10F, 6.5F, 3F};
    private static final float RACK_DEPTH = 12.5F;  // между загибом крюка (z 10–11) и стеной
    private static final float RACK_TILT = -45F;
    private static final float RACK_SCALE = 1.0F;
    // Витрина-горка: ступени сверху вниз — высота настила и середина по глубине (пиксели), места по ширине, размер вещи
    private static final float[] STEP_HEIGHTS = {18F, 14F, 10F};
    private static final float[] STEP_DEPTHS = {13.5F, 8.75F, 3.75F};
    private static final float[][] STEP_SPOTS = {{3.5F, 12.5F}, {-0.5F, 8F, 16.5F}, {-0.5F, 8F, 16.5F}};
    private static final float FOOD_SCALE = 0.5F;
    private static final float FOOD_LIFT = 0.5F;
    // Прилавок товаров: стол в два блока, три ступени по 4 места (сверху вниз), первое место и шаг по ширине
    private static final float[] GOODS_HEIGHTS = {18F, 15.5F, 13F};
    private static final float[] GOODS_DEPTHS = {13.5F, 8.5F, 3.75F};
    private static final float GOODS_X0 = -3.25F;
    private static final float GOODS_STEP = 7.5F;
    private static final int GOODS_PER_ROW = 4;
    private static final float GOODS_SCALE = 0.45F;
    // Бочонок: кружки на нём (верх бочки — 15 пикселей)
    private static final float MUG_X0 = 3.5F;
    private static final float MUG_Z0 = 5.0F;
    private static final float MUG_STEP = 3.0F;
    private static final float MUG_Y = 16.5F;
    private static final float MUG_SCALE = 0.35F;

    private final ItemRenderer items;
    // Манекены по прилавкам: нарисованная стойка для брони и версия прилавка, с которой взята броня
    private final Map<MarketStallBlockEntity, ArmorStand> stands = new WeakHashMap<>();
    private final Map<MarketStallBlockEntity, Integer> versions = new WeakHashMap<>();

    public MarketStallRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(MarketStallBlockEntity stall, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        StallKind kind = stall.kind();
        Direction facing = stall.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (kind == StallKind.ARMOR) {
            mannequin(stall, facing, partialTick, pose, buffers, light);
            return;
        }
        pose.pushPose();
        // Модели смотрят на север; повернуть так же, как блок
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        switch (kind) {
            case WEAPONS -> rack(stall, pose, buffers, light, overlay);
            case FOOD -> showcase(stall, pose, buffers, light, overlay);
            case GOODS -> table(stall, pose, buffers, light, overlay);
            case TAP -> mugs(stall, pose, buffers, light, overlay);
            default -> { }
        }
        pose.popPose();
    }

    // Оружие лёжа поперёк крюков, от верхнего к нижнему — ячейки 0…3
    private void rack(MarketStallBlockEntity stall, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (int i = 0; i < RACK_HEIGHTS.length && i < stall.stock().getContainerSize(); i++) {
            ItemStack stack = stall.stock().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5, RACK_HEIGHTS[i] / 16.0, RACK_DEPTH / 16.0);
            pose.mulPose(Axis.ZP.rotationDegrees(RACK_TILT));
            pose.scale(RACK_SCALE, RACK_SCALE, RACK_SCALE);
            items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, stall.getLevel(), i);
            pose.popPose();
        }
    }

    // Еда лёжа на ступенях горки: верхняя (задняя) — 2 места, средняя и нижняя — по 3
    private void showcase(MarketStallBlockEntity stall, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int slot = 0;
        for (int step = 0; step < STEP_HEIGHTS.length; step++) {
            for (float x : STEP_SPOTS[step]) {
                ItemStack stack = slot < stall.stock().getContainerSize() ? stall.stock().getItem(slot) : ItemStack.EMPTY;
                slot++;
                if (stack.isEmpty()) {
                    continue;
                }
                pose.pushPose();
                pose.translate(x / 16.0, (STEP_HEIGHTS[step] + FOOD_LIFT) / 16.0, STEP_DEPTHS[step] / 16.0);
                pose.mulPose(Axis.XP.rotationDegrees(90)); // лёжа на полке
                pose.scale(FOOD_SCALE, FOOD_SCALE, FOOD_SCALE);
                items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, stall.getLevel(), slot);
                pose.popPose();
            }
        }
    }

    // Товар лёжа на ступенях стола: задняя ступень — ячейки 0…3, средняя — 4…7, передняя — 8…11
    private void table(MarketStallBlockEntity stall, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (int slot = 0; slot < stall.stock().getContainerSize() && slot < GOODS_HEIGHTS.length * GOODS_PER_ROW; slot++) {
            ItemStack stack = stall.stock().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int row = slot / GOODS_PER_ROW;
            float x = GOODS_X0 + (slot % GOODS_PER_ROW) * GOODS_STEP;
            pose.pushPose();
            pose.translate(x / 16.0, (GOODS_HEIGHTS[row] + FOOD_LIFT) / 16.0, GOODS_DEPTHS[row] / 16.0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(GOODS_SCALE, GOODS_SCALE, GOODS_SCALE);
            items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, stall.getLevel(), slot);
            pose.popPose();
        }
    }

    // Кружки торговца стоят на бочонке: два ряда по четыре, донышком вниз
    private void mugs(MarketStallBlockEntity stall, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (int i = 0; i < stall.stock().getContainerSize(); i++) {
            ItemStack stack = stall.stock().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            pose.translate((MUG_X0 + (i % 4) * MUG_STEP) / 16.0, MUG_Y / 16.0, (MUG_Z0 + (i / 4) * MUG_STEP) / 16.0);
            pose.scale(MUG_SCALE, MUG_SCALE, MUG_SCALE);
            items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, stall.getLevel(), i);
            pose.popPose();
        }
    }

    // Манекен: стойка для брони без подставки, броня — из ячеек прилавка (пересобирается, когда товар меняется)
    private void mannequin(MarketStallBlockEntity stall, Direction facing, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
        if (stall.getLevel() == null) {
            return;
        }
        ArmorStand stand = stands.computeIfAbsent(stall, key -> {
            ArmorStand created = new ArmorStand(EntityType.ARMOR_STAND, stall.getLevel());
            created.setNoBasePlate(true);
            created.setShowArms(true);
            return created;
        });
        Integer seen = versions.get(stall);
        if (seen == null || seen != stall.version()) {
            versions.put(stall, stall.version());
            ArmorInventory armor = stand.getData(ModAttachments.ARMOR);
            for (ArmorSlot slot : ArmorSlot.values()) {
                ItemStack stack = slot.ordinal() < stall.stock().getContainerSize()
                        ? stall.stock().getItem(slot.ordinal()) : ItemStack.EMPTY;
                armor.set(slot, stack.copy());
            }
        }
        float yaw = facing.toYRot(); // лицом туда же, куда прилавок
        stand.setYRot(yaw);
        stand.yRotO = yaw;
        stand.yBodyRot = yaw;
        stand.yBodyRotO = yaw;
        stand.setYHeadRot(yaw);
        stand.yHeadRotO = yaw;
        Minecraft.getInstance().getEntityRenderDispatcher().render(stand, 0.5, 1.0 / 16.0, 0.5, yaw, partialTick,
                pose, buffers, light);
    }

    // Манекен выше блока — рисовать и когда сам блок за краем экрана
    @Override
    public AABB getRenderBoundingBox(MarketStallBlockEntity stall) {
        return new AABB(stall.getBlockPos()).expandTowards(0, 1.2, 0).inflate(0.6);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TradeBlocks.MARKET_STALL_ENTITY.get(), MarketStallRenderer::new);
    }
}
