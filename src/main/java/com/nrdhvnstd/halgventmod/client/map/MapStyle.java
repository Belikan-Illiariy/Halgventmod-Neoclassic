package com.nrdhvnstd.halgventmod.client.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import org.slf4j.Logger;

/**
 * Вид карты — один на всех, «пергамент»: регион хранит «сырой» цвет блока и высоту (MapRegion), а картинку
 * на экране собирает отсюда — apply(). Тень от рельефа тоже здесь. Выбора основы и ползунков больше нет;
 * старый файл настроек .minecraft/halgventmod/map_style.json удаляется.
 */
public final class MapStyle {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OLD_FILE = "map_style.json";

    /** Альфа «сырого» пикселя: обычный блок и блок, окрашенный биомом (трава, листва, вода). 0 — неизвестно. */
    public static final int ALPHA_PLAIN = 0xFF;
    public static final int ALPHA_TINTED = 0xFE;

    // Пергамент: насыщенность, контраст, яркость, сепия, оттенок (°), цвет биомов, приглушение зелени,
    // склоны, светотень высот, зерно — в процентах
    private static final float SATURATION = 0.45F;
    private static final float CONTRAST = 0.95F;
    private static final float BRIGHTNESS = 1.05F;
    private static final float SEPIA_AMOUNT = 0.75F;
    private static final int HUE_DEGREES = 0;
    private static final float BIOME_COLOR = 0.60F;
    private static final float GREEN_MUTE = 0.55F;
    private static final float SLOPES = 1.40F;
    private static final float HEIGHTS = 0.30F;
    private static final float GRAIN = 0.35F;

    private static float saturation;
    private static float contrast;
    private static float brightness;
    private static float sepia;
    private static float biomeColor;
    private static float greenMute;
    private static float slopes;
    private static float heights;
    private static float grain;
    private static final float[] HUE = new float[9];
    private static boolean oldFileChecked;

    static {
        prepare();
    }

    private MapStyle() {}

    /** Вид не меняется: тайлы собираются один раз. */
    public static int version() {
        return 1;
    }

    /** Удалить файл настроек стиля прежних версий (один раз за запуск). */
    static void deleteOldFile() {
        if (oldFileChecked) {
            return;
        }
        oldFileChecked = true;
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve(HalgventMod.MODID).resolve(OLD_FILE);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.warn("Не удалось удалить {}", file, e);
        }
    }

    // ===== Цвет пикселя =====

    /**
     * Цвет на экране (ARGB) по «сырому» пикселю региона: raw — цвет блока с видом в альфе,
     * height — высота, north/west — высоты соседей с севера и запада (NO_HEIGHT — неизвестно),
     * x/z — координаты блока (для зерна бумаги).
     */
    public static int apply(int raw, int height, int north, int west, int x, int z) {
        return applySlope(raw, height, slope(height, north, west), x, z);
    }

    /** Склон пикселя: перепад с северным и западным соседом (NO_HEIGHT — неизвестно), ограниченный ±6. */
    public static float slope(int height, int north, int west) {
        if (height == MapRegion.NO_HEIGHT) {
            return 0;
        }
        int slope = (north == MapRegion.NO_HEIGHT ? 0 : height - north)
                + (west == MapRegion.NO_HEIGHT ? 0 : height - west);
        return Mth.clamp(slope, -6, 6);
    }

    /**
     * То же, но склон уже посчитан: у уровней отдаления это среднее склонов всех блоков под пикселем
     * (MapPyramid), поэтому рельеф виден и издалека, а не сглаживается вместе с высотами.
     */
    public static int applySlope(int raw, int height, float slope, int x, int z) {
        int alpha = raw >>> 24;
        if (alpha == 0) {
            return 0;
        }
        float r = ((raw >> 16) & 0xFF) / 255.0F;
        float g = ((raw >> 8) & 0xFF) / 255.0F;
        float b = (raw & 0xFF) / 255.0F;

        // Окраска биомов: от серого к своему цвету; приглушение зелени — к оливковому
        if (alpha == ALPHA_TINTED) {
            float lum = luminance(r, g, b);
            r = lum + (r - lum) * biomeColor;
            g = lum + (g - lum) * biomeColor;
            b = lum + (b - lum) * biomeColor;
            float top = Math.max(r, b);
            if (g > top) {
                float excess = (g - top) * greenMute;
                g -= excess * 0.75F;
                r += excess * 0.35F;
            }
        }

        // Свет: склоны (перепад с северным и западным соседом), высота и зерно бумаги
        float light = 1.0F;
        if (height != MapRegion.NO_HEIGHT) {
            light += slope * 0.035F * slopes;
            light += Mth.clamp((height - 64) / 128.0F, -0.5F, 1.0F) * 0.4F * heights;
        }
        if (grain > 0) {
            light += (noise(x, z) - 0.5F) * 0.16F * grain;
        }
        r *= light;
        g *= light;
        b *= light;

        // Насыщенность
        float lum = luminance(r, g, b);
        r = lum + (r - lum) * saturation;
        g = lum + (g - lum) * saturation;
        b = lum + (b - lum) * saturation;

        // Оттенок: поворот вокруг серой оси
        float hr = HUE[0] * r + HUE[1] * g + HUE[2] * b;
        float hg = HUE[3] * r + HUE[4] * g + HUE[5] * b;
        float hb = HUE[6] * r + HUE[7] * g + HUE[8] * b;

        // Сепия
        float sr = 0.393F * hr + 0.769F * hg + 0.189F * hb;
        float sg = 0.349F * hr + 0.686F * hg + 0.168F * hb;
        float sb = 0.272F * hr + 0.534F * hg + 0.131F * hb;
        r = hr + (sr - hr) * sepia;
        g = hg + (sg - hg) * sepia;
        b = hb + (sb - hb) * sepia;

        // Контраст и яркость
        r = ((r - 0.5F) * contrast + 0.5F) * brightness;
        g = ((g - 0.5F) * contrast + 0.5F) * brightness;
        b = ((b - 0.5F) * contrast + 0.5F) * brightness;

        return 0xFF000000 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static float luminance(float r, float g, float b) {
        return 0.299F * r + 0.587F * g + 0.114F * b;
    }

    private static int channel(float value) {
        return Mth.clamp((int) (value * 255.0F + 0.5F), 0, 255);
    }

    // Шум 0..1, постоянный для каждого блока
    private static float noise(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0F;
    }

    private static void prepare() {
        saturation = SATURATION;
        contrast = CONTRAST;
        brightness = BRIGHTNESS;
        sepia = SEPIA_AMOUNT;
        biomeColor = BIOME_COLOR;
        greenMute = GREEN_MUTE;
        slopes = SLOPES;
        heights = HEIGHTS;
        grain = GRAIN;

        // Матрица поворота оттенка (как hue-rotate в CSS)
        double angle = Math.toRadians(HUE_DEGREES);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        HUE[0] = 0.213F + cos * 0.787F - sin * 0.213F;
        HUE[1] = 0.715F - cos * 0.715F - sin * 0.715F;
        HUE[2] = 0.072F - cos * 0.072F + sin * 0.928F;
        HUE[3] = 0.213F - cos * 0.213F + sin * 0.143F;
        HUE[4] = 0.715F + cos * 0.285F + sin * 0.140F;
        HUE[5] = 0.072F - cos * 0.072F - sin * 0.283F;
        HUE[6] = 0.213F - cos * 0.213F - sin * 0.787F;
        HUE[7] = 0.715F - cos * 0.715F + sin * 0.715F;
        HUE[8] = 0.072F + cos * 0.928F + sin * 0.072F;
    }
}
