package com.nrdhvnstd.halgventmod.client.map;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.client.MenuTab;
import com.nrdhvnstd.halgventmod.client.MenuTabButton;
import com.nrdhvnstd.halgventmod.client.MenuTabScreen;
import com.nrdhvnstd.halgventmod.client.MenuTabs;
import com.nrdhvnstd.halgventmod.network.SquadMembersPayload;
import com.nrdhvnstd.halgventmod.squad.SquadMarkers;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Общее у окон карты мира (карта локации, техническая карта): камера над настоящим миром (центр и масштаб),
 * вкладки сверху, инструменты внизу по середине, слои справа, игрок и отряд, метки и карандаш.
 *
 * Инструменты: ЛКМ по иконке — выбрать, ПКМ — над иконкой всплывает панель его настроек (закрывается кликом мимо
 * или Esc). Правая панель — только слои (MapLayer).
 * «Рука» — тащить карту, колёсико — масштаб к курсору, наведение на метку — подсказка.
 * «Карандаш» — ЛКМ рисует, ПКМ стирает, средняя тащит карту, Ctrl+Z — отменить штрих (MapDrawing).
 * «Метка» — ЛКМ по карте сразу ставит метку выбранного в настройках типа; под ней поле названия: Enter — готово
 * (пустое — метка без названия), Esc — метка не ставится. Двойной клик по метке — то же поле для переименования,
 * ПКМ по метке — удалить.
 * Колёсико — масштаб: вблизи шаги плавные, при отдалении — ровно вдвое (пиксель тайла уровня отдаления ложится
 * на пиксель монитора). Пробел — к себе.
 */
abstract class MapScreenBase extends Screen implements MenuTabScreen {
    // Масштаб — в пикселях интерфейса на блок; «настоящий» — в пикселях монитора
    protected static final float MIN_REAL_ZOOM = 1.0F / (1 << MapPyramid.MAX_LEVEL);
    protected static final float MAX_ZOOM = 16.0F;
    private static final float ZOOM_STEP = 1.25F;
    protected static final int ICON_SIZE = 16;
    protected static final int CLICK_SLOP = 3; // ⚠️ сдвиг мыши, при котором нажатие ещё клик, а не перетаскивание
    private static final long DOUBLE_CLICK_MILLIS = 300;

    protected static final int MARGIN = 4;
    protected static final int TOOL_SIZE = 20;
    private static final int TOOL_GAP = 2;
    protected static final int TEXT_ROW = 11;

    // Слои справа: только галочки, название — подсказкой при наведении
    private static final int LAYER_BOX = 17;  // сторона ванильной галочки
    private static final int LAYER_ROW = 20;
    private static final int PANEL_PAD = 5;

    // Всплывающая панель настроек инструмента
    private static final int POPUP_GAP = 4;
    private static final int SWATCH = 16;
    private static final int SLIDER_HEIGHT = 14;
    private static final int MAX_BRUSH = 12;
    private static final int ICON_CELL = 17;
    private static final int ICONS_PER_ROW = 9;
    private static final int DYE_SWATCH = 11;
    private static final int DYE_STEP = 13;
    private static final int DYES_PER_ROW = 8;

    // Поле названия метки
    private static final int NAME_FIELD_WIDTH = 100; // ⚠️

    // Товарищи по отряду: курсор, окрашенный в зелёный, и имя тем же цветом
    private static final float SQUAD_TINT_R = 0.45F;
    private static final float SQUAD_TINT_G = 1.0F;
    private static final float SQUAD_TINT_B = 0.5F;
    private static final int COLOR_SQUAD = 0xFF7CFC8C;

    protected static final int COLOR_PANEL = 0xF0241C14;
    protected static final int COLOR_FRAME = 0xFF6E6250;
    protected static final int COLOR_TEXT = 0xFFE8DCC0;
    protected static final int COLOR_DIM = 0xFFB8A888;
    protected static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_BRUSH = 0xC0FFFFFF;
    private static final int COLOR_HOVER = 0xFF6E6250;
    private static final int COLOR_CELL = 0x60000000;

    /** Цвета карандаша: чернила, киноварь, синий, коричневый, зелёный, белила. */
    static final int[] INKS = {0x1E1A16, 0xB3261E, 0x2B4C9B, 0x6B4423, 0x2E6B2E, 0xE8DCC0};

