package com.nrdhvnstd.halgventmod.client.map;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Цвет блока на карте, как у Xaero: средний цвет верхней грани его текстуры
 * (если верхней грани нет — любой грани, если и её нет — значка частиц),
 * умноженный на оттенок биома, если грань окрашивается (трава, листва, вода).
 * Средние цвета запоминаются; сбрасываются при перезагрузке ресурсов.
 * Средний цвет (base) читается и из фонового потока карты (MapWorker) — поэтому кэш потокобезопасный.
 */
public final class MapColors {
    /** Средний цвет текстуры (ARGB, непрозрачный) и номер оттенка грани (-1 — без оттенка). */
    private record Entry(int color, int tintIndex) {}

    private static final Map<BlockState, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Entry FAILED = new Entry(0, -1);

    private MapColors() {}

    public static void clear() {
        CACHE.clear();
    }

    /** Цвет блока в этом месте мира (ARGB). */
    public static int color(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Entry entry = entry(state);
        if (entry.tintIndex() < 0) {
            return entry.color();
        }
        int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, entry.tintIndex());
        return tint == -1 ? entry.color() : multiply(entry.color(), tint);
    }

    /** Окрашивается ли блок оттенком биома (трава, листва, вода). */
    public static boolean tinted(BlockState state) {
        return entry(state).tintIndex() >= 0;
    }

    /** Средний цвет текстуры блока без оттенка (ARGB); 0 — не удалось посчитать. */
    public static int base(BlockState state) {
        return entry(state).color();
    }

    // Ресурсы могут перезагружаться прямо во время расчёта в фоновом потоке — тогда не запоминаем
    private static Entry entry(BlockState state) {
        Entry entry = CACHE.get(state);
        if (entry != null) {
            return entry;
        }
        try {
            entry = compute(state);
        } catch (RuntimeException e) {
            return FAILED;
        }
        CACHE.put(state, entry);
        return entry;
    }

    private static Entry compute(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(state);
        BakedQuad quad = firstQuad(model, state, Direction.UP);
        if (quad == null) {
            quad = firstQuad(model, state, null);
        }
        if (quad != null) {
            return new Entry(average(quad.getSprite()), quad.isTinted() ? quad.getTintIndex() : -1);
        }
        // Жидкости и блоки без модели: значок частиц. Вода окрашивается оттенком 0
        boolean tinted = !state.getFluidState().isEmpty();
        return new Entry(average(model.getParticleIcon()), tinted ? 0 : -1);
    }

    private static BakedQuad firstQuad(BakedModel model, BlockState state, Direction side) {
        List<BakedQuad> quads = model.getQuads(state, side, RandomSource.create(42L));
        return quads.isEmpty() ? null : quads.get(0);
    }

    // Среднее по непрозрачным пикселям первого кадра
    private static int average(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        NativeImage image = contents.getOriginalImage();
        long r = 0;
        long g = 0;
        long b = 0;
        long count = 0;
        for (int y = 0; y < contents.height(); y++) {
            for (int x = 0; x < contents.width(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                if ((abgr >>> 24) < 16) {
                    continue;
                }
                r += abgr & 0xFF;
                g += (abgr >> 8) & 0xFF;
                b += (abgr >> 16) & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        return FastColor.ARGB32.color(255, (int) (r / count), (int) (g / count), (int) (b / count));
    }

    private static int multiply(int color, int tint) {
        return FastColor.ARGB32.color(255,
                FastColor.ARGB32.red(color) * FastColor.ARGB32.red(tint) / 255,
                FastColor.ARGB32.green(color) * FastColor.ARGB32.green(tint) / 255,
                FastColor.ARGB32.blue(color) * FastColor.ARGB32.blue(tint) / 255);
    }
}
