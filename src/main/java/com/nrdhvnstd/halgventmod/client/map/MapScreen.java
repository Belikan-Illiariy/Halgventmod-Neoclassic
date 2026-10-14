package com.nrdhvnstd.halgventmod.client.map;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.client.MenuTab;
import com.nrdhvnstd.halgventmod.client.MenuTabButton;
import com.nrdhvnstd.halgventmod.client.MenuTabScreen;
import com.nrdhvnstd.halgventmod.client.MenuTabs;
import com.nrdhvnstd.halgventmod.network.MapTeleportPayload;
import com.nrdhvnstd.halgventmod.network.SquadMembersPayload;
import com.nrdhvnstd.halgventmod.squad.SquadMarkers;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно «Карта», как Xaero's World Map: разведанный мир на весь экран, вкладки поверх него.
 * Справа — инструменты:
 * «Рука» — перетаскивание левой кнопкой сдвигает карту; клик по надписи выделяет её рамкой (MapTextEditor:
 * двигать, растягивать, изгибать, вращать; двойной клик — окно надписи, Delete — удалить); правый клик — меню: метка, надпись (MapTexts),
 * телепортация (только администрации);
 * «Карандаш» — левой кнопкой рисовать, правой стирать, средней двигать карту, Ctrl+Z — отменить штрих
 * (пометки — MapDrawing); «Стиль» — основа и тонкая настройка вида карты (MapStyle).
 * Колёсико — масштаб к курсору, Пробел — вернуться к себе. При отдалении рисуются уровни MapPyramid: пиксель — среднее по блокам под ним.
 * Неразведанное — чёрное. Поверх — пометки, названия биомов (BiomeLabels; выключаются в панели стиля) и надписи.
 * Внизу — биом и координаты под курсором.
 */
public class MapScreen extends Screen implements MenuTabScreen {
    // Масштаб — в пикселях интерфейса на блок; «настоящий» масштаб — в пикселях монитора (× масштаб интерфейса).
    // Вблизи шаги плавные (ZOOM_STEP), при отдалении — ровно вдвое: тогда пиксель тайла уровня отдаления
    // ложится ровно на пиксель монитора и рисуется чётко, без сглаживания и без ряби
    private static final float MIN_REAL_ZOOM = 1.0F / (1 << MapPyramid.MAX_LEVEL);
    private static final float MAX_ZOOM = 16.0F;
    private static final float ZOOM_STEP = 1.25F;
    private static final float MIN_TEXT_PIXELS = 3; // надписи ниже стольких пикселей не рисуются
    private static final int ICON_SIZE = 16;
    // Товарищи по отряду: курсор, окрашенный в зелёный, и имя тем же цветом
    private static final float SQUAD_TINT_R = 0.45F;
    private static final float SQUAD_TINT_G = 1.0F;
    private static final float SQUAD_TINT_B = 0.5F;
    private static final int COLOR_SQUAD = 0xFF7CFC8C;

    private static final int MARGIN = 4;
    private static final int TOOL_SIZE = 20;
    private static final int PANEL_WIDTH = 170;
    private static final int PANEL_BOTTOM = 28; // место под координаты и подсказку
    private static final int SWATCH = 16;
    private static final int SLIDER_HEIGHT = 14;
    private static final int MAX_BRUSH = 12;

    private static final int COLOR_UNKNOWN = 0xFF000000;
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_DIM = 0xFFB8A888;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_BRUSH = 0xC0FFFFFF;

    // Меню правого клика
    private static final int MENU_ROW = 14;
    private static final int MENU_PAD = 5;
    private static final int COLOR_MENU = 0xF0241C14;
    private static final int COLOR_MENU_FRAME = 0xFF6E6250;
    private static final int COLOR_MENU_HOVER = 0xFF4A3E2E;

    /** Цвета карандаша и надписей: чернила, киноварь, синий, коричневый, зелёный, белила. */
    static final int[] INKS = {0x1E1A16, 0xB3261E, 0x2B4C9B, 0x6B4423, 0x2E6B2E, 0xE8DCC0};

