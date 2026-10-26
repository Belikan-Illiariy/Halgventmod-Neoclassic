package com.nrdhvnstd.halgventmod.client.map;

import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.client.MenuTabs;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Карта одной локации: лист на столе, обрезанный по её границе (LocationSheet). Изведанная — настоящая карта
 * (снимок сервера и то, что игрок видел сам), неизведанная — туман и «Локация неизведана».
 * Камера упирается в лист: середину экрана не увести за лист, отдалить — пока лист не виден целиком; ещё шаг
 * колёсиком наружу — глобальная карта в фокусе на регионе этой локации (как и кнопка «Хальгвент»).
 * Игрок рисуется, только если он в этой локации; соотрядники, метки и карандаш — только на листе.
 * Открывается по M (локация, где стоит игрок) и из окна локации на глобальной карте — для любой локации;
 * открытая оттуда, по Esc возвращается на глобальную карту.
 */
public class LocationMapScreen extends MapScreenBase {
    private static final int TOP_ROW = 20;
    private static final int HALGVENT_WIDTH = 90;

    private final int pointId;
    @Nullable
    private final Screen parent;
    private boolean placed;

    public LocationMapScreen(int pointId, @Nullable Screen parent) {
        super(Component.translatable("gui.halgventmod.location_map.title"));
        this.pointId = pointId;
        this.parent = parent;
    }

    public int pointId() {
        return pointId;
    }