    /** Инструменты нижней панели; у каждого окна — свой набор. */
    protected enum Tool {
        MOVE(Items.COMPASS, false),
        DRAW(Items.FEATHER, true),
        WAYPOINT(Items.RED_BANNER, true),
        POINT(Items.LODESTONE, false),
        ROUTE(Items.LEAD, false);

        final ItemStack icon;
        final boolean settings;

        Tool(Item icon, boolean settings) {
            this.icon = new ItemStack(icon);
            this.settings = settings;
        }

        Component title() {
            return Component.translatable("gui.halgventmod.map.tool." + name().toLowerCase(Locale.ROOT));
        }
    }

    // Помнится между открытиями: кисть и «тип» новых меток
    private static int ink = 1;
    private static int brushSize = 3;
    private static MapIcon waypointIcon = MapIcon.FLAG;
    private static DyeColor waypointDye = DyeColor.RED;
    private static boolean waypointCompass = true;

    protected double centerX;
    protected double centerZ;
    protected float zoom = 2.0F;
    protected Tool tool = Tool.MOVE;
    /** Инструмент, чьи настройки всплыли над иконкой; null — нет. */
    @Nullable
    protected Tool popup;
    private int popupLeft;
    private int popupTop;
    private int popupWidth;
    private int popupHeight;

    /** Кнопка, нажатая на самой карте; -1 — никакая. */
    protected int pressedButton = -1;
    protected double pressX;
    protected double pressY;
    protected boolean dragged;
    private double lastDrawX;
    private double lastDrawZ;

    // Метка, которой сейчас вводят название: новая (ещё не в списке) или переименование
    @Nullable
    private Waypoint naming;
    private boolean namingNew;
    private EditBox nameField;
    @Nullable
    private Waypoint lastClickedWaypoint;
    private long lastClickMillis;

    protected final MapView view = new MapView() {
        @Override
        public float screenX(double worldX) {
            return MapScreenBase.this.screenX(worldX);
        }

        @Override
        public float screenY(double worldZ) {
            return MapScreenBase.this.screenY(worldZ);
        }

        @Override
        public double worldX(double screenX) {
            return MapScreenBase.this.worldX(screenX);
        }

        @Override
        public double worldZ(double screenY) {
            return MapScreenBase.this.worldZ(screenY);
        }

        @Override
        public float zoom() {
            return zoom;
        }
    };

    protected MapScreenBase(Component title) {
        super(title);
    }

    @Override
    public MenuTab tab() {
        return MenuTab.MAP;
    }

    // ===== Что задаёт окно =====

    /** Инструменты внизу, слева направо. */
    protected abstract List<Tool> tools();

    /** Можно ли сейчас пользоваться инструментом (иначе он серый). */
    protected boolean toolEnabled(Tool each) {
        return true;
    }

    /** Подсказка серого инструмента. */
    protected Component disabledHint(Tool each) {
        return Component.empty();
    }

    /** Галочки справа. */
    protected abstract List<MapLayer> layers();

