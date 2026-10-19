package com.nrdhvnstd.halgventmod.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.point.MapPoint;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * Граница точки прямо на карте — до окна точки (администратор: «Поставить точку», «Изменить границы»).
 * Круг: колесико (и стрелки) — радиус, ручка на краю круга — тянуть радиус.
 * Многоугольник: вершины тянутся мышью; кружок посередине ребра — тянуть, и там появится новая вершина;
 * ПКМ по вершине — убрать её (меньше трёх нельзя); стрелки двигают последнюю тронутую вершину на блок.
 * Значок посередине — тянуть всю точку вместе с границей.
 */
final class PointBoundsEditor {
    private static final int HANDLE = 6;        // квадрат вершины
    private static final int MID_HANDLE = 4;    // кружок посередине ребра
    private static final int PICK = 6;          // на сколько пикселей можно промахнуться
    private static final int COLOR_FILL = 0x40FFD040;
    private static final int COLOR_LINE = 0xFFFFD040;
    private static final int COLOR_HANDLE = 0xFFFFFFFF;
    private static final int COLOR_HANDLE_FRAME = 0xFF000000;
    private static final int COLOR_MID = 0xC0FFFFFF;

    private static final int DRAG_NONE = -1;
    private static final int DRAG_CENTER = -2;
    private static final int DRAG_RADIUS = -3;

    private final MapView view;
    private final MapPoint point;
    private int drag = DRAG_NONE;   // номер вершины или DRAG_*
    private int selected = -1;      // последняя тронутая вершина — для стрелок
    // Сдвиг курсора от того, что тянем, чтобы оно не прыгало под курсор
    private double grabX;
    private double grabZ;

    PointBoundsEditor(MapView view, MapPoint point) {
        this.view = view;
        this.point = point;
    }

    MapPoint point() {
        return point;
    }

    void setShape(MapPoint.Shape shape) {
        if (point.shape == shape) {
            return;
        }
        if (shape == MapPoint.Shape.POLYGON) {
            point.squareAround(Mth.clamp(point.radius, MapPoint.MIN_RADIUS, MapPoint.MAX_RADIUS));
        } else {
            double farthest = 0;
            for (int i = 0; i < point.xs.size(); i++) {
                farthest = Math.max(farthest, Math.hypot(point.xs.get(i) - point.x, point.zs.get(i) - point.z));
            }
            point.radius = Mth.clamp((int) Math.round(farthest), MapPoint.MIN_RADIUS, MapPoint.MAX_RADIUS);
        }
        point.shape = shape;
        selected = -1;
        drag = DRAG_NONE;
    }

    // ===== Рисование =====

    void render(GuiGraphics graphics, int iconSize) {
        RenderSystem.enableBlend();
        PointShapes.draw(graphics, view, point, COLOR_FILL, COLOR_LINE);
        if (point.shape == MapPoint.Shape.POLYGON) {
            int count = point.xs.size();
            for (int i = 0; i < count; i++) {
                int next = (i + 1) % count;
                float mx = (vx(i) + vx(next)) / 2;
                float my = (vy(i) + vy(next)) / 2;
                dot(graphics, mx, my, MID_HANDLE, COLOR_MID);
            }
            for (int i = 0; i < count; i++) {
                square(graphics, vx(i), vy(i), i == selected ? HANDLE + 2 : HANDLE);
            }
        } else {
            square(graphics, radiusX(), centerY(), HANDLE);
        }
        MapIcon.draw(graphics, PointShapes.cell(point), Math.round(centerX()) - iconSize / 2,
                Math.round(centerY()) - iconSize / 2, iconSize);
        RenderSystem.disableBlend();
    }

    private static void square(GuiGraphics graphics, float x, float y, int size) {
        int left = Math.round(x - size / 2.0F);
        int top = Math.round(y - size / 2.0F);
        graphics.fill(left - 1, top - 1, left + size + 1, top + size + 1, COLOR_HANDLE_FRAME);
        graphics.fill(left, top, left + size, top + size, COLOR_HANDLE);
    }

    private static void dot(GuiGraphics graphics, float x, float y, int size, int color) {
        int left = Math.round(x - size / 2.0F);
        int top = Math.round(y - size / 2.0F);
        graphics.fill(left, top + 1, left + size, top + size - 1, color);
        graphics.fill(left + 1, top, left + size - 1, top + size, color);
    }

    // ===== Мышь и клавиши =====

