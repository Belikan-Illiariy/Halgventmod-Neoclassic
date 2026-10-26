package com.nrdhvnstd.halgventmod.client.map;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.MapTooltipBox;
import com.nrdhvnstd.halgventmod.client.MenuTab;
import com.nrdhvnstd.halgventmod.client.MenuTabs;
import com.nrdhvnstd.halgventmod.network.LocationCapturePayload;
import com.nrdhvnstd.halgventmod.network.MapTeleportPayload;
import com.nrdhvnstd.halgventmod.network.RouteEditPayload;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;
import com.nrdhvnstd.halgventmod.point.MapRoute;
import com.nrdhvnstd.halgventmod.point.MapRoutes;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Техническая карта («Маппа») — отдельная вкладка, только у администрации (Permissions.isAdmin). Мир как есть: без обрезки и тумана,
 * со всеми снимками и тем, что администратор разведал сам.
 *
 * Сверху: поле «Название локации» — ввод отбирает локации этого измерения по подстроке (подсказки под полем),
 * Enter или клик — камера летит к локации и подгоняет масштаб под её границу; ◀ ▶ перебирают локации по алфавиту
 * по кругу. Справа от них — «Снять карту локации» (LocationMaps, с полоской хода) и «Как видит игрок»
 * (LocationMapScreen выбранной локации).
 * Снизу — пять инструментов: «Рука» (ПКМ по карте — телепорт сюда), «Карандаш», «Метка» (как у игрока, MapScreenBase),
 * «Точка» (по пустому — новая локация: граница на карте, PointBoundsEditor, потом окно точки; по точке — её окно;
 * Shift+ЛКМ по пустому — точка появления; ПКМ по месту — «Изменить границы») и «Маршрут» (RouteEditor).
 * Правка точек и маршрутов и снимок — только в творческом режиме (MapPoints.canEdit), без него они серые.
 * Справа — слои: карандаш, метки, точки, маршруты, НПС (пока пусто — НПС в моде нет), подписи биомов, отряд.
 * Открытая точка с описанием под «Рукой» кликается: слева — панель описания (PointInfoPanel).
 * Внизу над инструментами — биом и координаты под курсором.
 */
