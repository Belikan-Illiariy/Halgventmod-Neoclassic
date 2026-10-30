package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;


import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.Blood;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/**
 * Кровь на текстуре оружия (combat/Blood): поверх предмета второй проход теми же гранями, но с текстурой пятен, собранной
 * из рисунков этой модели. Чем больше крови ({@link #STEPS} ступеней), тем дальше она сползает по клинку от острия
 * (правый верх рисунка, так нарисованы клинки) — рваными пятнами и потёками с брызгами впереди, рукоять чистая.
 * Пятна лежат только на непрозрачных пикселях. Работает для моделей предметов из атласа, в руке, на земле и в ячейке
 * (ItemRendererMixin).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class WeaponBloodRender {
    // Меняйте здесь ⚠️
    private static final int STEPS = 8;
    private static final float FROM_LEVEL = 0.02F;    // столько крови — уже видно у острия
    private static final int DETAIL = 2;              // пятна мельче пикселя рисунка
    private static final float REACH = 0.38F;         // докуда от острия доходит кровь (доля длины оружия)
    private static final float FRONT_START = 0.15F;   // первая же ступень пачкает кончик
    private static final float EDGE = 0.18F;          // неровность края
    private static final float SOFT = 0.16F;          // как плавно редеют пятна к краю
    private static final float RUN = 0.2F;            // длина струек впереди края
    private static final float RUN_FROM = 0.62F;      // какая часть полос без струек
    private static final float SPLASH = 0.1F;         // брызги перед краем
    private static final float SPLASH_REACH = 0.16F;
    private static final float LIFT = 1.002F;         // пятна чуть над гранями: Sodium считает вершины иначе, иначе мерцало бы
    private static final int TEXTURE_MAX = 1024;      // на такую модель пятен не делаем

    private static final Map<BakedModel, Stained> CACHE = new WeakHashMap<>();
    private static int nextId;

    /** Модель с гранями под свою текстуру и уже собранные ступени. */
    private static final class Stained {
        final List<BakedQuad> quads = new ArrayList<>();
        final Map<TextureAtlasSprite, Integer> placed = new LinkedHashMap<>(); // рисунок → x в текстуре
        final ResourceLocation[] textures = new ResourceLocation[STEPS];
        int width;
        int height;
    }

    private WeaponBloodRender() {}

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
    }

    private static void clear() {
        for (Stained stained : CACHE.values()) {
            for (ResourceLocation texture : stained.textures) {
                if (texture != null) {
                    Minecraft.getInstance().getTextureManager().release(texture);
                }
            }
        }
        CACHE.clear();
    }

    /** Пятна на предмете: та же раскладка, что у ItemRenderer.render, но раньше него (ItemRendererMixin). */
    public static void renderItem(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers,
                                  int light, int overlay, BakedModel model) {
        if (stack.isEmpty() || Blood.level(stack) < FROM_LEVEL) {
            return;
        }
        pose.pushPose();
        BakedModel placed = ClientHooks.handleCameraTransforms(pose, model, context, leftHand);
        pose.translate(-0.5F, -0.5F, -0.5F);
        if (!placed.isCustomRenderer()) {
            for (BakedModel pass : placed.getRenderPasses(stack, true)) {
                render(pass, stack, light, overlay, pose, buffers);
            }
        }
        pose.popPose();
    }

    private static void render(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose, MultiBufferSource buffers) {
        float blood = Blood.level(stack);
        if (blood < FROM_LEVEL) {
            return;
        }
        Stained stained = CACHE.computeIfAbsent(model, WeaponBloodRender::prepare);
        if (stained.quads.isEmpty()) {
            return;
        }
        int step = Mth.clamp(Mth.ceil(blood * STEPS), 1, STEPS) - 1;
        if (stained.textures[step] == null) {
            stained.textures[step] = build(stained, step);
        }
        // Как сам предмет — непрозрачным проходом: прозрачный шейдеры Iris рисуют иначе, и кровь могла не быть видна
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(stained.textures[step]));
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(LIFT, LIFT, LIFT);
        pose.translate(-0.5F, -0.5F, -0.5F);
        for (BakedQuad quad : stained.quads) {
            consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
        }
        pose.popPose();
    }

    // Рисунки модели — в ряд в своей текстуре, UV граней — на неё
    private static Stained prepare(BakedModel model) {
        Stained stained = new Stained();
        RandomSource random = RandomSource.create();
        List<BakedQuad> source = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            random.setSeed(42);
            source.addAll(model.getQuads(null, direction, random));
        }
        random.setSeed(42);
        source.addAll(model.getQuads(null, null, random));
        for (BakedQuad quad : source) {
            TextureAtlasSprite sprite = quad.getSprite();
            if (!stained.placed.containsKey(sprite)) {
                stained.placed.put(sprite, stained.width);
                stained.width += sprite.contents().width();
                stained.height = Math.max(stained.height, sprite.contents().height());
            }
        }
        if (stained.width == 0 || stained.width > TEXTURE_MAX || stained.height > TEXTURE_MAX) {
            return stained;
        }
        for (BakedQuad quad : source) {
            stained.quads.add(remap(quad, stained));
        }
        return stained;
    }

    private static BakedQuad remap(BakedQuad quad, Stained stained) {
        TextureAtlasSprite sprite = quad.getSprite();
        int[] vertices = quad.getVertices().clone();
        float x0 = stained.placed.get(sprite);
        float w = sprite.contents().width();
        float h = sprite.contents().height();
        for (int i = 0; i < 4; i++) {
            int at = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            float u = (Float.intBitsToFloat(vertices[at]) - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
            float v = (Float.intBitsToFloat(vertices[at + 1]) - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
            vertices[at] = Float.floatToRawIntBits((x0 + u * w) / stained.width);
            vertices[at + 1] = Float.floatToRawIntBits(v * h / stained.height);
        }
        return new BakedQuad(vertices, -1, quad.getDirection(), sprite, quad.isShade());
    }

    private static ResourceLocation build(Stained stained, int step) {
        NativeImage image = new NativeImage(stained.width * DETAIL, stained.height * DETAIL, true);
        float progress = (step + 1) / (float) STEPS;
        for (Map.Entry<TextureAtlasSprite, Integer> entry : stained.placed.entrySet()) {
            SpriteContents contents = entry.getKey().contents();
            NativeImage original = contents.getOriginalImage();
            int width = contents.width();
            int height = contents.height();
            // Где на рисунке острие и конец: оружие не занимает его целиком
            float tip = Float.MAX_VALUE;
            float end = -Float.MAX_VALUE;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((original.getPixelRGBA(x, y) >>> 24) >= 128) {
                        float along = along((x + 0.5F) / width, (y + 0.5F) / height);
                        tip = Math.min(tip, along);
                        end = Math.max(end, along);
                    }
                }
            }
            float length = Math.max(0.01F, end - tip);
            for (int y = 0; y < height * DETAIL; y++) {
                for (int x = 0; x < width * DETAIL; x++) {
                    int pixel = original.getPixelRGBA(x / DETAIL, y / DETAIL);
                    if ((pixel >>> 24) < 128) {
                        continue;
                    }
                    float u = (x + 0.5F) / (width * DETAIL);
                    float v = (y + 0.5F) / (height * DETAIL);
                    int color = stain(pixel, x, y, width, u, v, (along(u, v) - tip) / length, progress);
                    if (color != 0) {
                        image.setPixelRGBA(entry.getValue() * DETAIL + x, y, color);
                    }
                }
            }
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "weapon_blood/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    // Расстояние от правого верхнего угла рисунка вдоль диагонали
    private static float along(float u, float v) {
        return 1 - (u + 1 - v) / 2;
    }

    /**
     * Цвет пятна в пикселе (ABGR) или 0 — чисто. Кровь не заливка: неровный край и потёки вдоль клинка, рваные пятна с просветами
     * стали, больше у одной кромки, густое темнее и краснее, сквозь неё видна светотень клинка. Узор один на все ступени.
     */
    private static int stain(int pixel, int x, int y, int width, float u, float v, float fromTip, float progress) {
        int r0 = pixel & 0xFF;
        int g0 = pixel >> 8 & 0xFF;
        int b0 = pixel >> 16 & 0xFF;
        float across = u + v - 1;
        float light = (r0 * 0.3F + g0 * 0.59F + b0 * 0.11F) / 255;
        // Рукоять и гарда цветные: на них кровь едва ложится
        float steel = 1 - Mth.clamp((Math.max(r0, Math.max(g0, b0)) - Math.min(r0, Math.min(g0, b0))) / 70.0F, 0, 1);
        float streak = smooth(fromTip * 4, across * 26);
        float clot = smooth(u * 9 + 40, v * 9);
        float bandHash = hash(Mth.floor((across + 2) * width * 0.9F), 7);
        float run = bandHash > RUN_FROM ? (bandHash - RUN_FROM) / (1 - RUN_FROM) * RUN * progress : 0; // отдельные струйки вперёд
        float front = progress * REACH + FRONT_START;
        float cover = Mth.clamp((front + run - (fromTip + (streak - 0.5F) * EDGE)) / SOFT, 0, 1);
        float blob = smooth(fromTip * 8 + 3, across * 16) * 0.6F + smooth(u * 22, v * 22 + 9) * 0.4F;
        float limit = cover * 0.62F + Mth.clamp(0.5F + across * 6, 0, 1) * cover * 0.22F; // у нижней кромки гуще
        float edge = Mth.clamp((limit - blob) / 0.07F, 0, 1);
        float alpha = edge * edge * (3 - 2 * edge) * 0.93F;
        if (cover <= 0 && fromTip < front + SPLASH_REACH && hash(x / 2, y / 2 + 500) < SPLASH * progress) {
            alpha = 0.75F; // брызги перед краем
        }
        alpha *= steel * 0.9F + 0.1F;
        if (alpha < 0.08F) {
            return 0;
        }
        float thick = Mth.clamp((limit - blob) * 3 + clot * 0.55F, 0, 1);
        float shade = 0.72F + 0.5F * light;
        float wet = hash(x, y) > 0.965F && thick > 0.5F ? 50 : 0; // влажный блик
        int r = (int) Math.min(255, Mth.lerp(alpha, r0, Mth.lerp(thick, 190, 92) * shade + wet));
        int g = (int) Math.min(255, Mth.lerp(alpha, g0, Mth.lerp(thick, 22, 6) * shade));
        int b = (int) Math.min(255, Mth.lerp(alpha, b0, Mth.lerp(thick, 18, 8) * shade));
        return 0xFF << 24 | b << 16 | g << 8 | r;
    }

    // Плавный шум: один на все ступени, поэтому кровь только прибывает
    private static float smooth(float x, float y) {
        int cx = Mth.floor(x);
        int cy = Mth.floor(y);
        float fx = x - cx;
        float fy = y - cy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        return Mth.lerp(fy, Mth.lerp(fx, hash(cx + 91, cy), hash(cx + 92, cy)), Mth.lerp(fx, hash(cx + 91, cy + 1), hash(cx + 92, cy + 1)));
    }

    private static float hash(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ n >>> 13) * 1274126177;
        return ((n ^ n >>> 16) & 0xFFFF) / 65535.0F;
    }
}
