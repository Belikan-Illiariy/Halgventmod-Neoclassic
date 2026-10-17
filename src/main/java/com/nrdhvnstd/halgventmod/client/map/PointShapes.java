package com.nrdhvnstd.halgventmod.client.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

/**
 * Точки карты на клиенте: какие видны в этом измерении, открыта ли точка, её значок (у неоткрытой — «?»),
 * и рисование границы на карте — заливка построчно (подходит для любой фигуры) и контур.
 */
public final class PointShapes {
    /** Неоткрытая точка — серая, открытая — светлая. */
    public static final int FILL_HIDDEN = 0x30808080;
    public static final int LINE_HIDDEN = 0xD0909090;
    public static final int FILL_OPEN = 0x38FFF4D8;
    public static final int LINE_OPEN = 0xFFFFF4D8;
    /** Неоткрытая точка вместо значка — белый знак вопроса; её название не показывается. */
    private static final String UNKNOWN = "?";
    public static final int COLOR_UNKNOWN = 0xFFFFFFFF;
    /** «Клетка» знака вопроса — в атласе такой нет. */
    public static final int UNKNOWN_CELL = -1;

    private PointShapes() {}

    /** Точки измерения, в котором сейчас игрок. */
    public static List<MapPoint> here() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        String dimension = minecraft.level.dimension().location().toString();
        List<MapPoint> result = new ArrayList<>();
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.dimension.equals(dimension)) {
                result.add(point);
            }
        }
        return result;
    }

    public static boolean discovered(MapPoint point) {
        return MapPoints.clientDiscovered.contains(point.id);
    }

    public static MapIcon icon(MapPoint point) {
        return MapIcon.byId(point.icon);
    }

    public static DyeColor dye(MapPoint point) {
        return DyeColor.byName(point.dye, DyeColor.RED);
    }

    /** Клетка атласа значков. */
    public static int cell(MapPoint point) {
        return icon(point).cell(dye(point));
    }

    /** Клетка атласа значка до открытия; UNKNOWN_CELL — «?» (значок не задан или клиенту неизвестен). */
    public static int hiddenCell(MapPoint point) {
        MapIcon icon = MapIcon.find(point.hiddenIcon);
        return icon == null ? UNKNOWN_CELL : icon.cell(dye(point));
    }

    /** Значок неоткрытой точки серединой в (x; y): свой значок до открытия или «?»; unknownColor — цвет «?». */
    public static void drawHidden(GuiGraphics graphics, Font font, MapPoint point, int x, int y, int size, int unknownColor) {
        int cell = hiddenCell(point);
        if (cell == UNKNOWN_CELL) {
            drawUnknown(graphics, font, x, y, size, 255, unknownColor);
        } else {
            MapIcon.draw(graphics, cell, x - size / 2, y - size / 2, size);
        }
    }

    /** Знак вопроса неоткрытой точки серединой в (x; y); size — высота в пикселях, alpha 0–255. */
    public static void drawUnknown(GuiGraphics graphics, Font font, float x, float y, float size, int alpha) {
        drawUnknown(graphics, font, x, y, size, alpha, COLOR_UNKNOWN);
    }

    /** То же другим цветом (alpha цвета не учитывается). */
    public static void drawUnknown(GuiGraphics graphics, Font font, float x, float y, float size, int alpha, int color) {
        if (alpha < 8) {
            return; // шрифт рисует почти прозрачный текст непрозрачным
        }
        float scale = size / font.lineHeight;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, UNKNOWN, -(font.width(UNKNOWN) - 1) / 2.0F, -(font.lineHeight - 1) / 2.0F,
                color & 0xFFFFFF | alpha << 24, true);
        graphics.pose().popPose();
    }

    /** Граница на экране: вершины многоугольника или круг из отрезков. Пары x, y подряд. */
    static float[] outline(MapView view, MapPoint point) {
        if (point.shape == MapPoint.Shape.CIRCLE) {
            float cx = view.screenX(point.x + 0.5);
            float cy = view.screenY(point.z + 0.5);
            float r = point.radius * view.zoom();
            int segments = Mth.clamp(Math.round(r / 3), 24, 160);
            float[] result = new float[segments * 2];
            for (int i = 0; i < segments; i++) {
                double angle = Math.PI * 2 * i / segments;
                result[2 * i] = cx + (float) Math.cos(angle) * r;
                result[2 * i + 1] = cy + (float) Math.sin(angle) * r;
            }
            return result;
        }
        float[] result = new float[point.xs.size() * 2];
        for (int i = 0; i < point.xs.size(); i++) {
            result[2 * i] = view.screenX(point.xs.get(i) + 0.5);
            result[2 * i + 1] = view.screenY(point.zs.get(i) + 0.5);
        }
        return result;
    }

    /** Залить и обвести границу точки. */
    static void draw(GuiGraphics graphics, MapView view, MapPoint point, int fill, int line) {
        float[] shape = outline(view, point);
        int count = shape.length / 2;
        if (count < 3) {
            return;
        }
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            minX = Math.min(minX, shape[2 * i]);
            maxX = Math.max(maxX, shape[2 * i]);
            minY = Math.min(minY, shape[2 * i + 1]);
            maxY = Math.max(maxY, shape[2 * i + 1]);
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (maxX < 0 || minX > width || maxY < 0 || minY > height) {
            return; // вся фигура за краем экрана
        }

        // Заливка: по строке экрана — отрезки между пересечениями с рёбрами (чёт-нечет)
        float[] crossings = new float[count];
        int fromRow = Math.max(0, Mth.floor(minY));
        int toRow = Math.min(height, Mth.ceil(maxY));
        for (int row = fromRow; row < toRow; row++) {
            float y = row + 0.5F;
            int found = 0;
            for (int i = 0, j = count - 1; i < count; j = i++) {
                float yi = shape[2 * i + 1];
                float yj = shape[2 * j + 1];
                if ((yi > y) != (yj > y)) {
                    float xi = shape[2 * i];
                    float xj = shape[2 * j];
                    crossings[found++] = xi + (y - yi) / (yj - yi) * (xj - xi);
                }
            }
            Arrays.sort(crossings, 0, found);
            for (int k = 0; k + 1 < found; k += 2) {
                int left = Math.max(0, Math.round(crossings[k]));
                int right = Math.min(width, Math.round(crossings[k + 1]));
                if (right > left) {
                    graphics.fill(left, row, right, row + 1, fill);
                }
            }
        }

        for (int i = 0, j = count - 1; i < count; j = i++) {
            line(graphics, shape[2 * j], shape[2 * j + 1], shape[2 * i], shape[2 * i + 1], line);
        }
    }

    /** Отрезок толщиной в пиксель: прямоугольник, повёрнутый вдоль него. */
    static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5F) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x1, y1, 0);
        graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        graphics.pose().scale(length, 1, 1);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popPose();
    }
}
