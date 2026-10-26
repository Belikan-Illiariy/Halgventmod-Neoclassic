package com.nrdhvnstd.halgventmod.client.map;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.network.RouteEditPayload;
import com.nrdhvnstd.halgventmod.point.MapRoute;
import com.nrdhvnstd.halgventmod.point.MapRoutes;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Маршруты на технической карте (только администрация). Рисование: первый ЛКМ по карте — начало, каждый
 * следующий — точка маршрута; между точками прямые, от последней до курсора — предпросмотр. Enter — завершить
 * (окно названия и цвета, MapScreen), ПКМ или Esc — бросить, ничего не сохраняя. Меньше двух точек — Enter не работает.
 * Готовый маршрут: ЛКМ по линии — выделить, вершины выделенного тянутся мышью, двойной клик по линии — то же окно,
 * Delete — удалить (с подтверждением). Название — подписью у начала, тусклым.
 */
final class RouteEditor {
    /** Цвета маршрутов в окне; первый — по умолчанию (светло-оранжевый). */
    static final int[] COLORS = {MapRoute.DEFAULT_COLOR, 0xFFE04848, 0xFF4898E0, 0xFF58C060, 0xFFE8D850, 0xFFB070E0,
            0xFFF0F0F0, 0xFF505050};

    private static final float THICKNESS = 2;
    private static final int HIT_DISTANCE = 4;      // пикселей экрана до линии или вершины
    private static final int VERTEX_SIZE = 5;
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_PREVIEW = 0x80FFFFFF;
    private static final int COLOR_NAME = 0xFFB8A888;
    private static final long DOUBLE_CLICK_MILLIS = 300;

    /** Что окно карты делает по просьбе редактора. */
    interface Host {
        /** Окно названия и цвета: новый маршрут (id 0) или правка готового. */
        void openDialog(MapRoute draft);

        /** Спросить и удалить маршрут. */
        void confirmDelete(MapRoute route);
    }

    private final MapView view;
    private final Host host;
    // Прокладываемый маршрут; null — не рисуем
    @Nullable
    private List<int[]> drawing;
    // Выделенный готовый маршрут — копия для правки вершин
    @Nullable
    private MapRoute selected;
    private int dragVertex = -1;
    private boolean moved;
    private int lastClickedId;
    private long lastClickMillis;

    RouteEditor(MapView view, Host host) {
        this.view = view;
        this.host = host;
    }

    boolean drawing() {
        return drawing != null;
    }

    /** Бросить рисование и снять выделение (сменили инструмент). */
    void reset() {
        drawing = null;
        selected = null;
        dragVertex = -1;
    }

    // ===== Рисование =====

    void render(GuiGraphics graphics, Font font, String dimension, int mouseX, int mouseY, boolean showAll) {
        if (selected != null && find(selected.id) == null) {
            selected = null; // удалили
        }
        if (showAll) {
            for (MapRoute route : MapRoutes.clientRoutes) {
                if (!route.dimension.equals(dimension)) {
                    continue;
                }
                MapRoute shown = selected != null && selected.id == route.id ? selected : route;
                polyline(graphics, shown.xs, shown.zs, shown.color);
                if (!shown.name.isEmpty()) {
                    graphics.drawString(font, shown.name, Math.round(screenX(shown.xs.get(0))) + 4,
                            Math.round(screenY(shown.zs.get(0))) - font.lineHeight - 2, COLOR_NAME, false);
                }
            }
        }
        if (selected != null) {
            for (int i = 0; i < selected.size(); i++) {
                vertex(graphics, screenX(selected.xs.get(i)), screenY(selected.zs.get(i)), COLOR_SELECTED);
            }
        }
        if (drawing != null) {
            List<Integer> xs = new ArrayList<>();
            List<Integer> zs = new ArrayList<>();
            for (int[] point : drawing) {
                xs.add(point[0]);
                zs.add(point[1]);
            }
            polyline(graphics, xs, zs, MapRoute.DEFAULT_COLOR);
            int[] last = drawing.get(drawing.size() - 1);
            thickLine(graphics, screenX(last[0]), screenY(last[1]), mouseX, mouseY, COLOR_PREVIEW, 1);
            for (int[] point : drawing) {
                vertex(graphics, screenX(point[0]), screenY(point[1]), MapRoute.DEFAULT_COLOR);
            }
        }
    }

    private void polyline(GuiGraphics graphics, List<Integer> xs, List<Integer> zs, int color) {
        for (int i = 1; i < xs.size(); i++) {
            thickLine(graphics, screenX(xs.get(i - 1)), screenY(zs.get(i - 1)), screenX(xs.get(i)), screenY(zs.get(i)),
                    color, THICKNESS);
        }
    }

