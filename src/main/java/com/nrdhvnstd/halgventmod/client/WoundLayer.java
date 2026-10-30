package com.nrdhvnstd.halgventmod.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.Wounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Раны на коже (combat/Wounds): слой поверх кожи модели игрока и НПС, под бронёй (броня рисуется после — под латами
 * ран не видно). Текстура склеивается по развёртке кожи 64×64: каждая рана — на лицевой стороне своей части тела
 * (место — по зерну раны): порез — тёмная полоса с кровью и подтёками, колотая — тёмная точка, синяк — лиловое
 * пятно; перевязанная — светлая повязка с пятном. К концу заживления рана бледнеет и буреет (корка).
 */
public class WoundLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final int SIZE = 64;
    private static final int FADE_STEPS = 4;              // ступени заживления для кэша
    private static final int CACHE_MAX = 96;
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static int nextId;

    public WoundLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        List<Wounds.Wound> wounds = Wounds.of(entity);
        if (wounds.isEmpty() || entity.isInvisible()) {
            return;
        }
        ResourceLocation texture = texture(wounds, entity.level().getGameTime());
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(texture)), light,
                OverlayTexture.NO_OVERLAY);
    }

    // ---------------------------------------------------------------- склейка

    private static ResourceLocation texture(List<Wounds.Wound> wounds, long now) {
        StringBuilder key = new StringBuilder();
        for (Wounds.Wound wound : wounds) {
            int fade = Math.min(FADE_STEPS - 1, (int) (wound.healed(now) * FADE_STEPS));
            key.append(wound.zone().ordinal()).append(':').append(wound.kind().ordinal()).append(':').append(wound.size())
                    .append(':').append(wound.seed()).append(':').append(fade).append(wound.bandaged() ? 'b' : 'o').append(';');
        }
        return CACHE.computeIfAbsent(key.toString(), k -> {
            if (CACHE.size() >= CACHE_MAX) {
                CACHE.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
                CACHE.clear();
            }
            NativeImage image = new NativeImage(SIZE, SIZE, true);
            for (Wounds.Wound wound : wounds) {
                draw(image, wound, Math.min(FADE_STEPS - 1, (int) (wound.healed(now) * FADE_STEPS)) / (float) FADE_STEPS);
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "wounds/" + nextId++);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return id;
        });
    }

    /** Область развёртки (x0, y0, x1, y1) лицевой стороны части тела; right — правая рука или нога. */
    private static int[] area(Wounds.Wound wound, Random random) {
        boolean right = random.nextBoolean();
        boolean back = random.nextInt(3) == 0;
        return switch (wound.zone()) {
            case CROWN -> back ? new int[] {24, 8, 32, 14} : new int[] {8, 0, 16, 8};
            case FACE -> new int[] {9, 10, 15, 15};
            case NECK -> new int[] {21, 20, 27, 22};
            case CHEST -> back ? new int[] {33, 21, 39, 26} : new int[] {21, 21, 27, 26};
            case ABDOMEN -> back ? new int[] {33, 26, 39, 31} : new int[] {21, 26, 27, 31};
            case ARM -> right ? new int[] {44, 20, 48, 28} : new int[] {36, 52, 40, 60};
            case HAND -> right ? new int[] {44, 28, 48, 32} : new int[] {36, 60, 40, 64};
            case THIGH -> right ? new int[] {4, 20, 8, 25} : new int[] {20, 52, 24, 57};
            case KNEE -> right ? new int[] {4, 25, 8, 27} : new int[] {20, 57, 24, 59};
            case SHIN -> right ? new int[] {4, 27, 8, 30} : new int[] {20, 59, 24, 62};
            case FOOT -> right ? new int[] {4, 30, 8, 32} : new int[] {20, 62, 24, 64};
        };
    }

    private static void draw(NativeImage image, Wounds.Wound wound, float healed) {
        Random random = new Random(wound.seed());
        int[] area = area(wound, random);
        int w = Math.max(1, area[2] - area[0]);
        int h = Math.max(1, area[3] - area[1]);
        int cx = area[0] + random.nextInt(w);
        int cy = area[1] + random.nextInt(h);
        if (wound.bandaged()) {
            // Повязка поперёк части тела: светлая полоса, на ней — бурое пятно
            int y0 = Math.max(area[1], Math.min(area[3] - 2, cy - 1));
            for (int x = area[0]; x < area[2]; x++) {
                for (int y = y0; y < Math.min(area[3], y0 + 2); y++) {
                    put(image, x, y, 0xE8DCC8, 1.0F);
                }
            }
            put(image, cx, y0, 0x7A2010, 0.8F * (1 - healed));
            return;
        }
        float alpha = 1.0F - healed * 0.6F;       // бледнеет к концу
        int core = blend(0x4A0606, 0x5A3A24, healed); // свежая — тёмно-красная, к заживлению — бурая корка
        int edge = blend(0x9A1414, 0x7A5038, healed);
        switch (wound.kind()) {
            case CUT -> {
                int length = 1 + wound.size() + random.nextInt(2);
                int dx = random.nextBoolean() ? 1 : -1;
                for (int i = 0; i < length; i++) {
                    int x = cx + i * dx;
                    int y = cy + i / 2;
                    put(image, x, y, core, alpha);
                    put(image, x, y - 1, edge, alpha * 0.7F);
                    if (healed < 0.5F && random.nextInt(3) == 0) {
                        put(image, x, y + 1, edge, alpha * 0.8F); // подтёк
                        put(image, x, y + 2, edge, alpha * 0.5F);
                    }
                }
            }
            case STAB -> {
                put(image, cx, cy, core, alpha);
                put(image, cx + 1, cy, edge, alpha * 0.8F);
                put(image, cx, cy + 1, edge, alpha * 0.8F);
                if (wound.size() > 1) {
                    put(image, cx + 1, cy + 1, core, alpha);
                    put(image, cx, cy + 2, edge, alpha * 0.6F);
                }
            }
            case BRUISE -> {
                int r = wound.size();
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (dx * dx + dy * dy <= r * r) {
                            put(image, cx + dx, cy + dy, blend(0x5A2A6A, 0x6A6A30, healed), alpha * 0.55F);
                        }
                    }
                }
            }
        }
    }

    private static int blend(int from, int to, float t) {
        int r = (int) ((from >> 16 & 0xFF) * (1 - t) + (to >> 16 & 0xFF) * t);
        int g = (int) ((from >> 8 & 0xFF) * (1 - t) + (to >> 8 & 0xFF) * t);
        int b = (int) ((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return r << 16 | g << 8 | b;
    }

    // RGB с прозрачностью в ABGR-пиксель; поверх уже нарисованного — непрозрачнее из двух
    private static void put(NativeImage image, int x, int y, int rgb, float alpha) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE || alpha <= 0) {
            return;
        }
        int a = Math.min(255, Math.round(alpha * 255));
        int old = image.getPixelRGBA(x, y) >>> 24;
        if (old > a) {
            return;
        }
        image.setPixelRGBA(x, y, a << 24 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | (rgb >> 16 & 0xFF));
    }
}