    /** Пункт меню правого клика. */
    private record MenuItem(Component title, Runnable action) {}

    private enum Tool {
        MOVE(Items.COMPASS), DRAW(Items.FEATHER), STYLE(Items.PAINTING);

        final ItemStack icon;

        Tool(net.minecraft.world.item.Item icon) {
            this.icon = new ItemStack(icon);
        }

        Component title() {
            return Component.translatable("gui.halgventmod.map.tool." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    // Масштаб, инструмент и кисть помнятся между открытиями; середина — всегда на игроке при открытии
    private static float zoom = 2.0F;
    private static Tool tool = Tool.MOVE;
    private static int ink = 1;
    private static int brushSize = 3;
    private static int drawScroll;
    private static int styleScroll;

    private double centerX;
    private double centerZ;
    private boolean centered;
    private MapSidePanel panel;
    /** Кнопка, нажатая на самой карте (не на панели); -1 — никакая. */
    private int pressedButton = -1;
    private double lastDrawX;
    private double lastDrawZ;
    // Открытое меню правого клика: пункты и левый верхний угол; null — закрыто
    private List<MenuItem> menu;
    private int menuX;
    private int menuY;
    private int menuWidth;
    // Двойной клик по надписи — её окно
    private static final long DOUBLE_CLICK_MILLIS = 300;
    private MapText lastClickedText;
    private long lastClickMillis;

    private final MapTextEditor textEditor = new MapTextEditor(new MapTextEditor.View() {
        @Override
        public float screenX(double worldX) {
            return MapScreen.this.screenX(worldX);
        }

        @Override
        public float screenY(double worldZ) {
            return MapScreen.this.screenY(worldZ);
        }

        @Override
        public float zoom() {
            return zoom;
        }
    });

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

    public MapScreen() {
        super(MenuTab.MAP.title());
    }

    @Override
    public MenuTab tab() {
        return MenuTab.MAP;
    }

    @Override
    protected void init() {
        snapZoom();
        if (!centered) {
            centerOnPlayer();
            centered = true;
        }
        for (MenuTabButton button : MenuTabs.createButtons(font, 0, width, MenuTab.MAP)) {
            addRenderableWidget(button);
        }
        int toolX = width - TOOL_SIZE - MARGIN;
        int toolY = MenuTabs.TAB_HEIGHT + MARGIN;
        for (Tool each : Tool.values()) {
            addRenderableWidget(new MapWidgets.ToolButton(toolX, toolY, TOOL_SIZE, each.icon, each.title(),
                    each == tool, () -> selectTool(each)));
            toolY += TOOL_SIZE + 2;
        }
        panel = switch (tool) {
            case DRAW -> drawPanel();
            case STYLE -> stylePanel();
            case MOVE -> null;
        };
    }

    // Повторный выбор карандаша или стиля закрывает панель
    private void selectTool(Tool selected) {
        rememberScroll();
        tool = tool == selected ? Tool.MOVE : selected;
        textEditor.select(null);
        rebuildWidgets();
    }

    private void rememberScroll() {
        if (panel != null && tool == Tool.DRAW) {
            drawScroll = panel.scroll();
        } else if (panel != null && tool == Tool.STYLE) {
            styleScroll = panel.scroll();
        }
    }

    private MapSidePanel newPanel(int scroll) {
        int top = MenuTabs.TAB_HEIGHT + MARGIN;
        return new MapSidePanel(width - TOOL_SIZE - 2 * MARGIN - PANEL_WIDTH, top, PANEL_WIDTH,
                height - top - PANEL_BOTTOM, scroll, this::addRenderableWidget);
    }

    // ===== Панель карандаша =====

    private MapSidePanel drawPanel() {
        MapSidePanel p = newPanel(drawScroll);
        int x = p.innerLeft();
        int w = p.innerWidth();
        int y = 0;
        p.label(Tool.DRAW.title(), y, COLOR_TITLE);
        y += 16;
        p.label(Component.translatable("gui.halgventmod.map.draw.color"), y, COLOR_DIM);
        y += 11;
        for (int i = 0; i < INKS.length; i++) {
            int index = i;
            p.widget(new MapWidgets.SwatchButton(x + i * (SWATCH + 4), 0, SWATCH, INKS[i],
                    () -> ink == index, () -> ink = index), y);
        }
        y += SWATCH + 8;
        p.label(Component.translatable("gui.halgventmod.map.draw.brush"), y, COLOR_DIM);
        y += 11;
        p.widget(new MapWidgets.IntSlider(x, 0, w, SLIDER_HEIGHT, 1, MAX_BRUSH, brushSize, value -> brushSize = value,
                value -> Component.translatable("gui.halgventmod.map.draw.brush_size", value)), y);
        y += SLIDER_HEIGHT + 8;
        for (String hint : new String[] {"draw", "erase", "pan", "undo"}) {
            p.label(Component.translatable("gui.halgventmod.map.draw.hint." + hint), y, COLOR_TEXT);
            y += 11;
        }
        y += 8;
        p.widget(Button.builder(Component.translatable("gui.halgventmod.map.draw.clear"), button -> confirmClear())
                .bounds(x, 0, w, 20).build(), y);
        p.finish();
        return p;
    }

    private void confirmClear() {
        rememberScroll();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                MapDrawing.clearAll();
            }
            minecraft.setScreen(this);
        }, Component.translatable("gui.halgventmod.map.draw.clear"),
                Component.translatable("gui.halgventmod.map.draw.clear_confirm")));
    }

    // ===== Панель стиля =====

    private MapSidePanel stylePanel() {
        MapSidePanel p = newPanel(styleScroll);
        int x = p.innerLeft();
        int w = p.innerWidth();
        int half = (w - 4) / 2;
        int y = 0;
        p.label(Tool.STYLE.title(), y, COLOR_TITLE);
        y += 16;
        p.label(Component.translatable("gui.halgventmod.map.style.base"), y, COLOR_DIM);
        y += 11;
        MapStyle.Base[] bases = MapStyle.Base.values();
        for (int i = 0; i < bases.length; i++) {
            MapStyle.Base base = bases[i];
            Component title = base == MapStyle.base()
                    ? base.title().copy().withStyle(ChatFormatting.YELLOW) : base.title();
            p.widget(Button.builder(title, button -> {
                MapStyle.setBase(base);
                rememberScroll();
                rebuildWidgets();
            }).bounds(x + (i % 2) * (half + 4), 0, half, 18).build(), y + (i / 2) * 20);
        }
        y += 2 * 20 + 4;
        p.widget(Checkbox.builder(Component.translatable("gui.halgventmod.map.style.biome_labels"), font)
                .pos(x, 0).selected(MapStyle.biomeLabels())
                .onValueChange((checkbox, shown) -> MapStyle.setBiomeLabels(shown)).build(), y);
        y += 17 + 6;
        for (MapStyle.Group group : MapStyle.Group.values()) {
            p.label(group.title(), y, COLOR_TITLE);
            y += 11;
            for (MapStyle.Setting setting : MapStyle.Setting.values()) {
                if (setting.group != group) {
                    continue;
                }
                p.widget(new MapWidgets.IntSlider(x, 0, w, SLIDER_HEIGHT, setting.min, setting.max,
                        MapStyle.get(setting), value -> MapStyle.set(setting, value),
                        value -> Component.translatable(setting.degrees
                                ? "gui.halgventmod.map.style.degrees" : "gui.halgventmod.map.style.percent",
                                setting.title(), value)), y);
                y += SLIDER_HEIGHT + 2;
            }
            y += 6;
        }
        p.widget(Button.builder(Component.translatable("gui.halgventmod.map.style.reset"), button -> {
            MapStyle.reset();
            rememberScroll();
            rebuildWidgets();
        }).bounds(x, 0, w, 18).build(), y);
        p.finish();
        return p;
    }

    private void centerOnPlayer() {
        LocalPlayer player = minecraft.player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
    }

    // ===== Рисование =====

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MapTile.newFrame();
        graphics.fill(0, 0, width, height, COLOR_UNKNOWN);
        renderRegions(graphics);
        if (MapStyle.biomeLabels()) {
            biomeLabels.update(worldX(0), worldZ(0), worldX(width), worldZ(height));
            biomeLabels.render(graphics, font, projection, zoom);
        }
        renderTexts(graphics);
        renderWaypoints(graphics);
        renderSquad(graphics);
        renderPlayer(graphics, partialTick);
        if (textEditor.selected() != null && !MapTexts.all().contains(textEditor.selected())) {
            textEditor.select(null); // надпись удалили (меню, окно правки)
        }
        textEditor.render(graphics, font);
        if (tool == Tool.DRAW && overMap(mouseX, mouseY)) {
            renderBrush(graphics, mouseX, mouseY);
        }
        if (panel != null) {
            panel.render(graphics, font);
        }
    }

    // Надписи: серединой на своём месте; размер на экране от масштаба не зависит
    private void renderTexts(GuiGraphics graphics) {
        for (MapText text : MapTexts.all()) {
            float x = screenX(text.x + 0.5);
            float y = screenY(text.z + 0.5);
            float screenScale = text.screenScale(zoom);
            if (screenScale * font.lineHeight < MIN_TEXT_PIXELS) {
                continue; // так мелко не прочесть — и рисовать незачем
            }
            float reach = (font.width(text.text) + font.lineHeight) * screenScale; // при любом повороте и изгибе — с запасом
            if (x + reach < 0 || x - reach > width || y + reach < 0 || y - reach > height) {
                continue;
            }
            text.draw(graphics, font, zoom, x, y);
        }
    }

    /** Надпись под курсором (верхняя, если несколько). */
    private MapText textAt(double mouseX, double mouseY) {
        List<MapText> texts = MapTexts.all();
        for (int i = texts.size() - 1; i >= 0; i--) {
            MapText text = texts.get(i);
            if (text.screenScale(zoom) * font.lineHeight >= MIN_TEXT_PIXELS
                    && text.contains(font, zoom, screenX(text.x + 0.5), screenY(text.z + 0.5), mouseX, mouseY)) {
                return text;
            }
        }
        return null;
    }

    // Метка: значок серединой на месте метки; размер не зависит от масштаба
    private void renderWaypoints(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        for (Waypoint waypoint : Waypoints.all()) {
            int x = Math.round(screenX(waypoint.x + 0.5));
            int y = Math.round(screenY(waypoint.z + 0.5));
            MapIcon.draw(graphics, waypoint.cell(), x - ICON_SIZE / 2, y - ICON_SIZE / 2, ICON_SIZE);
        }
        RenderSystem.disableBlend();
    }

    /** Метка под курсором (ближайшая, если несколько). */
    private Waypoint waypointAt(double mouseX, double mouseY) {
        Waypoint found = null;
        double best = (ICON_SIZE / 2.0) * (ICON_SIZE / 2.0);
        for (Waypoint waypoint : Waypoints.all()) {
            double dx = screenX(waypoint.x + 0.5) - mouseX;
            double dy = screenY(waypoint.z + 0.5) - mouseY;
            if (dx * dx + dy * dy <= best) {
                best = dx * dx + dy * dy;
                found = waypoint;
            }
        }
        return found;
    }

    /**
     * Уровень отдаления для нынешнего масштаба: такой, чтобы пиксель тайла был не крупнее пикселя экрана.
     * Вблизи — регионы (уровень 0), дальше — тайлы MapPyramid, где пиксель — среднее по 2^L блокам.
     */
    private int levelForZoom() {
        double real = realZoom();
        if (real >= 1) {
            return 0;
        }
        int level = (int) Math.round(Math.log(1 / real) / Math.log(2));
        return Mth.clamp(level, 0, MapPyramid.MAX_LEVEL);
    }

    // Пикселей монитора на блок
    private double realZoom() {
        return zoom * minecraft.getWindow().getGuiScale();
    }

    // Отдаление — только степенями двойки в пикселях монитора (масштаб интерфейса могли сменить, пока окно было закрыто)
    private void snapZoom() {
        double scale = minecraft.getWindow().getGuiScale();
        double real = zoom * scale;
        if (real < 1) {
            real = Math.max(MIN_REAL_ZOOM, Math.pow(2, Math.round(Math.log(real) / Math.log(2))));
            zoom = (float) (real / scale);
        }
    }

    // Видимые тайлы уровня, затем пометки поверх. Каждый тайл — со своей точкой отсчёта, чтобы вдали от нуля не терялась точность
    private void renderRegions(GuiGraphics graphics) {
        int level = levelForZoom();
        WorldMap.setShownLevel(level);
        int shift = MapTile.SHIFT + level;
        int fromX = Mth.floor(worldX(0)) >> shift;
        int toX = Mth.floor(worldX(width)) >> shift;
        int fromZ = Mth.floor(worldZ(0)) >> shift;
        int toZ = Mth.floor(worldZ(height)) >> shift;
        boolean blur = false; // пиксель тайла — не мельче пикселя монитора: всегда чёткие пиксели
        for (int tx = fromX; tx <= toX; tx++) {
            for (int tz = fromZ; tz <= toZ; tz++) {
                MapTile tile = level == 0 ? WorldMap.region(tx, tz) : WorldMap.lod(level, tx, tz);
                if (tile != null) {
                    ResourceLocation texture = tile.texture(blur); // заодно заказывает сборку картинки
                    if (tile.painted()) {
                        blitTile(graphics, texture, level, tx, tz, 0, 0, MapTile.SIZE);
                        continue;
                    }
                }
                // Пока нужный тайл читается или собирается — кусок более грубого, если он уже в памяти
                for (int up = 1; up <= 2 && level + up <= MapPyramid.MAX_LEVEL; up++) {
                    MapTile parent = WorldMap.loadedTile(level + up, tx >> up, tz >> up);
                    if (parent != null && parent.painted()) {
                        int part = MapTile.SIZE >> up;
                        int mask = (1 << up) - 1;
                        blitTile(graphics, parent.texture(false), level, tx, tz, (tx & mask) * part, (tz & mask) * part, part);
                        break;
                    }
                }
            }
        }
        // Пометки — только там, где они есть (их немного), а не по всем регионам на экране
        int regionFromX = Mth.floor(worldX(0)) >> MapTile.SHIFT;
        int regionToX = Mth.floor(worldX(width)) >> MapTile.SHIFT;
        int regionFromZ = Mth.floor(worldZ(0)) >> MapTile.SHIFT;
        int regionToZ = Mth.floor(worldZ(height)) >> MapTile.SHIFT;
        RenderSystem.enableBlend();
        for (long key : MapDrawing.regionsWithDrawings()) {
            int rx = ChunkPos.getX(key);
            int rz = ChunkPos.getZ(key);
            if (rx < regionFromX || rx > regionToX || rz < regionFromZ || rz > regionToZ) {
                continue;
            }
            ResourceLocation drawing = MapDrawing.texture(rx, rz);
            if (drawing != null) {
                blitTile(graphics, drawing, 0, rx, rz, 0, 0, MapTile.SIZE);
            }
        }
        RenderSystem.disableBlend();
    }

    // Текстура 512 × 512 (или её квадрат part пикселей от u, v) — на место тайла (level, tx, tz)
    private void blitTile(GuiGraphics graphics, ResourceLocation texture, int level, int tx, int tz, int u, int v, int part) {
        double blocks = (double) MapTile.SIZE * (1 << level); // блоков в стороне тайла
        float scale = (float) (zoom * blocks / part);
        graphics.pose().pushPose();
        graphics.pose().translate(screenX(tx * blocks), screenY(tz * blocks), 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(texture, 0, 0, part, part, u, v, part, part, MapTile.SIZE, MapTile.SIZE);
        graphics.pose().popPose();
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

    // Товарищи по отряду в этом измерении: зелёный курсор (остриё — куда смотрит) и имя под ним.
    // Позиции присылает сервер дважды в секунду — видны на любом расстоянии
    private void renderSquad(GuiGraphics graphics) {
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Внизу: биом под курсором (если там разведано), под ним — координаты
        int blockX = Mth.floor(worldX(mouseX));
        int blockZ = Mth.floor(worldZ(mouseY));
        ResourceLocation biome = overMap(mouseX, mouseY) ? WorldMap.biome(WorldMap.biomeAt(blockX, blockZ)) : null;
        if (biome != null) {
            graphics.drawCenteredString(font, Component.translatable(Util.makeDescriptionId("biome", biome)),
                    width / 2, height - 22, COLOR_TITLE);
        }
        graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.map.cursor", blockX, blockZ),
                width / 2, height - 11, COLOR_TEXT);

        if (menu != null) {
            renderMenu(graphics, mouseX, mouseY);
            return;
        }
        Waypoint hovered = overMap(mouseX, mouseY) ? waypointAt(mouseX, mouseY) : null;
        if (hovered != null && minecraft.player != null) {
            int distance = Mth.floor(Math.sqrt(minecraft.player.distanceToSqr(hovered.x + 0.5, hovered.y, hovered.z + 0.5)));
            graphics.renderComponentTooltip(font, List.of(
                    Component.literal(hovered.name),
                    Component.translatable("gui.halgventmod.waypoint.coords", hovered.x, hovered.y, hovered.z)
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.halgventmod.waypoint.distance", distance)
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    // ===== Меню правого клика =====

    // Пункты зависят от того, что под курсором: метка, надпись или просто местность
    private void openMenu(double mouseX, double mouseY) {
        int x = Mth.floor(worldX(mouseX));
        int z = Mth.floor(worldZ(mouseY));
        List<MenuItem> items = new ArrayList<>();
        if (Waypoints.available()) {
            Waypoint waypoint = waypointAt(mouseX, mouseY);
            if (waypoint != null) {
                items.add(new MenuItem(Component.translatable("gui.halgventmod.map.menu.edit_waypoint"),
                        () -> minecraft.setScreen(WaypointEditScreen.edit(this, waypoint))));
            } else {
                items.add(new MenuItem(Component.translatable("gui.halgventmod.map.menu.add_waypoint"),
                        () -> minecraft.setScreen(WaypointEditScreen.create(this, x, standingY(x, z), z))));
            }
        }
        if (MapTexts.available()) {
            MapText text = textAt(mouseX, mouseY);
            if (text != null) {
                items.add(new MenuItem(Component.translatable("gui.halgventmod.map.menu.edit_text"),
                        () -> minecraft.setScreen(MapTextEditScreen.edit(this, text, zoom))));
                items.add(new MenuItem(Component.translatable("gui.halgventmod.map.menu.delete_text"),
                        () -> MapTexts.remove(text)));
            } else {
                items.add(new MenuItem(Component.translatable("gui.halgventmod.map.menu.add_text"),
                        () -> minecraft.setScreen(MapTextEditScreen.create(this, x, z, zoom))));
            }
        }
        // Только администрации; сервер всё равно проверит права сам
        if (minecraft.player != null && minecraft.player.hasPermissions(MapTeleportPayload.PERMISSION_LEVEL)) {
            items.add(new MenuItem(Component.translatable("gui.halgventmod.map.menu.teleport"), () -> teleport(x, z)));
        }
        if (items.isEmpty()) {
            return;
        }
        int widest = 0;
        for (MenuItem item : items) {
            widest = Math.max(widest, font.width(item.title()));
        }
        menu = items;
        menuWidth = widest + 2 * MENU_PAD;
        int menuHeight = items.size() * MENU_ROW + 2;
        menuX = Math.min((int) mouseX, width - menuWidth - 1);
        menuY = Math.min((int) mouseY, height - menuHeight - 1);
    }

    private void teleport(int x, int z) {
        Integer surface = WorldMap.surfaceHeight(x, z);
        short mapped = WorldMap.loadedHeight(x, z);
        int hint = surface != null ? surface
                : mapped != MapRegion.NO_HEIGHT ? mapped + 1 : MapTeleportPayload.NO_HINT;
        PacketDistributor.sendToServer(new MapTeleportPayload(x, z, hint));
        onClose();
    }

    // Высота для новой метки: поверхность, если чанк загружен, иначе с карты, иначе — где стоит игрок
    private int standingY(int x, int z) {
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

    private int menuIndexAt(double mouseX, double mouseY) {
        if (menu == null || mouseX < menuX || mouseX >= menuX + menuWidth || mouseY < menuY + 1) {
            return -1;
        }
        int index = (int) (mouseY - menuY - 1) / MENU_ROW;
        return index < menu.size() ? index : -1;
    }

    private void renderMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        int menuHeight = menu.size() * MENU_ROW + 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400); // поверх всего окна
        graphics.fill(menuX - 1, menuY - 1, menuX + menuWidth + 1, menuY + menuHeight + 1, COLOR_MENU_FRAME);
        graphics.fill(menuX, menuY, menuX + menuWidth, menuY + menuHeight, COLOR_MENU);
        int hovered = menuIndexAt(mouseX, mouseY);
        for (int i = 0; i < menu.size(); i++) {
            int rowY = menuY + 1 + i * MENU_ROW;
            if (i == hovered) {
                graphics.fill(menuX, rowY, menuX + menuWidth, rowY + MENU_ROW, COLOR_MENU_HOVER);
            }
            graphics.drawString(font, menu.get(i).title(), menuX + MENU_PAD, rowY + 3,
                    i == hovered ? COLOR_TITLE : COLOR_TEXT, false);
        }
        graphics.pose().popPose();
    }

    // ===== Перевод координат =====

    private double worldX(double screenX) {
        return centerX + (screenX - width / 2.0) / zoom;
    }

    private double worldZ(double screenY) {
        return centerZ + (screenY - height / 2.0) / zoom;
    }

    private float screenX(double worldX) {
        return (float) (width / 2.0 + (worldX - centerX) * zoom);
    }

    private float screenY(double worldZ) {
        return (float) (height / 2.0 + (worldZ - centerZ) * zoom);
    }

    /** Курсор над самой картой, а не над панелью или кнопками инструментов. */
    private boolean overMap(double mouseX, double mouseY) {
        if (panel != null && panel.contains(mouseX, mouseY)) {
            return false;
        }
        int toolX = width - TOOL_SIZE - MARGIN;
        int toolsTop = MenuTabs.TAB_HEIGHT + MARGIN;
        int toolsBottom = toolsTop + Tool.values().length * (TOOL_SIZE + 2);
        return !(mouseX >= toolX && mouseX < toolX + TOOL_SIZE && mouseY >= toolsTop && mouseY < toolsBottom);
    }

    // ===== Управление =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Открытое меню: клик по пункту выполняет его, клик мимо — просто закрывает
        if (menu != null) {
            int index = menuIndexAt(mouseX, mouseY);
            List<MenuItem> items = menu;
            menu = null;
            if (index >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                items.get(index).action().run();
            }
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button) || !overMap(mouseX, mouseY)) {
            return true;
        }
        if (tool == Tool.DRAW) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                MapDrawing.beginStroke();
                lastDrawX = worldX(mouseX);
                lastDrawZ = worldZ(mouseY);
                drawTo(mouseX, mouseY, button);
            }
            pressedButton = button;
            return true;
        }
        pressedButton = button;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressText(mouseX, mouseY);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        openMenu(mouseX, mouseY);
        return true;
    }

    // Левый клик «Рукой»: маркер или выделенная надпись — тянуть её; другая надпись — выделить
    // (двойной клик — открыть её окно); мимо — снять выделение, дальше карта двигается как обычно
    private void pressText(double mouseX, double mouseY) {
        if (textEditor.press(font, mouseX, mouseY)) {
            openOnDoubleClick(textEditor.selected());
            return;
        }
        MapText text = textAt(mouseX, mouseY);
        textEditor.select(text);
        if (text != null) {
            textEditor.beginMove(mouseX, mouseY);
            openOnDoubleClick(text);
        }
    }

    private void openOnDoubleClick(MapText text) {
        long now = Util.getMillis();
        if (text == lastClickedText && now - lastClickMillis < DOUBLE_CLICK_MILLIS) {
            textEditor.release();
            textEditor.select(null);
            lastClickedText = null;
            minecraft.setScreen(MapTextEditScreen.edit(this, text, zoom));
            return;
        }
        lastClickedText = text;
        lastClickMillis = now;
    }

    // Отрезок от прошлой точки до курсора: левой — цветом, правой — ластиком
    private void drawTo(double mouseX, double mouseY, int button) {
        double x = worldX(mouseX);
        double z = worldZ(mouseY);
        int color = button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? 0xFF000000 | INKS[ink] : 0;
        MapDrawing.line(lastDrawX, lastDrawZ, x, z, brushSize, color);
        lastDrawX = x;
        lastDrawZ = z;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressedButton != button) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (textEditor.dragging()) {
            textEditor.drag(font, mouseX, mouseY, hasShiftDown());
            return true;
        }
        boolean pan = tool == Tool.DRAW ? button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE : button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (pan) {
            centerX -= dragX / zoom;
            centerZ -= dragY / zoom;
        } else if (tool == Tool.DRAW) {
            drawTo(mouseX, mouseY, button);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressedButton == button) {
            pressedButton = -1;
            MapDrawing.endStroke();
            if (textEditor.release()) {
                MapTexts.changed();
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // Над панелью — прокрутка её; над картой — масштаб к курсору: точка мира под курсором остаётся на месте
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        if (panel != null && panel.contains(mouseX, mouseY)) {
            panel.scrollBy(scrollY);
            return true;
        }
        menu = null; // меню привязано к точке карты — после масштаба оно было бы не там
        double anchorX = worldX(mouseX);
        double anchorZ = worldZ(mouseY);
        double scale = minecraft.getWindow().getGuiScale();
        double real = zoom * scale;
        if (scrollY > 0) {
            real = real < 1 - 1e-6 ? real * 2 : real * ZOOM_STEP;
        } else {
            real = real <= 1 + 1e-6 ? real / 2 : Math.max(1, real / ZOOM_STEP); // из плавных шагов — сначала ровно на 1
        }
        zoom = (float) Mth.clamp(real / scale, MIN_REAL_ZOOM / scale, MAX_ZOOM);
        centerX = anchorX - (mouseX - width / 2.0) / zoom;
        centerZ = anchorZ - (mouseY - height / 2.0) / zoom;
        if (pressedButton >= 0 && tool == Tool.DRAW) {
            lastDrawX = worldX(mouseX);
            lastDrawZ = worldZ(mouseY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (menu != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            menu = null;
            return true;
        }
        // Выделенная надпись: Esc — снять выделение, Delete — удалить
        MapText selected = textEditor.selected();
        if (selected != null && !textEditor.dragging()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                textEditor.select(null);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                textEditor.select(null);
                MapTexts.remove(selected);
                return true;
            }
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
        rememberScroll();
        MapDrawing.endStroke();
        MapStyle.saveIfChanged();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