public class MapScreen extends MapScreenBase implements RouteEditor.Host {
    // Свечение под значком точки под курсором (заглушка)
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID,
            "textures/gui/map_point_glow.png");
    private static final int GLOW_TEXTURE = 32;
    private static final float GLOW_SCALE = 1.6F;  // ⚠️ от размера значка
    private static final float GLOW_ALPHA = 0.6F;  // ⚠️
    // Точка появления: свечение под значком — зелёное, название — тем же цветом
    private static final float SPAWN_GLOW_R = 0.45F;
    private static final float SPAWN_GLOW_G = 1.0F;
    private static final float SPAWN_GLOW_B = 0.5F;
    private static final int COLOR_SPAWN = 0xFF8CF09C;
    private static final int COLOR_UNKNOWN = 0xFF000000;
    private static final int COLOR_PROGRESS = 0xC0FFE8A0;

    // Верхняя строка
    private static final int ROW = 20;
    private static final int ARROW_WIDTH = 20;
    private static final int SEARCH_WIDTH = 160;
    private static final int CAPTURE_WIDTH = 150;
    private static final int PLAYER_VIEW_WIDTH = 120;
    private static final int GAP = 2;
    private static final int SUGGESTIONS = 8; // ⚠️
    private static final int SUGGESTION_ROW = 12;
    private static final float FLY_SECONDS = 0.4F; // ⚠️
    private static final int BOUNDS_GAP = 4;

    // Окно маршрута
    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 112;
    private static final int ROUTE_SWATCH = 16;

    private static final Collator NAMES = Collator.getInstance(Locale.forLanguageTag("ru"));

    // Помнится между открытиями: масштаб и выбранная локация
    private static float savedZoom = 2.0F;
    private static int selectedId;

    private boolean centered;
    @Nullable
    private PointInfoPanel infoPanel;
    @Nullable
    private MapPoint pressedPoint;
    /** Правка границы точки; null — обычная карта. */
    @Nullable
    private PointBoundsEditor bounds;
    private final BiomeLabels biomeLabels = new BiomeLabels();
    private final BiomeLabels.Projection projection = new BiomeLabels.Projection() {
        @Override
        public float x(double worldX) {
            return screenX(worldX);
        }

        @Override
        public float y(double worldZ) {
            return screenY(worldZ);
        }
    };
    private final RouteEditor routes = new RouteEditor(view, this);

    private EditBox search;
    private Button captureButton;
    private Button playerViewButton;
    private List<MapPoint> suggestions = List.of();

    // Пролёт камеры к выбранной локации
    private long flyStart = -1;
    private double flyFromX;
    private double flyFromZ;
    private float flyFromZoom;
    private double flyToX;
    private double flyToZ;
    private float flyToZoom;

    // Окно названия и цвета маршрута; null — закрыто
    @Nullable
    private MapRoute routeDialog;
    private EditBox routeName;
    private int routeColor;

    public MapScreen() {
        super(Component.translatable("gui.halgventmod.tech_map.title"));
    }

    @Override
    public MenuTab tab() {
        return MenuTab.MAPPA;
    }

    @Override
    protected void init() {
        if (!centered) {
            zoom = savedZoom;
            centerOnPlayer();
            centered = true;
        }
        super.init();
        if (bounds != null) {
            initBoundsButtons();
            infoPanel = null;
            return; // пока правится граница — без инструментов, слоёв и верхней строки
        }
        initTopBar();
        if (routeDialog != null) {
            initRouteDialog();
        }
        if (infoPanel != null) {
            placeInfoPanel();
        }
    }

    private boolean canEdit() {
        return MapPoints.canEdit(minecraft.player);
    }

    @Override
    protected List<Tool> tools() {
        return bounds != null ? List.of() : List.of(Tool.MOVE, Tool.DRAW, Tool.WAYPOINT, Tool.POINT, Tool.ROUTE);
    }

    @Override
    protected boolean toolEnabled(Tool each) {
        return each != Tool.POINT && each != Tool.ROUTE || canEdit();
    }

    @Override
    protected Component disabledHint(Tool each) {
        return Component.translatable("gui.halgventmod.tech_map.need_creative").withStyle(ChatFormatting.GRAY);
    }

    @Override
    protected List<MapLayer> layers() {
        return bounds != null ? List.of() : List.of(MapLayer.DRAWING, MapLayer.WAYPOINTS, MapLayer.POINTS,
                MapLayer.ROUTES, MapLayer.NPC, MapLayer.BIOMES, MapLayer.SQUAD);
    }

    @Override
    protected void selectTool(Tool selected) {
        routes.reset();
        super.selectTool(selected);
    }

    @Override
    protected int topReserved() {
        return MenuTabs.TAB_HEIGHT + 2 * MARGIN + ROW;
    }

    // ===== Верхняя строка: поиск, снимок, «как видит игрок» =====

    private void initTopBar() {
        int y = MenuTabs.TAB_HEIGHT + MARGIN;
        int x = MARGIN;
        addRenderableWidget(Button.builder(Component.literal("◀"), button -> step(-1))
                .bounds(x, y, ARROW_WIDTH, ROW).build());
        x += ARROW_WIDTH + GAP;
        String typed = search != null ? search.getValue() : selected() != null ? selected().name : "";
        search = new EditBox(font, x, y + 1, SEARCH_WIDTH, ROW - 2, Component.translatable("gui.halgventmod.tech_map.search"));
        search.setMaxLength(MapPoint.NAME_MAX);
        search.setHint(Component.translatable("gui.halgventmod.tech_map.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(typed);
        search.setResponder(text -> updateSuggestions());
        addRenderableWidget(search);
        x += SEARCH_WIDTH + GAP;
        addRenderableWidget(Button.builder(Component.literal("▶"), button -> step(1))
                .bounds(x, y, ARROW_WIDTH, ROW).build());
        x += ARROW_WIDTH + 4 * GAP;
        captureButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.tech_map.capture"),
                button -> capture()).bounds(x, y, CAPTURE_WIDTH, ROW).build());
        x += CAPTURE_WIDTH + GAP;
        playerViewButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.tech_map.player_view"),
                button -> playerView()).bounds(x, y, PLAYER_VIEW_WIDTH, ROW).build());
        updateTopButtons();
    }

    private void updateTopButtons() {
        MapPoint selected = selected();
        captureButton.active = selected != null && canEdit() && LocationMapCache.progress(selected.id) < 0;
        playerViewButton.active = selected != null;
    }

    /** Локации этого измерения по алфавиту. */
    private static List<MapPoint> locations() {
        List<MapPoint> result = new ArrayList<>();
        for (MapPoint point : PointShapes.here()) {
            if (!point.isSpawn()) {
                result.add(point);
            }
        }
        result.sort(Comparator.comparing(point -> point.name, NAMES));
        return result;
    }

    @Nullable
    private static MapPoint selected() {
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.id == selectedId && !point.isSpawn()) {
                return point;
            }
        }
        return null;
    }

    private void updateSuggestions() {
        String text = search.getValue().strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || selected() != null && selected().name.equalsIgnoreCase(search.getValue())) {
            suggestions = List.of();
            return;
        }
        List<MapPoint> found = new ArrayList<>();
        for (MapPoint point : locations()) {
            if (point.name.toLowerCase(Locale.ROOT).contains(text) && found.size() < SUGGESTIONS) {
                found.add(point);
            }
        }
        suggestions = found;
    }

    // ◀ ▶ — соседняя по алфавиту, по кругу
    private void step(int direction) {
        List<MapPoint> all = locations();
        if (all.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == selectedId) {
                index = i;
            }
        }
        int next = index < 0 ? (direction > 0 ? 0 : all.size() - 1) : Math.floorMod(index + direction, all.size());
        select(all.get(next));
    }

    private void select(MapPoint point) {
        selectedId = point.id;
        search.setValue(point.name);
        search.setFocused(false);
        setFocused(null);
        suggestions = List.of();
        updateTopButtons();
        flyTo(point);
    }

    // Камера летит к локации, масштаб — чтобы граница целиком поместилась
    private void flyTo(MapPoint point) {
        int[] box = point.bounds();
        float freeWidth = width - 2 * MARGIN;
        float freeHeight = height - topReserved() - TOOL_SIZE - 3 * MARGIN - 2 * TEXT_ROW;
        float fit = Math.min(freeWidth / (box[2] - box[0] + 1), freeHeight / (box[3] - box[1] + 1)) * 0.9F;
        double scale = minecraft.getWindow().getGuiScale();
        double real = fit * scale;
        if (real < 1) {
            real = Math.max(MIN_REAL_ZOOM, Math.pow(2, Math.floor(Math.log(real) / Math.log(2))));
        }
        flyFromX = centerX;
        flyFromZ = centerZ;
        flyFromZoom = zoom;
        flyToX = (box[0] + box[2] + 1) / 2.0;
        flyToZ = (box[1] + box[3] + 1) / 2.0;
        flyToZoom = (float) Mth.clamp(real / scale, minZoom(), MAX_ZOOM);
        flyStart = Util.getMillis();
    }

    private void updateFlight() {
        if (flyStart < 0) {
            return;
        }
        float t = Math.min(1, (Util.getMillis() - flyStart) / (FLY_SECONDS * 1000));
        float eased = t * t * (3 - 2 * t);
        centerX = Mth.lerp(eased, flyFromX, flyToX);
        centerZ = Mth.lerp(eased, flyFromZ, flyToZ);
        // Масштаб — по логарифму, чтобы пролёт шёл равномерно
        zoom = (float) Math.exp(Mth.lerp(eased, Math.log(flyFromZoom), Math.log(flyToZoom)));
        if (t >= 1) {
            flyStart = -1;
            zoom = flyToZoom;
        }
    }

    private void capture() {
        MapPoint selected = selected();
        if (selected != null && canEdit()) {
            PacketDistributor.sendToServer(new LocationCapturePayload(selected.id));
            LocationMapCache.captureProgress(selected.id, 0); // полоска — сразу
            updateTopButtons();
        }
    }

    private void playerView() {
        MapPoint selected = selected();
        if (selected != null) {
            minecraft.setScreen(new LocationMapScreen(selected.id, this));
        }
    }

    private int suggestionTop() {
        return search.getY() + search.getHeight() + 1;
    }

    private int suggestionAt(double mouseX, double mouseY) {
        if (suggestions.isEmpty() || !search.isFocused() || mouseX < search.getX()
                || mouseX >= search.getX() + SEARCH_WIDTH || mouseY < suggestionTop()) {
            return -1;
        }
        int index = (int) (mouseY - suggestionTop()) / SUGGESTION_ROW;
        return index < suggestions.size() ? index : -1;
    }

    private void renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        if (suggestions.isEmpty() || !search.isFocused()) {
            return;
        }
        int top = suggestionTop();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        panel(graphics, search.getX(), top, SEARCH_WIDTH, suggestions.size() * SUGGESTION_ROW);
        int hovered = suggestionAt(mouseX, mouseY);
        for (int i = 0; i < suggestions.size(); i++) {
            int y = top + i * SUGGESTION_ROW;
            if (i == hovered) {
                graphics.fill(search.getX(), y, search.getX() + SEARCH_WIDTH, y + SUGGESTION_ROW, COLOR_FRAME);
            }
            graphics.drawString(font, suggestions.get(i).name, search.getX() + 4, y + 2,
                    i == hovered ? COLOR_TITLE : COLOR_TEXT, false);
        }
        graphics.pose().popPose();
    }

    // ===== Граница точки =====

    // Вверху по середине: «Круг», «Многоугольник» (выбранная фигура — неактивна), «Далее», «Отмена»
    private void initBoundsButtons() {
        Component[] titles = {
                Component.translatable("gui.halgventmod.point.shape.circle"),
                Component.translatable("gui.halgventmod.point.shape.polygon"),
                Component.translatable("gui.halgventmod.point.next"),
                CommonComponents.GUI_CANCEL};
        Runnable[] actions = {
                () -> setBoundsShape(MapPoint.Shape.CIRCLE),
                () -> setBoundsShape(MapPoint.Shape.POLYGON),
                this::boundsNext,
                this::boundsCancel};
        int[] widths = new int[titles.length];
        int total = (titles.length - 1) * BOUNDS_GAP;
        for (int i = 0; i < titles.length; i++) {
            widths[i] = font.width(titles[i]) + 16;
            total += widths[i];
        }
        int x = (width - total) / 2;
        int y = MenuTabs.TAB_HEIGHT + MARGIN;
        for (int i = 0; i < titles.length; i++) {
            Runnable action = actions[i];
            Button button = addRenderableWidget(Button.builder(titles[i], b -> action.run())
                    .bounds(x, y, widths[i], ROW).build());
            if (i < 2) {
                button.active = bounds.point().shape != MapPoint.Shape.values()[i];
            }
            x += widths[i] + BOUNDS_GAP;
        }
    }

    // Новая точка: круг вокруг места клика, дальше — граница на карте
    private void startPoint(int x, int z) {
        MapPoint point = new MapPoint();
        point.x = x;
        point.y = standingY(x, z);
        point.z = z;
        startBounds(point);
    }

    private void startBounds(MapPoint draft) {
        bounds = new PointBoundsEditor(view, draft);
        rebuildWidgets();
    }

    private void setBoundsShape(MapPoint.Shape shape) {
        bounds.setShape(shape);
        rebuildWidgets();
    }

    // «Далее» — окно точки; там «Сохранить» или «Удалить» закончит правку границы, «Отмена» вернёт к ней
    private void boundsNext() {
        minecraft.setScreen(new PointEditScreen(this, bounds.point(), () -> bounds = null));
    }

    private void boundsCancel() {
        bounds = null;
        rebuildWidgets();
    }

    private boolean editingBounds(MapPoint point) {
        return bounds != null && bounds.point().id != 0 && bounds.point().id == point.id;
    }

    // ===== Панель описания точки =====

    // Слева, под верхней строкой; правую панель слоёв не перекрывает
    private void placeInfoPanel() {
        int top = topReserved() + MARGIN;
        infoPanel.place(MARGIN, top, height - 2 * MARGIN - TOOL_SIZE - 2 * TEXT_ROW, width - 2 * MARGIN - layersWidth(), width);
    }

    private void openInfo(MapPoint point) {
        if (infoPanel == null || infoPanel.pointId() != point.id) {
            infoPanel = new PointInfoPanel(point.id);
            placeInfoPanel();
        }
    }

    /** Открытая точка с описанием — кликается. */
    private static boolean clickable(MapPoint point) {
        return point != null && point.described && PointShapes.discovered(point);
    }

    @Override
    protected boolean overMap(double mouseX, double mouseY) {
        if (infoPanel != null && infoPanel.contains(mouseX, mouseY) || routeDialog != null && overDialog(mouseX, mouseY)) {
            return false;
        }
        if (bounds != null) {
            return mouseY >= topReserved(); // инструментов и слоёв сейчас нет
        }
        return super.overMap(mouseX, mouseY);
    }

    // ===== Окно маршрута =====

    @Override
    public void openDialog(MapRoute draft) {
        routeDialog = draft;
        routeColor = draft.color;
        routeName = null;
        rebuildWidgets();
    }

    @Override
    public void confirmDelete(MapRoute route) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                PacketDistributor.sendToServer(new RouteEditPayload(MapRoutes.Action.DELETE, route));
                routes.reset();
            }
            minecraft.setScreen(this);
        }, Component.translatable("gui.halgventmod.route.delete"),
                Component.translatable("gui.halgventmod.route.delete_confirm", route.name)));
    }

    private int dialogLeft() {
        return (width - DIALOG_WIDTH) / 2;
    }

    private int dialogTop() {
        return (height - DIALOG_HEIGHT) / 2;
    }

    private boolean overDialog(double mouseX, double mouseY) {
        return mouseX >= dialogLeft() && mouseX < dialogLeft() + DIALOG_WIDTH && mouseY >= dialogTop()
                && mouseY < dialogTop() + DIALOG_HEIGHT;
    }

    private void initRouteDialog() {
        int x = dialogLeft() + 8;
        int w = DIALOG_WIDTH - 16;
        String name = routeName != null ? routeName.getValue() : routeDialog.name;
        routeName = new EditBox(font, x, dialogTop() + 30, w, 18, Component.translatable("gui.halgventmod.route.name"));
        routeName.setMaxLength(MapRoute.NAME_MAX);
        routeName.setValue(name);
        addRenderableWidget(routeName);
        setFocused(routeName);
        int half = (w - 4) / 2;
        int y = dialogTop() + DIALOG_HEIGHT - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.waypoint.save"), button -> saveRoute())
                .bounds(x, y, half, ROW).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> closeDialog())
                .bounds(x + half + 4, y, half, ROW).build());
    }

    private void saveRoute() {
        routeDialog.name = routeName.getValue().strip();
        routeDialog.color = routeColor;
        PacketDistributor.sendToServer(new RouteEditPayload(routeDialog.id == 0 ? MapRoutes.Action.CREATE
                : MapRoutes.Action.UPDATE, routeDialog));
        closeDialog();
    }

    private void closeDialog() {
        routeDialog = null;
        routeName = null;
        setFocused(null);
        rebuildWidgets();
    }

    private int swatchX(int index) {
        return dialogLeft() + 8 + index * (ROUTE_SWATCH + 4);
    }

    private int swatchY() {
        return dialogTop() + 62;
    }

    private void renderDialog(GuiGraphics graphics) {
        panel(graphics, dialogLeft(), dialogTop(), DIALOG_WIDTH, DIALOG_HEIGHT);
        graphics.drawCenteredString(font, Component.translatable(routeDialog.id == 0 ? "gui.halgventmod.route.new"
                : "gui.halgventmod.route.edit"), width / 2, dialogTop() + 6, COLOR_TITLE);
        graphics.drawString(font, Component.translatable("gui.halgventmod.route.name"), dialogLeft() + 8,
                dialogTop() + 20, COLOR_DIM, false);
        graphics.drawString(font, Component.translatable("gui.halgventmod.route.color"), dialogLeft() + 8,
                swatchY() - 10, COLOR_DIM, false);
        for (int i = 0; i < RouteEditor.COLORS.length; i++) {
            int x = swatchX(i);
            int y = swatchY();
            graphics.fill(x - 1, y - 1, x + ROUTE_SWATCH + 1, y + ROUTE_SWATCH + 1,
                    RouteEditor.COLORS[i] == routeColor ? 0xFFFFFFFF : 0xFF000000);
            graphics.fill(x, y, x + ROUTE_SWATCH, y + ROUTE_SWATCH, RouteEditor.COLORS[i]);
        }
    }

    // ===== Рисование =====

    @Override
    protected void renderWorld(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateFlight();
        graphics.fill(0, 0, width, height, COLOR_UNKNOWN);
        TileRenderer.renderTiles(graphics, view, 0, 0, width, height);
        if (MapLayer.BIOMES.shown()) {
            biomeLabels.update(worldX(0), worldZ(0), worldX(width), worldZ(height));
            biomeLabels.render(graphics, font, projection, zoom);
        }
        if (MapLayer.DRAWING.shown()) {
            TileRenderer.renderDrawings(graphics, view, 0, 0, width, height);
        }
        if (MapLayer.POINTS.shown()) {
            renderPointAreas(graphics);
        }
        // Маршруты: слой прячет готовые; прокладываемый виден всегда
        routes.render(graphics, font, dimension(), mouseX, mouseY, MapLayer.ROUTES.shown());
        // Слой НПС — пока пустой: НПС в моде ещё нет
        if (MapLayer.POINTS.shown()) {
            renderPointIcons(graphics, mouseX, mouseY);
        }
        if (bounds != null) {
            bounds.render(graphics, ICON_SIZE);
        }
    }

    private String dimension() {
        return minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (infoPanel != null) {
            MapPoint point = infoPanel.point();
            if (point == null) {
                infoPanel = null; // точку удалили или сняли «с описанием»
            } else {
                infoPanel.render(graphics, font, point, mouseX, mouseY);
            }
        }
        if (routeDialog != null) {
            renderDialog(graphics); // поле и кнопки окна рисуются поверх
        }
    }

    // Точки: граница — только администрации в творческом режиме. Неоткрытая — серая, открытая — светлая.
    // Точку, у которой сейчас правится граница, рисует сам редактор
    private void renderPointAreas(GuiGraphics graphics) {
        if (!canEdit()) {
            return;
        }
        RenderSystem.enableBlend();
        for (MapPoint point : PointShapes.here()) {
            if (!point.isSpawn() && !editingBounds(point)) { // у точки появления границы нет
                boolean open = PointShapes.discovered(point);
                PointShapes.draw(graphics, view, point, open ? PointShapes.FILL_OPEN : PointShapes.FILL_HIDDEN,
                        open ? PointShapes.LINE_OPEN : PointShapes.LINE_HIDDEN);
            }
        }
        RenderSystem.disableBlend();
    }

    // Под курсором неоткрытая точка и открытая с описанием подсвечиваются: свечение под значком, «?» — цветом заголовка
    private void renderPointIcons(GuiGraphics graphics, int mouseX, int mouseY) {
        MapPoint hovered = hoveredPoint(mouseX, mouseY);
        RenderSystem.enableBlend();
        for (MapPoint point : PointShapes.here()) {
            if (editingBounds(point)) {
                continue;
            }
            int x = Math.round(screenX(point.x + 0.5));
            int y = Math.round(screenY(point.z + 0.5));
            if (point.isSpawn()) {
                renderSpawnPoint(graphics, point, x, y);
                continue;
            }
            boolean open = PointShapes.discovered(point);
            boolean glow = point == hovered && (!open || point.described) || point.id == selectedId;
            if (glow) {
                int size = Math.round(ICON_SIZE * GLOW_SCALE);
                graphics.setColor(1, 1, 1, GLOW_ALPHA);
                graphics.blit(GLOW, x - size / 2, y - size / 2, size, size, 0, 0, GLOW_TEXTURE, GLOW_TEXTURE,
                        GLOW_TEXTURE, GLOW_TEXTURE);
                graphics.setColor(1, 1, 1, 1);
            }
            if (open) {
                MapIcon.draw(graphics, PointShapes.cell(point), x - ICON_SIZE / 2, y - ICON_SIZE / 2, ICON_SIZE);
            } else {
                PointShapes.drawHidden(graphics, font, point, x, y, ICON_SIZE, glow ? COLOR_TITLE : PointShapes.COLOR_UNKNOWN);
            }
        }
        RenderSystem.disableBlend();
    }

    /** Точка появления: значок «место сбора» на зелёном свечении и название под ним. */
    private void renderSpawnPoint(GuiGraphics graphics, MapPoint point, int x, int y) {
        int size = Math.round(ICON_SIZE * GLOW_SCALE);
        graphics.setColor(SPAWN_GLOW_R, SPAWN_GLOW_G, SPAWN_GLOW_B, GLOW_ALPHA);
        graphics.blit(GLOW, x - size / 2, y - size / 2, size, size, 0, 0, GLOW_TEXTURE, GLOW_TEXTURE,
                GLOW_TEXTURE, GLOW_TEXTURE);
        graphics.setColor(1, 1, 1, 1);
        MapIcon.draw(graphics, PointShapes.SPAWN_ICON.cell(PointShapes.dye(point)), x - ICON_SIZE / 2, y - ICON_SIZE / 2,
                ICON_SIZE);
        graphics.drawCenteredString(font, point.name, x, y + ICON_SIZE / 2 + 1, COLOR_SPAWN);
    }

    /** Точка под курсором, если сейчас её можно навести. */
    @Nullable
    private MapPoint hoveredPoint(double mouseX, double mouseY) {
        return bounds == null && routeDialog == null && MapLayer.POINTS.shown() && overMap(mouseX, mouseY)
                ? pointAt(mouseX, mouseY) : null;
    }

    /** Точка под курсором — по значку (ближайшая, если несколько). */
    @Nullable
    private MapPoint pointAt(double mouseX, double mouseY) {
        if (!MapLayer.POINTS.shown()) {
            return null;
        }
        MapPoint found = null;
        double best = (ICON_SIZE / 2.0) * (ICON_SIZE / 2.0);
        for (MapPoint point : PointShapes.here()) {
            double dx = screenX(point.x + 0.5) - mouseX;
            double dy = screenY(point.z + 0.5) - mouseY;
            if (dx * dx + dy * dy <= best) {
                best = dx * dx + dy * dy;
                found = point;
            }
        }
        return found;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (captureButton != null && bounds == null) {
            updateTopButtons();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        // Полоска хода снимка — поверх кнопки
        MapPoint selected = selected();
        int progress = selected == null ? -1 : LocationMapCache.progress(selected.id);
        if (progress >= 0 && captureButton != null && bounds == null) {
            int x = captureButton.getX() + 2;
            int y = captureButton.getY() + captureButton.getHeight() - 4;
            int w = captureButton.getWidth() - 4;
            graphics.fill(x, y, x + w, y + 2, 0xFF000000);
            graphics.fill(x, y, x + w * progress / 100, y + 2, COLOR_PROGRESS);
        }
        // Над кнопками инструментов: биом под курсором (если там разведано), под ним — координаты
        int blockX = Mth.floor(worldX(mouseX));
        int blockZ = Mth.floor(worldZ(mouseY));
        int coordsY = toolsTop() - TEXT_ROW;
        ResourceLocation biome = overMap(mouseX, mouseY) ? WorldMap.biome(WorldMap.biomeAt(blockX, blockZ)) : null;
        if (biome != null) {
            graphics.drawCenteredString(font, Component.translatable(Util.makeDescriptionId("biome", biome)),
                    width / 2, coordsY - TEXT_ROW, COLOR_TITLE);
        }
        graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.map.cursor", blockX, blockZ),
                width / 2, coordsY, COLOR_TEXT);

        if (bounds != null) {
            // Под кнопками правки границы — подсказка по фигуре
            String hint = bounds.point().shape == MapPoint.Shape.CIRCLE
                    ? "gui.halgventmod.point.hint.circle" : "gui.halgventmod.point.hint.polygon";
            graphics.drawCenteredString(font, Component.translatable(hint), width / 2, topReserved() + MARGIN, COLOR_TEXT);
            return;
        }
        if (routeDialog != null) {
            return;
        }
        if (tool == Tool.ROUTE && routes.drawing()) {
            graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.route.hint"), width / 2,
                    topReserved() + MARGIN, COLOR_TEXT);
        }
        renderSuggestions(graphics, mouseX, mouseY);
        MapPoint hoveredPoint = hoveredPoint(mouseX, mouseY);
        if (hoveredPoint != null && minecraft.player != null && popup == null) {
            renderPointTooltip(graphics, hoveredPoint, mouseX, mouseY);
        }
    }

    private void renderPointTooltip(GuiGraphics graphics, MapPoint point, int mouseX, int mouseY) {
        int distance = Mth.floor(Math.sqrt(minecraft.player.distanceToSqr(point.x + 0.5, point.y, point.z + 0.5)));
        boolean open = PointShapes.discovered(point);
        Component coords = Component.translatable("gui.halgventmod.waypoint.coords", point.x, point.y, point.z);
        Component away = Component.translatable("gui.halgventmod.waypoint.distance", distance);
        if (point.isSpawn()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.literal(point.name),
                    Component.translatable("gui.halgventmod.spawn.kind").withStyle(ChatFormatting.GREEN),
                    coords.copy().withStyle(ChatFormatting.GRAY),
                    away.copy().withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.halgventmod.spawn.visibility").withStyle(ChatFormatting.DARK_GRAY)),
                    mouseX, mouseY);
            return;
        }
        if (!open) {
            // Неоткрытая: имя и «не открыта» — чтобы проверить текст для игроков
            List<Component> dim = new ArrayList<>();
            dim.add(Component.translatable("gui.halgventmod.point.hidden"));
            dim.add(coords);
            dim.add(away);
            MapTooltipBox.render(graphics, font, Component.literal(point.name), Component.literal(point.hoverText), dim,
                    mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(point.name));
        lines.add(Component.translatable("gui.halgventmod.point.open").withStyle(ChatFormatting.WHITE));
        lines.add(coords.copy().withStyle(ChatFormatting.GRAY));
        lines.add(away.copy().withStyle(ChatFormatting.GRAY));
        if (point.described) {
            lines.add(Component.translatable("gui.halgventmod.point.click_description").withStyle(ChatFormatting.DARK_GRAY));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ===== Управление =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Окно маршрута: цвет, поле, кнопки; мимо — ничего
        if (routeDialog != null) {
            for (int i = 0; i < RouteEditor.COLORS.length; i++) {
                int x = swatchX(i);
                if (mouseX >= x && mouseX < x + ROUTE_SWATCH && mouseY >= swatchY() && mouseY < swatchY() + ROUTE_SWATCH) {
                    routeColor = RouteEditor.COLORS[i];
                    return true;
                }
            }
            if (overDialog(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }
        int suggestion = suggestionAt(mouseX, mouseY);
        if (suggestion >= 0) {
            select(suggestions.get(suggestion));
            return true;
        }
        // Панель описания: [×] закрывает, остальные клики по ней карте не достаются
        if (infoPanel != null && infoPanel.contains(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && infoPanel.overClose(mouseX, mouseY)) {
                infoPanel = null;
            }
            return true;
        }
        if (search != null && search.isFocused() && !search.isMouseOver(mouseX, mouseY)) {
            search.setFocused(false); // клик мимо поля поиска — подсказки прячутся
        }
        flyStart = -1;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean mapPressed(double mouseX, double mouseY, int button) {
        if (bounds != null) {
            // Ручки и вершины — редактору; мимо них левой кнопкой карта двигается
            bounds.mousePressed(mouseX, mouseY, button, ICON_SIZE);
            return true;
        }
        if (tool == Tool.ROUTE && canEdit()) {
            return routes.pressed(mouseX, mouseY, button);
        }
        if (tool == Tool.MOVE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MapPoint point = pointAt(mouseX, mouseY);
            pressedPoint = clickable(point) ? point : null;
        }
        return false;
    }

    @Override
    protected boolean mapDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (bounds != null) {
            if (bounds.dragging()) {
                bounds.mouseDragged(mouseX, mouseY);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                centerX -= dragX / zoom;
                centerZ -= dragY / zoom;
            }
            return true;
        }
        return tool == Tool.ROUTE && routes.dragged(mouseX, mouseY);
    }

    @Override
    protected void mapReleased(double mouseX, double mouseY, int button) {
        if (bounds != null) {
            bounds.mouseReleased();
        }
        routes.released();
    }

    @Override
    protected boolean mapClicked(double mouseX, double mouseY, int button) {
        if (bounds != null) {
            return true;
        }
        int x = Mth.floor(worldX(mouseX));
        int z = Mth.floor(worldZ(mouseY));
        switch (tool) {
            case MOVE -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressedPoint != null) {
                    openInfo(pressedPoint);
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && minecraft.player != null
                        && minecraft.player.hasPermissions(MapTeleportPayload.PERMISSION_LEVEL)) {
                    teleport(x, z);
                }
                pressedPoint = null;
                return true;
            }
            case POINT -> {
                if (!canEdit()) {
                    return true;
                }
                MapPoint point = pointAt(mouseX, mouseY);
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (point != null && point.isSpawn()) {
                        minecraft.setScreen(new SpawnPointEditScreen(this, point.copy()));
                    } else if (point != null) {
                        minecraft.setScreen(new PointEditScreen(this, point.copy(), () -> {}));
                    } else if (hasShiftDown()) {
                        minecraft.setScreen(SpawnPointEditScreen.create(this, x, standingY(x, z), z));
                    } else {
                        startPoint(x, z);
                    }
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && point != null && !point.isSpawn()) {
                    startBounds(point.copy());
                }
                return true;
            }
            case ROUTE -> {
                if (canEdit()) {
                    routes.clicked(mouseX, mouseY, button);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void teleport(int x, int z) {
        Integer surface = WorldMap.surfaceHeight(x, z);
        short mapped = WorldMap.loadedHeight(x, z);
        int hint = surface != null ? surface
                : mapped != MapRegion.NO_HEIGHT ? mapped + 1 : MapTeleportPayload.NO_HINT;
        PacketDistributor.sendToServer(new MapTeleportPayload(x, z, hint));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (infoPanel != null && infoPanel.contains(mouseX, mouseY)) {
            infoPanel.scroll(mouseX, mouseY, scrollY);
            return true;
        }
        // Граница-круг: колесико — радиус; Shift+колесико — как обычно, масштаб карты
        if (bounds != null && !hasShiftDown() && bounds.scroll(scrollY)) {
            return true;
        }
        if (bounds != null) {
            zoomAt(mouseX, mouseY, scrollY);
            return true;
        }
        flyStart = -1;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (routeDialog != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                saveRoute();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeDialog();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (search != null && search.isFocused()) {
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !suggestions.isEmpty()) {
                select(suggestions.get(0));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // Первый Esc закрывает панель описания, второй — карту
        if (infoPanel != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            infoPanel = null;
            return true;
        }
        // Правка границы: Esc — отмена, Enter — дальше, стрелки — радиус или вершина
        if (bounds != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                boundsCancel();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                boundsNext();
                return true;
            }
            if (bounds.keyPressed(keyCode)) {
                return true;
            }
        }
        if (tool == Tool.ROUTE && routes.keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        savedZoom = zoom;
        super.removed();
    }
}
