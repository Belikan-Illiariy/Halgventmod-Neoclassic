package com.nrdhvnstd.halgventmod.client.map;

import org.joml.Matrix4f;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Надпись игрока на карте. Поля открыты для Gson и окна правки.
 * Надпись лежит на карте, как название на бумажной: отдалили карту — она уменьшилась вместе с местностью.
 * Размер — scale блоков мира на пиксель шрифта; на экране это scale × масштаб карты.
 * Надпись можно повернуть и изогнуть дугой: буквы встают на окружность, arc — сколько градусов дуги
 * занимает вся надпись (180 — полукруг горкой ∩, −180 — чашей ∪, 360 — полный круг).
 */
public final class MapText {
    // Пиксель шрифта — от 1/64 блока (буквы меньше блока) до 256 блоков (название на полматерика)
    public static final float MIN_SCALE = 1.0F / 64;
    public static final float MAX_SCALE = 256.0F;
    public static final float MAX_ARC = 360.0F;
    // Надписи, сделанные, когда размер на экране не зависел от масштаба, пересчитываются так,
    // будто на них смотрели при обычном масштабе карты
    private static final float LEGACY_ZOOM = 2.0F;
    public static final int MAX_LENGTH = 48;
    private static final float STRAIGHT = 0.5F; // дуга меньше полуградуса — прямая

    private static final float OUTLINE_PIXELS = 1.5F; // толщина обводки в пикселях монитора — не растёт вместе с надписью
    private static final int OUTLINE_DARK = 0xFF20170E;
    private static final int OUTLINE_LIGHT = 0xFFE8DCC0;

    public String text;
    /** Середина надписи в блоках мира. */
    public int x;
    public int z;
    /** Блоков мира на пиксель шрифта. */
    public float scale;
    /** true — scale уже в блоках мира (у надписей старого формата его нет). */
    public boolean onMap;
    /** Поворот по часовой стрелке, в градусах. */
    public float rotation;
    /** Изгиб: градусов дуги на всю надпись; 0 — прямая. */
    public float arc;
    /** Цвет, RGB. */
    public int color;
    /** Только у надписей старого формата: размер 1–8 (половина шрифта за единицу). */
    public Integer size;

    public MapText(String text, int x, int z, float scale, int color) {
        this.text = text;
        this.x = x;
        this.z = z;
        this.scale = scale;
        this.color = color;
        this.onMap = true;
    }

    /** Масштаб на экране при этом масштабе карты. */
    float screenScale(float zoom) {
        return scale * zoom;
    }

    /** Надпись старого формата: размер → масштаб на карте; заодно поправить негодные значения. true — что-то поменялось. */
    boolean upgrade() {
        boolean changed = false;
        if (size != null) {
            scale = size * 0.5F;
            size = null;
            changed = true;
        }
        if (!onMap) {
            scale = (scale == 0 ? 2.0F : scale) / LEGACY_ZOOM;
            onMap = true;
            changed = true;
        }
        float fixedScale = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
        float fixedArc = Mth.clamp(arc, -MAX_ARC, MAX_ARC);
        changed |= fixedScale != scale || fixedArc != arc;
        scale = fixedScale;
        arc = fixedArc;
        return changed;
    }

    // ===== Раскладка букв: в единицах шрифта, середина надписи — (0; 0), до масштаба и поворота =====

    /** Буквы надписи: строка буквы, её ширина, середина (x; y) и наклон в радианах. */
    record Glyph(String letter, float width, float x, float y, float angle) {}

    static Glyph[] layout(Font font, String text, float arc) {
        int[] codePoints = text.codePoints().toArray();
        Glyph[] glyphs = new Glyph[codePoints.length];
        float total = font.width(text);
        boolean straight = Math.abs(arc) < STRAIGHT;
        // Буквы на окружности: середина надписи сверху (при arc > 0), центр окружности под ней
        float radius = straight ? 0 : total / (arc * Mth.DEG_TO_RAD);
        float along = -total / 2;
        for (int i = 0; i < codePoints.length; i++) {
            String letter = Character.toString(codePoints[i]);
            float width = font.width(letter);
            float middle = along + width / 2;
            if (straight) {
                glyphs[i] = new Glyph(letter, width, middle, 0, 0);
            } else {
                float angle = middle / radius;
                glyphs[i] = new Glyph(letter, width, radius * Mth.sin(angle), radius * (1 - Mth.cos(angle)), angle);
            }
            along += width;
        }
        return glyphs;
    }