    @Nullable
    MapPoint point() {
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.id == pointId) {
                return point;
            }
        }
        return null;
    }

    private boolean explored(MapPoint point) {
        return PointShapes.discovered(point);
    }

    @Override
    protected void init() {
        MapPoint point = point();
        if (point == null) {
            onClose(); // точку удалили
            return;
        }
        if (!placed) {
            // Первое открытие: весь лист на экране, середина — на игроке, если он здесь
            placed = true;
            int[] rect = LocationSheet.rect(point);
            centerX = (rect[0] + rect[2]) / 2.0;
            centerZ = (rect[1] + rect[3]) / 2.0;
            zoom = minZoom();
            if (showPlayer()) {
                centerOnPlayer();
            }
        }
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.location_map.halgvent"),
                button -> openGlobal()).bounds(MARGIN, MenuTabs.TAB_HEIGHT + MARGIN, HALGVENT_WIDTH, TOP_ROW).build());
    }

    @Override
    protected List<Tool> tools() {
        MapPoint point = point();
        return point != null && explored(point) ? List.of(Tool.MOVE, Tool.DRAW, Tool.WAYPOINT) : List.of(Tool.MOVE);
    }

    @Override
    protected List<MapLayer> layers() {
        return List.of(MapLayer.DRAWING, MapLayer.WAYPOINTS, MapLayer.SQUAD);
    }

    @Override
    protected int topReserved() {
        return MenuTabs.TAB_HEIGHT + 2 * MARGIN + TOP_ROW;
    }

    // ===== Камера =====

    // Весь лист — на свободном месте экрана; при отдалении — степень двойки в пикселях монитора, чтобы тайлы были чёткими
    @Override
    protected float minZoom() {
        MapPoint point = point();
        if (point == null) {
            return super.minZoom();
        }
        int[] rect = LocationSheet.rect(point);
        float freeWidth = width - 2 * MARGIN;
        float freeHeight = height - topReserved() - TOOL_SIZE - 3 * MARGIN;
        float fit = Math.min(freeWidth / (rect[2] - rect[0]), freeHeight / (rect[3] - rect[1]));
        double scale = minecraft.getWindow().getGuiScale();
        double real = fit * scale;
        if (real < 1) {
            real = Math.max(MIN_REAL_ZOOM, Math.pow(2, Math.floor(Math.log(real) / Math.log(2))));
        }
        return (float) Math.min(real / scale, MAX_ZOOM);
    }

    @Override
    protected void clampCamera() {
        MapPoint point = point();
        if (point == null) {
            return;
        }
        int[] rect = LocationSheet.rect(point);
        centerX = Mth.clamp(centerX, rect[0], rect[2]);
        centerZ = Mth.clamp(centerZ, rect[1], rect[3]);
    }

    @Override
    protected boolean zoomOutBeyond() {
        openGlobal();
        return true;
    }

    @Override
    protected void centerOnPlayer() {
        if (showPlayer()) {
            super.centerOnPlayer();
        }
    }

    // ===== Что где видно =====

    @Override
    protected boolean showPlayer() {
        MapPoint point = point();
        LocalPlayer player = minecraft.player;
        return point != null && player != null && minecraft.level != null
                && point.dimension.equals(minecraft.level.dimension().location().toString())
                && point.contains(player.getX(), player.getZ());
    }

    private boolean onSheet(double worldX, double worldZ) {
        MapPoint point = point();
        if (point == null) {
            return false;
        }
        int[] rect = LocationSheet.rect(point);
        return worldX >= rect[0] && worldX < rect[2] && worldZ >= rect[1] && worldZ < rect[3];
    }

    @Override
    protected boolean editableAt(double worldX, double worldZ) {
        MapPoint point = point();
        return point != null && explored(point) && onSheet(worldX, worldZ);
    }

    @Override
    protected boolean waypointShown(Waypoint waypoint) {
        MapPoint point = point();
        return point != null && explored(point) && onSheet(waypoint.x + 0.5, waypoint.z + 0.5);
    }

    // ===== Рисование =====

    @Override
    protected void renderWorld(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(LocationSheet.TABLE, 0, 0, 0, 0, width, height, LocationSheet.TABLE_SIZE, LocationSheet.TABLE_SIZE);
        MapPoint point = point();
        if (point == null) {
            return;
        }
        boolean explored = explored(point);
        LocationSheet.renderFrame(graphics, view, point);
        LocationSheet.render(graphics, view, point, explored, 0, 0, width, height);
        if (!explored) {
            LocationSheet.renderUnexplored(graphics, font, view, point);
        } else if (MapLayer.DRAWING.shown()) {
            sheetScissor(graphics, point);
            TileRenderer.renderDrawings(graphics, view, 0, 0, width, height);
            graphics.disableScissor();
        }
    }

    // Соотрядники — только на листе
    @Override
    protected void renderSquad(GuiGraphics graphics) {
        MapPoint point = point();
        if (point == null) {
            return;
        }
        sheetScissor(graphics, point);
        super.renderSquad(graphics);
        graphics.disableScissor();
    }

    private void sheetScissor(GuiGraphics graphics, MapPoint point) {
        float[] sheet = LocationSheet.screenRect(view, point);
        graphics.enableScissor(Math.max(0, Mth.floor(sheet[0])), Math.max(0, Mth.floor(sheet[1])),
                Math.min(width, Mth.ceil(sheet[2])), Math.min(height, Mth.ceil(sheet[3])));
    }

    // Название локации — вверху по середине (неоткрытая — «Неизвестное место»)
    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MapPoint point = point();
        if (point == null) {
            return;
        }
        Component name = explored(point) ? Component.literal(point.name)
                : Component.translatable("gui.halgventmod.point.unknown");
        int y = MenuTabs.TAB_HEIGHT + MARGIN + (TOP_ROW - font.lineHeight) / 2 + 1;
        int textWidth = font.width(name);
        panel(graphics, (width - textWidth) / 2 - 6, y - 4, textWidth + 12, font.lineHeight + 6);
        graphics.drawCenteredString(font, name, width / 2, y, COLOR_TITLE);
    }

    // ===== Переходы =====

    private void openGlobal() {
        MapPoint point = point();
        GlobalMapScreen global = parent instanceof GlobalMapScreen existing ? existing : new GlobalMapScreen();
        if (point != null) {
            global.focusOn(point);
        }
        minecraft.setScreen(global);
    }

    @Override
    public void onClose() {
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
