package com.nrdhvnstd.halgventmod.client.map;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.slf4j.Logger;

/**
 * Стиль карты: основа (пресет) и тонкая настройка поверх неё. Регион хранит «сырой» цвет блока
 * и высоту (MapRegion), а картинку на экране собирает отсюда — apply(). Тень от рельефа тоже здесь,
 * поэтому её сила настраивается. Стиль один на все миры: .minecraft/halgventmod/map_style.json.
 */
public final class MapStyle {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "map_style.json";

    /** Альфа «сырого» пикселя: обычный блок и блок, окрашенный биомом (трава, листва, вода). 0 — неизвестно. */
    public static final int ALPHA_PLAIN = 0xFF;
    public static final int ALPHA_TINTED = 0xFE;

    public enum Group {
        COLOR, RELIEF, PAPER;

        public Component title() {
            return Component.translatable("gui.halgventmod.map.style.group." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Ползунки: пределы и единица (% или °). */
    public enum Setting {
        SATURATION(Group.COLOR, 0, 200, false),
        CONTRAST(Group.COLOR, 50, 200, false),
        BRIGHTNESS(Group.COLOR, 50, 150, false),
        SEPIA(Group.COLOR, 0, 100, false),
        HUE(Group.COLOR, -180, 180, true),
        BIOME_COLOR(Group.COLOR, 0, 100, false),
        GREEN_MUTE(Group.COLOR, 0, 100, false),
        SLOPES(Group.RELIEF, 0, 200, false),
        HEIGHTS(Group.RELIEF, 0, 100, false),
        GRAIN(Group.PAPER, 0, 100, false);

        public final Group group;
        public final int min;
        public final int max;
        public final boolean degrees;

        Setting(Group group, int min, int max, boolean degrees) {
            this.group = group;
            this.min = min;
            this.max = max;
            this.degrees = degrees;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component title() {
            return Component.translatable("gui.halgventmod.map.style." + key());
        }
    }

    /** Основы. Значения — в порядке Setting. */
    public enum Base {
        //            насыщ контр ярк сепия оттен биомы зелень склоны высоты зерно
        VANILLA(new int[] {115, 105, 100, 0, 0, 100, 0, 100, 0, 0}),
        PARCHMENT(new int[] {45, 95, 105, 75, 0, 60, 55, 140, 30, 35}),
        ENGRAVING(new int[] {0, 140, 105, 20, 0, 0, 0, 200, 45, 50}),
        ATLAS(new int[] {70, 100, 105, 15, -8, 75, 45, 120, 20, 10});

        private final int[] defaults;

        Base(int[] defaults) {
            this.defaults = defaults;
        }

        public Component title() {
            return Component.translatable("gui.halgventmod.map.style.base." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static Base base = Base.PARCHMENT;
    private static final int[] VALUES = Base.PARCHMENT.defaults.clone();
    private static boolean biomeLabels = true;
    private static boolean loaded;
    private static boolean dirty;
    /** Растёт при каждой смене стиля: регионы с другим номером пересобирают картинку. */
    private static int version;

    // Готовые к расчёту множители — пересчитываются при смене стиля
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

    private MapStyle() {}

    public static Base base() {
        load();
        return base;
    }

    public static int get(Setting setting) {
        load();
        return VALUES[setting.ordinal()];
    }

    /** Показывать ли названия биомов. К основе не относится: сброс её не трогает. */
    public static boolean biomeLabels() {
        load();
        return biomeLabels;
    }

    public static void setBiomeLabels(boolean shown) {
        load();
        if (biomeLabels != shown) {
            biomeLabels = shown;
            dirty = true;
        }
    }

    public static int version() {
        load();
        return version;
    }

    /** Новая основа: все ползунки — к её значениям. */
    public static void setBase(Base newBase) {
        load();
        base = newBase;
        System.arraycopy(newBase.defaults, 0, VALUES, 0, VALUES.length);
        changed();
    }

    /** Сброс тонкой настройки к значениям основы. */
    public static void reset() {
        setBase(base());
    }

    public static void set(Setting setting, int value) {
        load();
        value = Mth.clamp(value, setting.min, setting.max);
        if (VALUES[setting.ordinal()] != value) {
            VALUES[setting.ordinal()] = value;
            changed();
        }
    }

    private static void changed() {
        version++;
        dirty = true;
        prepare();
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
        saturation = VALUES[Setting.SATURATION.ordinal()] / 100.0F;
        contrast = VALUES[Setting.CONTRAST.ordinal()] / 100.0F;
        brightness = VALUES[Setting.BRIGHTNESS.ordinal()] / 100.0F;
        sepia = VALUES[Setting.SEPIA.ordinal()] / 100.0F;
        biomeColor = VALUES[Setting.BIOME_COLOR.ordinal()] / 100.0F;
        greenMute = VALUES[Setting.GREEN_MUTE.ordinal()] / 100.0F;
        slopes = VALUES[Setting.SLOPES.ordinal()] / 100.0F;
        heights = VALUES[Setting.HEIGHTS.ordinal()] / 100.0F;
        grain = VALUES[Setting.GRAIN.ordinal()] / 100.0F;

        // Матрица поворота оттенка (как hue-rotate в CSS)
        double angle = Math.toRadians(VALUES[Setting.HUE.ordinal()]);
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

    // ===== Файл =====

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(HalgventMod.MODID).resolve(FILE);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    JsonElement baseName = json.get("base");
                    if (baseName != null) {
                        for (Base candidate : Base.values()) {
                            if (candidate.name().equalsIgnoreCase(baseName.getAsString())) {
                                base = candidate;
                            }
                        }
                    }
                    System.arraycopy(base.defaults, 0, VALUES, 0, VALUES.length);
                    JsonElement labels = json.get("biome_labels");
                    if (labels != null) {
                        biomeLabels = labels.getAsBoolean();
                    }
                    JsonObject values = json.getAsJsonObject("values");
                    if (values != null) {
                        for (Setting setting : Setting.values()) {
                            JsonElement value = values.get(setting.key());
                            if (value != null) {
                                VALUES[setting.ordinal()] = Mth.clamp(value.getAsInt(), setting.min, setting.max);
                            }
                        }
                    }
                }
            } catch (IOException | JsonParseException | ClassCastException | IllegalStateException e) {
                LOGGER.warn("Не удалось прочитать стиль карты {}", file, e);
            }
        }
        version++;
        prepare();
    }

    /** Записать, если менялся (при закрытии карты). */
    public static void saveIfChanged() {
        if (!dirty) {
            return;
        }
        dirty = false;
        JsonObject json = new JsonObject();
        json.addProperty("base", base.name().toLowerCase(Locale.ROOT));
        json.addProperty("biome_labels", biomeLabels);
        JsonObject values = new JsonObject();
        for (Setting setting : Setting.values()) {
            values.addProperty(setting.key(), VALUES[setting.ordinal()]);
        }
        json.add("values", values);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Не удалось сохранить стиль карты {}", file, e);
        }
    }
}