    /** Мир под картой: тайлы, листы, точки — всё, что ниже меток, отряда и игрока. */
    protected abstract void renderWorld(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** Поверх меток и игроков (рамки правки, панели окна). */
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    /** Сколько места сверху занимает окно (вкладки и своя строка). */
    protected int topReserved() {
        return MenuTabs.TAB_HEIGHT;
    }

    /** Можно ли рисовать карандашом и ставить метку в этом месте мира. */
    protected boolean editableAt(double worldX, double worldZ) {
        return true;
    }

    /** Наименьший масштаб (пикселей интерфейса на блок). */
    protected float minZoom() {
        return MIN_REAL_ZOOM / (float) minecraft.getWindow().getGuiScale();
    }

    /** Отдалили дальше наименьшего масштаба. true — окно что-то сделало (например, ушло на глобальную карту). */
    protected boolean zoomOutBeyond() {
        return false;
    }

    /** Камера сдвинулась или масштаб сменился — окно может её ограничить. */
    protected void clampCamera() {}

    /** Нажатие на карте инструментом окна (POINT, ROUTE) или рукой; true — обработано. */
    protected boolean mapPressed(double mouseX, double mouseY, int button) {
        return false;
    }

    /** Клик (нажали и отпустили почти на месте) по карте; true — обработано. */
    protected boolean mapClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /** Видна ли метка в этом окне (карта локации — только на листе). */
    protected boolean waypointShown(Waypoint waypoint) {
        return true;
    }

    /** Рисовать ли игрока (карта локации — только в своей локации). */
    protected boolean showPlayer() {
        return true;
    }

    /** Курсор над картой, а не над панелями окна. */
    protected boolean overMap(double mouseX, double mouseY) {
        if (mouseY < topReserved() || overTools(mouseX, mouseY) || overLayers(mouseX, mouseY)) {
            return false;
        }
        return !(popup != null && overPopup(mouseX, mouseY));
    }

    // ===== Окно =====

    @Override
    protected void init() {
        snapZoom();
        clampCamera();
        for (MenuTabButton button : MenuTabs.createButtons(font, 0, width, tab())) {
            addRenderableWidget(button);
        }
        int toolX = toolsLeft();
        for (Tool each : tools()) {
            MapWidgets.ToolButton button = addRenderableWidget(new MapWidgets.ToolButton(toolX, toolsTop(), TOOL_SIZE,
                    each.icon, each.title(), each == tool, () -> selectTool(each)));
            if (!toolEnabled(each)) {
                button.active = false;
                button.setDisabledHint(disabledHint(each));
            }
            toolX += TOOL_SIZE + TOOL_GAP;
        }
        initLayers();
        if (popup != null) {
            initPopup();
        }
        String name = nameField != null ? nameField.getValue() : "";
        nameField = new EditBox(font, 0, 0, NAME_FIELD_WIDTH, 14, Component.translatable("gui.halgventmod.waypoint.name"));
        nameField.setMaxLength(32);
        nameField.setValue(name);
        nameField.visible = naming != null;
        addRenderableWidget(nameField);
        if (naming != null) {
            setFocused(nameField);
        }
    }

    protected void selectTool(Tool selected) {
        if (!toolEnabled(selected)) {
            return;
        }
        finishNaming(true);
        tool = selected;
        popup = null;
        rebuildWidgets();
    }

    protected void centerOnPlayer() {
        LocalPlayer player = minecraft.player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
            clampCamera();
        }
    }

    // ===== Слои =====

    private void initLayers() {
        int y = layersTop() + PANEL_PAD;
        for (MapLayer layer : layers()) {
            Checkbox box = addRenderableWidget(Checkbox.builder(Component.empty(), font)
                    .pos(layersLeft() + PANEL_PAD, y)
                    .selected(layer.shown())
                    .onValueChange((checkbox, shown) -> layer.setShown(shown))
                    .build());
            box.setTooltip(Tooltip.create(layer.title()));
            y += LAYER_ROW;
        }
    }

    /** Ширина панели слоёв (0 — слоёв нет): по ней окна не заходят панелями под неё. */
    protected int layersWidth() {
        return layers().isEmpty() ? 0 : LAYER_BOX + 2 * PANEL_PAD;
    }

    private int layersLeft() {
        return width - MARGIN - layersWidth();
    }

    protected int layersTop() {
        return topReserved() + MARGIN;
    }

    private int layersHeight() {
        return 2 * PANEL_PAD + layers().size() * LAYER_ROW - (LAYER_ROW - LAYER_BOX);
    }

    private boolean overLayers(double mouseX, double mouseY) {
        return !layers().isEmpty() && mouseX >= layersLeft() && mouseX < layersLeft() + layersWidth()
                && mouseY >= layersTop() && mouseY < layersTop() + layersHeight();
    }

    private void renderLayersPanel(GuiGraphics graphics) {
        if (layers().isEmpty()) {
            return;
        }
        panel(graphics, layersLeft(), layersTop(), layersWidth(), layersHeight());
    }