    /** true — нажатие наше (ручка, вершина, значок). */
    boolean mousePressed(double mouseX, double mouseY, int button, int iconSize) {
        if (point.shape == MapPoint.Shape.POLYGON) {
            int vertex = vertexAt(mouseX, mouseY);
            if (vertex >= 0) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (point.xs.size() > MapPoint.MIN_VERTICES) {
                        point.xs.remove(vertex);
                        point.zs.remove(vertex);
                        selected = -1;
                    }
                    return true;
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    startDrag(vertex, mouseX, mouseY, point.xs.get(vertex), point.zs.get(vertex));
                    return true;
                }
            }
            int edge = button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? midpointAt(mouseX, mouseY) : -1;
            if (edge >= 0 && point.xs.size() < MapPoint.MAX_VERTICES) {
                int next = (edge + 1) % point.xs.size();
                int x = Math.round((point.xs.get(edge) + point.xs.get(next)) / 2.0F);
                int z = Math.round((point.zs.get(edge) + point.zs.get(next)) / 2.0F);
                point.xs.add(edge + 1, x);
                point.zs.add(edge + 1, z);
                startDrag(edge + 1, mouseX, mouseY, x, z);
                return true;
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && near(mouseX, mouseY, radiusX(), centerY(), PICK)) {
            drag = DRAG_RADIUS;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && near(mouseX, mouseY, centerX(), centerY(), iconSize / 2)) {
            startDrag(DRAG_CENTER, mouseX, mouseY, point.x, point.z);
            return true;
        }
        return false;
    }

    private void startDrag(int what, double mouseX, double mouseY, int worldX, int worldZ) {
        drag = what;
        if (what >= 0) {
            selected = what;
        }
        grabX = worldX - view.worldX(mouseX);
        grabZ = worldZ - view.worldZ(mouseY);
    }

    boolean dragging() {
        return drag != DRAG_NONE;
    }

    void mouseDragged(double mouseX, double mouseY) {
        if (drag == DRAG_RADIUS) {
            double dx = view.worldX(mouseX) - (point.x + 0.5);
            double dz = view.worldZ(mouseY) - (point.z + 0.5);
            point.radius = Mth.clamp((int) Math.round(Math.hypot(dx, dz)), MapPoint.MIN_RADIUS, MapPoint.MAX_RADIUS);
            return;
        }
        int x = Mth.floor(view.worldX(mouseX) + grabX + 0.5);
        int z = Mth.floor(view.worldZ(mouseY) + grabZ + 0.5);
        if (drag == DRAG_CENTER) {
            point.moveBy(x - point.x, z - point.z);
        } else if (drag >= 0 && drag < point.xs.size()) {
            point.xs.set(drag, clampAround(x, point.x));
            point.zs.set(drag, clampAround(z, point.z));
        }
    }

    void mouseReleased() {
        drag = DRAG_NONE;
    }

    /** Колесико над картой: радиус круга. false — у многоугольника колесико ни при чём. */
    boolean scroll(double amount) {
        if (point.shape != MapPoint.Shape.CIRCLE) {
            return false;
        }
        int step = Math.max(1, Math.round(point.radius * 0.1F)); // большой круг — шагом побольше
        point.radius = Mth.clamp(point.radius + (amount > 0 ? step : -step), MapPoint.MIN_RADIUS, MapPoint.MAX_RADIUS);
        return true;
    }

    /** Стрелки: радиус круга на блок или последняя тронутая вершина на блок. */
    boolean keyPressed(int keyCode) {
        int dx = keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 0;
        int dz = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : keyCode == GLFW.GLFW_KEY_UP ? -1 : 0;
        if (dx == 0 && dz == 0) {
            return false;
        }
        if (point.shape == MapPoint.Shape.CIRCLE) {
            int change = dx != 0 ? dx : -dz; // вверх и вправо — больше
            point.radius = Mth.clamp(point.radius + change, MapPoint.MIN_RADIUS, MapPoint.MAX_RADIUS);
            return true;
        }
        if (selected < 0 || selected >= point.xs.size()) {
            return false;
        }
        point.xs.set(selected, clampAround(point.xs.get(selected) + dx, point.x));
        point.zs.set(selected, clampAround(point.zs.get(selected) + dz, point.z));
        return true;
    }

    // ===== Попадание =====

    private int vertexAt(double mouseX, double mouseY) {
        for (int i = point.xs.size() - 1; i >= 0; i--) {
            if (near(mouseX, mouseY, vx(i), vy(i), PICK)) {
                return i;
            }
        }
        return -1;
    }

    private int midpointAt(double mouseX, double mouseY) {
        int count = point.xs.size();
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            if (near(mouseX, mouseY, (vx(i) + vx(next)) / 2, (vy(i) + vy(next)) / 2, PICK)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean near(double mouseX, double mouseY, float x, float y, int distance) {
        return Math.abs(mouseX - x) <= distance && Math.abs(mouseY - y) <= distance;
    }

    private static int clampAround(int value, int center) {
        return Mth.clamp(value, center - MapPoint.MAX_RADIUS, center + MapPoint.MAX_RADIUS);
    }

    private float vx(int i) {
        return view.screenX(point.xs.get(i) + 0.5);
    }

    private float vy(int i) {
        return view.screenY(point.zs.get(i) + 0.5);
    }

    private float centerX() {
        return view.screenX(point.x + 0.5);
    }

    private float centerY() {
        return view.screenY(point.z + 0.5);
    }

    private float radiusX() {
        return centerX() + point.radius * view.zoom();
    }
}