    private static void vertex(GuiGraphics graphics, float x, float y, int color) {
        int half = VERTEX_SIZE / 2;
        int cx = Math.round(x);
        int cy = Math.round(y);
        graphics.fill(cx - half - 1, cy - half - 1, cx + half + 2, cy + half + 2, 0xFF000000);
        graphics.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, color);
    }

    /** Отрезок нужной толщины: прямоугольник, повёрнутый вдоль него. */
    static void thickLine(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5F) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x1, y1, 0);
        graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        graphics.pose().translate(0, -thickness / 2, 0);
        graphics.pose().scale(length, thickness, 1);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popPose();
    }

    // ===== Ввод (инструмент «Маршрут») =====

    /** Нажатие: вершина выделенного — тащить её. true — обработано. */
    boolean pressed(double mouseX, double mouseY, int button) {
        if (drawing == null && selected != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragVertex = vertexAt(selected, mouseX, mouseY);
            moved = false;
            return dragVertex >= 0;
        }
        return false;
    }

    boolean dragged(double mouseX, double mouseY) {
        if (dragVertex < 0 || selected == null) {
            return false;
        }
        selected.xs.set(dragVertex, Mth.floor(view.worldX(mouseX)));
        selected.zs.set(dragVertex, Mth.floor(view.worldZ(mouseY)));
        moved = true;
        return true;
    }

    void released() {
        if (dragVertex >= 0 && moved && selected != null) {
            PacketDistributor.sendToServer(new RouteEditPayload(MapRoutes.Action.UPDATE, selected));
        }
        dragVertex = -1;
    }

    /** Клик по карте. */
    void clicked(double mouseX, double mouseY, int button) {
        int x = Mth.floor(view.worldX(mouseX));
        int z = Mth.floor(view.worldZ(mouseY));
        if (drawing != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                drawing = null;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && drawing.size() < MapRoute.MAX_POINTS) {
                drawing.add(new int[] {x, z});
            }
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        MapRoute hit = routeAt(mouseX, mouseY);
        if (hit != null) {
            long now = Util.getMillis();
            if (hit.id == lastClickedId && now - lastClickMillis < DOUBLE_CLICK_MILLIS) {
                lastClickedId = 0;
                host.openDialog(hit.copy());
            } else {
                lastClickedId = hit.id;
                lastClickMillis = now;
            }
            selected = hit.copy();
            return;
        }
        if (selected != null) {
            selected = null; // клик мимо — снять выделение
            return;
        }
        drawing = new ArrayList<>();
        drawing.add(new int[] {x, z});
    }

    /** Клавиши: Enter — завершить, Esc — бросить, Delete — удалить выделенный. true — обработано. */
    boolean keyPressed(int keyCode) {
        if (drawing != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (drawing.size() >= MapRoute.MIN_POINTS) {
                    MapRoute draft = new MapRoute();
                    for (int[] point : drawing) {
                        draft.xs.add(point[0]);
                        draft.zs.add(point[1]);
                    }
                    drawing = null;
                    host.openDialog(draft);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                drawing = null;
                return true;
            }
            return false;
        }
        if (selected != null) {
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                host.confirmDelete(selected);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                selected = null;
                return true;
            }
        }
        return false;
    }

    // ===== Попадание =====

    /** Маршрут, к линии которого курсор ближе HIT_DISTANCE (только этого измерения). */
    @Nullable
    MapRoute routeAt(double mouseX, double mouseY) {
        String dimension = Minecraft.getInstance().level == null ? ""
                : Minecraft.getInstance().level.dimension().location().toString();
        MapRoute found = null;
        double best = HIT_DISTANCE * HIT_DISTANCE;
        for (MapRoute route : MapRoutes.clientRoutes) {
            if (!route.dimension.equals(dimension)) {
                continue;
            }
            for (int i = 1; i < route.size(); i++) {
                double distance = segmentDistance(mouseX, mouseY, screenX(route.xs.get(i - 1)), screenY(route.zs.get(i - 1)),
                        screenX(route.xs.get(i)), screenY(route.zs.get(i)));
                if (distance <= best) {
                    best = distance;
                    found = route;
                }
            }
        }
        return found;
    }

    private int vertexAt(MapRoute route, double mouseX, double mouseY) {
        for (int i = 0; i < route.size(); i++) {
            double dx = screenX(route.xs.get(i)) - mouseX;
            double dy = screenY(route.zs.get(i)) - mouseY;
            if (dx * dx + dy * dy <= HIT_DISTANCE * HIT_DISTANCE) {
                return i;
            }
        }
        return -1;
    }

    // Квадрат расстояния от точки до отрезка
    private static double segmentDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSq = dx * dx + dy * dy;
        double t = lengthSq == 0 ? 0 : Mth.clamp(((px - x1) * dx + (py - y1) * dy) / lengthSq, 0, 1);
        double cx = x1 + t * dx - px;
        double cy = y1 + t * dy - py;
        return cx * cx + cy * cy;
    }

    @Nullable
    private static MapRoute find(int id) {
        for (MapRoute route : MapRoutes.clientRoutes) {
            if (route.id == id) {
                return route;
            }
        }
        return null;
    }

    private float screenX(int worldX) {
        return view.screenX(worldX + 0.5);
    }

    private float screenY(int worldZ) {
        return view.screenY(worldZ + 0.5);
    }
}