    protected static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, COLOR_FRAME);
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
    }

    // ===== Инструменты =====

    private int toolsWidth() {
        int count = tools().size();
        return count * TOOL_SIZE + (count - 1) * TOOL_GAP;
    }

    protected int toolsLeft() {
        return (width - toolsWidth()) / 2;
    }

    protected int toolsTop() {
        return height - MARGIN - TOOL_SIZE;
    }

    private boolean overTools(double mouseX, double mouseY) {
        return toolAt(mouseX, mouseY) != null;
    }

    @Nullable
    private Tool toolAt(double mouseX, double mouseY) {
        int x = toolsLeft();
        int top = toolsTop();
        for (Tool each : tools()) {
            if (mouseX >= x && mouseX < x + TOOL_SIZE && mouseY >= top && mouseY < top + TOOL_SIZE) {
                return each;
            }
            x += TOOL_SIZE + TOOL_GAP;
        }
        return null;
    }

    private int toolX(Tool each) {
        return toolsLeft() + tools().indexOf(each) * (TOOL_SIZE + TOOL_GAP);
    }

    // ===== Всплывающие настройки =====

    private void initPopup() {
        popupWidth = switch (popup) {
            case DRAW -> 140;
            case WAYPOINT -> ICONS_PER_ROW * ICON_CELL + 2 * PANEL_PAD;
            default -> 0;
        };
        popupHeight = switch (popup) {
            case DRAW -> 2 * PANEL_PAD + TEXT_ROW + SWATCH + 6 + TEXT_ROW + SLIDER_HEIGHT + 6 + 20;
            case WAYPOINT -> 2 * PANEL_PAD + TEXT_ROW + iconRows() * ICON_CELL + 6 + TEXT_ROW
                    + 2 * DYE_STEP + 6 + 17;
            default -> 0;
        };
        popupLeft = Mth.clamp(toolX(popup) + TOOL_SIZE / 2 - popupWidth / 2, MARGIN, width - MARGIN - popupWidth);
        popupTop = toolsTop() - POPUP_GAP - popupHeight;
        int x = popupLeft + PANEL_PAD;
        int w = popupWidth - 2 * PANEL_PAD;
        int y = popupTop + PANEL_PAD + TEXT_ROW;
        if (popup == Tool.DRAW) {
            for (int i = 0; i < INKS.length; i++) {
                int index = i;
                addRenderableWidget(new MapWidgets.SwatchButton(x + i * (SWATCH + 4), y, SWATCH, INKS[i],
                        () -> ink == index, () -> ink = index));
            }
            y += SWATCH + 6 + TEXT_ROW;
            addRenderableWidget(new MapWidgets.IntSlider(x, y, w, SLIDER_HEIGHT, 1, MAX_BRUSH, brushSize,
                    value -> brushSize = value,
                    value -> Component.translatable("gui.halgventmod.map.draw.brush_size", value)));
            y += SLIDER_HEIGHT + 6;
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.map.draw.clear"),
                    button -> confirmClear()).bounds(x, y, w, 20).build());
        } else if (popup == Tool.WAYPOINT) {
            y += iconRows() * ICON_CELL + 6 + TEXT_ROW + 2 * DYE_STEP + 6;
            addRenderableWidget(Checkbox.builder(Component.translatable("gui.halgventmod.waypoint.compass"), font)
                    .pos(x, y).selected(waypointCompass)
                    .onValueChange((box, value) -> waypointCompass = value).build());
        }
    }

    private static int iconRows() {
        return (MapIcon.values().length + ICONS_PER_ROW - 1) / ICONS_PER_ROW;
    }

    private boolean overPopup(double mouseX, double mouseY) {
        return mouseX >= popupLeft && mouseX < popupLeft + popupWidth && mouseY >= popupTop
                && mouseY < popupTop + popupHeight;
    }

    private void closePopup() {
        popup = null;
        rebuildWidgets();
    }

    private void confirmClear() {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                MapDrawing.clearAll();
            }
            minecraft.setScreen(this);
        }, Component.translatable("gui.halgventmod.map.draw.clear"),
                Component.translatable("gui.halgventmod.map.draw.clear_confirm")));
    }

    private void renderPopup(GuiGraphics graphics) {
        panel(graphics, popupLeft, popupTop, popupWidth, popupHeight);
        int x = popupLeft + PANEL_PAD;
        int y = popupTop + PANEL_PAD;
        if (popup == Tool.DRAW) {
            graphics.drawString(font, Component.translatable("gui.halgventmod.map.draw.color"), x, y, COLOR_DIM, false);
            y += TEXT_ROW + SWATCH + 6;
            graphics.drawString(font, Component.translatable("gui.halgventmod.map.draw.brush"), x, y, COLOR_DIM, false);
        } else if (popup == Tool.WAYPOINT) {
            graphics.drawString(font, Component.translatable("gui.halgventmod.waypoint.icon", waypointIcon.title()),
                    x, y, COLOR_DIM, false);
            y += TEXT_ROW;
            MapIcon[] icons = MapIcon.values();
            RenderSystem.enableBlend();
            for (int i = 0; i < icons.length; i++) {
                int cx = x + i % ICONS_PER_ROW * ICON_CELL;
                int cy = y + i / ICONS_PER_ROW * ICON_CELL;
                if (icons[i] == waypointIcon) {
                    graphics.fill(cx, cy, cx + ICON_CELL, cy + ICON_CELL, COLOR_TITLE);
                }
                graphics.fill(cx + 1, cy + 1, cx + ICON_CELL - 1, cy + ICON_CELL - 1, COLOR_CELL);
                MapIcon.draw(graphics, icons[i].cell(waypointDye), cx + 1, cy + 1, ICON_CELL - 2);
            }
            RenderSystem.disableBlend();
            y += iconRows() * ICON_CELL + 6;
            graphics.drawString(font, Component.translatable("gui.halgventmod.waypoint.flag_color",
                    Component.translatable("color.minecraft." + waypointDye.getName())), x, y, COLOR_DIM, false);
            y += TEXT_ROW;
            for (DyeColor each : DyeColor.values()) {
                int sx = x + 1 + each.getId() % DYES_PER_ROW * DYE_STEP;
                int sy = y + each.getId() / DYES_PER_ROW * DYE_STEP;
                graphics.fill(sx - 1, sy - 1, sx + DYE_SWATCH + 1, sy + DYE_SWATCH + 1,
                        each == waypointDye ? 0xFFFFFFFF : 0xFF000000);
                graphics.fill(sx, sy, sx + DYE_SWATCH, sy + DYE_SWATCH, 0xFF000000 | each.getTextureDiffuseColor());
            }
        }
    }

    // Клик по сетке значков или цветов во всплывающей панели метки
    private boolean popupGridClicked(double mouseX, double mouseY) {
        if (popup != Tool.WAYPOINT) {
            return false;
        }
        int x = popupLeft + PANEL_PAD;
        int y = popupTop + PANEL_PAD + TEXT_ROW;
        MapIcon[] icons = MapIcon.values();
        for (int i = 0; i < icons.length; i++) {
            int cx = x + i % ICONS_PER_ROW * ICON_CELL;
            int cy = y + i / ICONS_PER_ROW * ICON_CELL;
            if (mouseX >= cx && mouseX < cx + ICON_CELL && mouseY >= cy && mouseY < cy + ICON_CELL) {
                waypointIcon = icons[i];
                return true;
            }
        }
        y += iconRows() * ICON_CELL + 6 + TEXT_ROW;
        for (DyeColor each : DyeColor.values()) {
            int sx = x + 1 + each.getId() % DYES_PER_ROW * DYE_STEP;
            int sy = y + each.getId() / DYES_PER_ROW * DYE_STEP;
            if (mouseX >= sx && mouseX < sx + DYE_SWATCH && mouseY >= sy && mouseY < sy + DYE_SWATCH) {
                waypointDye = each;
                return true;
            }
        }
        return false;
    }

    // ===== Рисование =====

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MapTile.newFrame();
        renderWorld(graphics, mouseX, mouseY, partialTick);
        if (MapLayer.WAYPOINTS.shown() || naming != null) {
            renderWaypoints(graphics);
        }
        if (MapLayer.SQUAD.shown()) {
            renderSquad(graphics);
        }
        if (showPlayer()) {
            renderPlayer(graphics, partialTick);
        }
        if (tool == Tool.DRAW && overMap(mouseX, mouseY) && editableAt(worldX(mouseX), worldZ(mouseY))) {
            renderBrush(graphics, mouseX, mouseY);
        }
        renderOverlay(graphics, mouseX, mouseY, partialTick);
        renderLayersPanel(graphics);
        if (popup != null) {
            renderPopup(graphics);
        }
        if (naming != null) {
            // Поле названия — под меткой, где бы она ни была на экране
            int x = Math.round(screenX(naming.x + 0.5));
            int y = Math.round(screenY(naming.z + 0.5));
            nameField.setX(Mth.clamp(x - NAME_FIELD_WIDTH / 2, MARGIN, width - MARGIN - NAME_FIELD_WIDTH));
            nameField.setY(Mth.clamp(y + ICON_SIZE / 2 + 2, topReserved(), height - 14));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (naming == null && popup == null && overMap(mouseX, mouseY) && MapLayer.WAYPOINTS.shown()) {
            Waypoint hovered = waypointAt(mouseX, mouseY);
            if (hovered != null) {
                renderWaypointTooltip(graphics, hovered, mouseX, mouseY);
            }
        }
    }

    // Метка: значок серединой на месте метки; размер не зависит от масштаба
    private void renderWaypoints(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        if (MapLayer.WAYPOINTS.shown()) {
            for (Waypoint waypoint : Waypoints.all()) {
                if (waypointShown(waypoint)) {
                    drawWaypoint(graphics, waypoint);
                }
            }
        }
        if (naming != null && namingNew) {
            drawWaypoint(graphics, naming);
        }
        RenderSystem.disableBlend();
    }

    private void drawWaypoint(GuiGraphics graphics, Waypoint waypoint) {
        int x = Math.round(screenX(waypoint.x + 0.5));
        int y = Math.round(screenY(waypoint.z + 0.5));
        MapIcon.draw(graphics, waypoint.cell(), x - ICON_SIZE / 2, y - ICON_SIZE / 2, ICON_SIZE);
    }

    private void renderWaypointTooltip(GuiGraphics graphics, Waypoint waypoint, int mouseX, int mouseY) {
        int distance = minecraft.player == null ? 0
                : Mth.floor(Math.sqrt(minecraft.player.distanceToSqr(waypoint.x + 0.5, waypoint.y, waypoint.z + 0.5)));
        Component name = waypoint.name.isEmpty() ? Component.translatable("gui.halgventmod.waypoint.unnamed")
                .withStyle(ChatFormatting.ITALIC) : Component.literal(waypoint.name);
        graphics.renderComponentTooltip(font, List.of(name,
                Component.translatable("gui.halgventmod.waypoint.coords", waypoint.x, waypoint.y, waypoint.z)
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.halgventmod.waypoint.distance", distance).withStyle(ChatFormatting.GRAY)),
                mouseX, mouseY);
    }

    /** Метка под курсором (ближайшая, если несколько). */
    @Nullable
    protected Waypoint waypointAt(double mouseX, double mouseY) {
        Waypoint found = null;
        double best = (ICON_SIZE / 2.0) * (ICON_SIZE / 2.0);
        for (Waypoint waypoint : Waypoints.all()) {
            if (!waypointShown(waypoint)) {
                continue;
            }
            double dx = screenX(waypoint.x + 0.5) - mouseX;
            double dy = screenY(waypoint.z + 0.5) - mouseY;
            if (dx * dx + dy * dy <= best) {
                best = dx * dx + dy * dy;
                found = waypoint;
            }
        }
        return found;
    }

    // Рамка кисти вокруг курсора: сколько блоков она закрасит
    private void renderBrush(GuiGraphics graphics, int mouseX, int mouseY) {
        int half = Math.max(1, Math.round(brushSize * zoom / 2.0F));
        int left = mouseX - half;
        int top = mouseY - half;
        int right = mouseX + half;
        int bottom = mouseY + half;
        graphics.fill(left, top, right, top + 1, COLOR_BRUSH);
        graphics.fill(left, bottom - 1, right, bottom, COLOR_BRUSH);
        graphics.fill(left, top + 1, left + 1, bottom - 1, COLOR_BRUSH);
        graphics.fill(right - 1, top + 1, right, bottom - 1, COLOR_BRUSH);
    }

    // Товарищи по отряду: зелёный курсор (остриё — куда смотрит) и имя под ним.
    // Позиции присылает сервер дважды в секунду — видны на любом расстоянии
    protected void renderSquad(GuiGraphics graphics) {
        for (SquadMembersPayload.Member member : SquadMarkers.clientMembers) {
            float x = screenX(member.x());
            float y = screenY(member.z());
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(member.yaw()));
            RenderSystem.enableBlend();
            graphics.setColor(SQUAD_TINT_R, SQUAD_TINT_G, SQUAD_TINT_B, 1.0F);
            MapIcon.draw(graphics, MapIcon.CURSOR, -9, -7, ICON_SIZE);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            graphics.pose().popPose();
            graphics.drawCenteredString(font, member.name(), Math.round(x), Math.round(y) + ICON_SIZE / 2 + 2,
                    COLOR_SQUAD);
        }
    }

    // Курсор игрока: остриё — куда он смотрит; размер не зависит от масштаба.
    // В атласе он остриём вниз (на юг, куда смотрит игрок с поворотом 0), середина рисунка — (18; 13.5) из 32
    private void renderPlayer(GuiGraphics graphics, float partialTick) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        double x = Mth.lerp(partialTick, player.xo, player.getX());
        double z = Mth.lerp(partialTick, player.zo, player.getZ());
        graphics.pose().pushPose();
        graphics.pose().translate(screenX(x), screenY(z), 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(player.getViewYRot(partialTick)));
        RenderSystem.enableBlend();
        MapIcon.draw(graphics, MapIcon.CURSOR, -9, -7, ICON_SIZE);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    // ===== Масштаб =====

    // Отдаление — только степенями двойки в пикселях монитора (масштаб интерфейса могли сменить, пока окно было закрыто)
    protected void snapZoom() {
        double scale = minecraft.getWindow().getGuiScale();
        double real = zoom * scale;
        if (real < 1) {
            real = Math.max(MIN_REAL_ZOOM, Math.pow(2, Math.round(Math.log(real) / Math.log(2))));
            zoom = (float) (real / scale);
        }
        zoom = Mth.clamp(zoom, minZoom(), MAX_ZOOM);
    }

    /** Масштаб к точке экрана: точка мира под ней остаётся на месте. */
    protected void zoomAt(double mouseX, double mouseY, double scrollY) {
        double anchorX = worldX(mouseX);
        double anchorZ = worldZ(mouseY);
        double scale = minecraft.getWindow().getGuiScale();
        double real = zoom * scale;
        if (scrollY > 0) {
            real = real < 1 - 1e-6 ? real * 2 : real * ZOOM_STEP;
        } else {
            real = real <= 1 + 1e-6 ? real / 2 : Math.max(1, real / ZOOM_STEP); // из плавных шагов — сначала ровно на 1
        }
        float wanted = (float) (real / scale);
        if (scrollY < 0 && wanted < minZoom() - 1e-6F && zoom <= minZoom() + 1e-6F && zoomOutBeyond()) {
            return;
        }
        zoom = Mth.clamp(wanted, minZoom(), MAX_ZOOM);
        centerX = anchorX - (mouseX - width / 2.0) / zoom;
        centerZ = anchorZ - (mouseY - height / 2.0) / zoom;
        clampCamera();
    }

    // ===== Перевод координат =====

    protected double worldX(double screenX) {
        return centerX + (screenX - width / 2.0) / zoom;
    }

    protected double worldZ(double screenY) {
        return centerZ + (screenY - height / 2.0) / zoom;
    }

    protected float screenX(double worldX) {
        return (float) (width / 2.0 + (worldX - centerX) * zoom);
    }

    protected float screenY(double worldZ) {
        return (float) (height / 2.0 + (worldZ - centerZ) * zoom);
    }

    // ===== Метки без окна =====

    private void startNaming(Waypoint waypoint, boolean isNew) {
        naming = waypoint;
        namingNew = isNew;
        nameField.setValue(isNew ? "" : waypoint.name);
        nameField.visible = true;
        setFocused(nameField);
    }

    /** keep — сохранить (Enter или клик мимо), иначе отменить (Esc). */
    private void finishNaming(boolean keep) {
        if (naming == null) {
            return;
        }
        if (keep) {
            naming.name = nameField.getValue().strip();
            if (namingNew) {
                Waypoints.add(naming);
            } else {
                Waypoints.changed();
            }
        }
        naming = null;
        nameField.setValue("");
        nameField.visible = false;
        setFocused(null);
    }

    /** Высота для новой метки: поверхность, если чанк загружен, иначе с карты, иначе — где стоит игрок. */
    protected int standingY(int x, int z) {
        Integer surface = WorldMap.surfaceHeight(x, z);
        if (surface != null) {
            return surface;
        }
        short mapped = WorldMap.loadedHeight(x, z);
        if (mapped != MapRegion.NO_HEIGHT) {
            return mapped + 1;
        }
        return minecraft.player != null ? minecraft.player.getBlockY() : 64;
    }

    // ===== Управление =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Поле названия метки: клик по нему — ему; мимо — метка остаётся с тем, что введено
        if (naming != null) {
            if (nameField.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            finishNaming(true);
        }
        // ПКМ по иконке инструмента — его настройки
        Tool clickedTool = toolAt(mouseX, mouseY);
        if (clickedTool != null && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (clickedTool.settings && toolEnabled(clickedTool)) {
                popup = popup == clickedTool ? null : clickedTool;
                rebuildWidgets();
            }
            return true;
        }
        // Открытые настройки: клик внутри — им, мимо — закрыть (клик на карту не идёт)
        if (popup != null) {
            if (overPopup(mouseX, mouseY)) {
                if (popupGridClicked(mouseX, mouseY)) {
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (clickedTool == null) {
                closePopup();
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button) || !overMap(mouseX, mouseY)) {
            return true;
        }
        pressedButton = button;
        pressX = mouseX;
        pressY = mouseY;
        dragged = false;
        if (mapPressed(mouseX, mouseY, button)) {
            return true;
        }
        if (tool == Tool.DRAW && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            MapDrawing.beginStroke();
            lastDrawX = worldX(mouseX);
            lastDrawZ = worldZ(mouseY);
            drawTo(mouseX, mouseY, button);
        }
        return true;
    }

    // Отрезок от прошлой точки до курсора: левой — цветом, правой — ластиком. Только там, где можно рисовать
    private void drawTo(double mouseX, double mouseY, int button) {
        double x = worldX(mouseX);
        double z = worldZ(mouseY);
        if (editableAt(x, z) && editableAt(lastDrawX, lastDrawZ)) {
            int color = button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? 0xFF000000 | INKS[ink] : 0;
            MapDrawing.line(lastDrawX, lastDrawZ, x, z, brushSize, color);
        }
        lastDrawX = x;
        lastDrawZ = z;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressedButton != button) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (Math.abs(mouseX - pressX) > CLICK_SLOP || Math.abs(mouseY - pressY) > CLICK_SLOP) {
            dragged = true;
        }
        if (mapDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        boolean pan = tool == Tool.DRAW ? button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE : button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (pan) {
            centerX -= dragX / zoom;
            centerZ -= dragY / zoom;
            clampCamera();
        } else if (tool == Tool.DRAW) {
            drawTo(mouseX, mouseY, button);
        }
        return true;
    }

    /** Перетаскивание инструментом окна; true — обработано (карта не двигается). */
    protected boolean mapDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    /** Отпустили кнопку, нажатую на карте (до проверки клика). */
    protected void mapReleased(double mouseX, double mouseY, int button) {}

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressedButton == button) {
            pressedButton = -1;
            MapDrawing.endStroke();
            mapReleased(mouseX, mouseY, button);
            if (!dragged && !mapClicked(mouseX, mouseY, button)) {
                waypointClicked(mouseX, mouseY, button);
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // «Метка»: ЛКМ мимо меток — новая метка, двойной ЛКМ по метке — переименовать, ПКМ по метке — удалить
    private void waypointClicked(double mouseX, double mouseY, int button) {
        if (tool != Tool.WAYPOINT || !Waypoints.available()) {
            return;
        }
        Waypoint hit = MapLayer.WAYPOINTS.shown() ? waypointAt(mouseX, mouseY) : null;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (hit != null) {
                Waypoints.remove(hit);
            }
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (hit != null) {
            long now = Util.getMillis();
            if (hit == lastClickedWaypoint && now - lastClickMillis < DOUBLE_CLICK_MILLIS) {
                lastClickedWaypoint = null;
                startNaming(hit, false);
            } else {
                lastClickedWaypoint = hit;
                lastClickMillis = now;
            }
            return;
        }
        int x = Mth.floor(worldX(mouseX));
        int z = Mth.floor(worldZ(mouseY));
        if (!editableAt(x + 0.5, z + 0.5)) {
            return;
        }
        Waypoint waypoint = new Waypoint("", x, standingY(x, z), z, waypointIcon, waypointDye);
        waypoint.compass = waypointCompass;
        startNaming(waypoint, true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || !overMap(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        zoomAt(mouseX, mouseY, scrollY);
        if (pressedButton >= 0 && tool == Tool.DRAW) {
            lastDrawX = worldX(mouseX);
            lastDrawZ = worldZ(mouseY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (naming != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishNaming(true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishNaming(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers); // всё остальное — в поле
        }
        if (popup != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePopup();
            return true;
        }
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            centerOnPlayer();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z && hasControlDown()) {
            MapDrawing.undo();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        finishNaming(true);
        MapDrawing.endStroke();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