    /** Границы надписи в единицах шрифта: {minX, minY, maxX, maxY}. */
    static float[] bounds(Font font, String text, float arc) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float halfHeight = font.lineHeight / 2.0F;
        for (Glyph glyph : layout(font, text, arc)) {
            float cos = Mth.cos(glyph.angle());
            float sin = Mth.sin(glyph.angle());
            for (int corner = 0; corner < 4; corner++) {
                float dx = (corner & 1) == 0 ? -glyph.width() / 2 : glyph.width() / 2;
                float dy = (corner & 2) == 0 ? -halfHeight : halfHeight;
                float px = glyph.x() + dx * cos - dy * sin;
                float py = glyph.y() + dx * sin + dy * cos;
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
        }
        if (minX > maxX) {
            return new float[] {0, -halfHeight, 0, halfHeight}; // пустая строка
        }
        return new float[] {minX, minY, maxX, maxY};
    }

    // ===== Экран =====

    /** Точка экрана → единицы шрифта надписи, нарисованной серединой в (centerX, centerY) при масштабе карты zoom. */
    float[] toLocal(float zoom, float centerX, float centerY, double screenX, double screenY) {
        float scale = screenScale(zoom);
        float dx = (float) (screenX - centerX) / scale;
        float dy = (float) (screenY - centerY) / scale;
        float angle = -rotation * Mth.DEG_TO_RAD;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        return new float[] {dx * cos - dy * sin, dx * sin + dy * cos};
    }

    /** Единицы шрифта → точка экрана. */
    float[] toScreen(float zoom, float centerX, float centerY, float localX, float localY) {
        float scale = screenScale(zoom);
        float angle = rotation * Mth.DEG_TO_RAD;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        return new float[] {centerX + (localX * cos - localY * sin) * scale, centerY + (localX * sin + localY * cos) * scale};
    }

    /** Попадает ли точка экрана в какую-нибудь букву (с запасом в пару пикселей). */
    boolean contains(Font font, float zoom, float centerX, float centerY, double mouseX, double mouseY) {
        float[] local = toLocal(zoom, centerX, centerY, mouseX, mouseY);
        float pad = 2.0F / screenScale(zoom);
        for (Glyph glyph : layout(font, text, arc)) {
            float dx = local[0] - glyph.x();
            float dy = local[1] - glyph.y();
            float cos = Mth.cos(-glyph.angle());
            float sin = Mth.sin(-glyph.angle());
            float gx = dx * cos - dy * sin;
            float gy = dx * sin + dy * cos;
            if (Math.abs(gx) <= glyph.width() / 2 + pad && Math.abs(gy) <= font.lineHeight / 2.0F + pad) {
                return true;
            }
        }
        return false;
    }

    void draw(GuiGraphics graphics, Font font, float zoom, float centerX, float centerY) {
        draw(graphics, font, text, color, screenScale(zoom), rotation, arc, centerX, centerY);
    }

    /** Надпись серединой в (centerX, centerY), с обводкой: тёмные чернила — светлой, светлые — тёмной. */
    static void draw(GuiGraphics graphics, Font font, String text, int color, float scale, float rotation, float arc,
                     float centerX, float centerY) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int outline = r * 299 + g * 587 + b * 114 > 128_000 ? OUTLINE_DARK : OUTLINE_LIGHT;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        graphics.pose().scale(scale, scale, 1);
        // В единицах шрифта — чтобы на мониторе всегда было OUTLINE_PIXELS, при любом размере надписи и масштабе интерфейса
        float thickness = OUTLINE_PIXELS / (scale * (float) Minecraft.getInstance().getWindow().getGuiScale());
        if (Math.abs(arc) < STRAIGHT) {
            outlined(graphics, font, text, -font.width(text) / 2.0F, -font.lineHeight / 2.0F + 1, color, outline, thickness);
        } else {
            // По букве: каждая стоит на дуге и повёрнута по ней
            for (Glyph glyph : layout(font, text, arc)) {
                graphics.pose().pushPose();
                graphics.pose().translate(glyph.x(), glyph.y(), 0);
                graphics.pose().mulPose(Axis.ZP.rotation(glyph.angle()));
                outlined(graphics, font, glyph.letter(), -glyph.width() / 2, -font.lineHeight / 2.0F + 1, color, outline, thickness);
                graphics.pose().popPose();
            }
        }
        graphics.pose().popPose();
    }

    // Обводка постоянной толщины: текст цветом обводки со сдвигом в восемь сторон, сверху — сам текст.
    // Ванильная обводка (drawInBatch8xOutline) толщиной в пиксель шрифта и у крупной надписи раздувается
    private static void outlined(GuiGraphics graphics, Font font, String text, float x, float y, int color, int outline,
                                 float thickness) {
        FormattedCharSequence sequence = FormattedCharSequence.forward(text, Style.EMPTY);
        Matrix4f pose = graphics.pose().last().pose();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    font.drawInBatch(sequence, x + dx * thickness, y + dy * thickness, outline, false, pose,
                            graphics.bufferSource(), Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                }
            }
        }
        font.drawInBatch(sequence, x, y, 0xFF000000 | color, false, pose, graphics.bufferSource(),
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }
}
