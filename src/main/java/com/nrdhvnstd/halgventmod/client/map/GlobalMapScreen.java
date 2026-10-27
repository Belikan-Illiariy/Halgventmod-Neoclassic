package com.nrdhvnstd.halgventmod.client.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.MenuTab;
import com.nrdhvnstd.halgventmod.client.MenuTabButton;
import com.nrdhvnstd.halgventmod.client.MenuTabScreen;
import com.nrdhvnstd.halgventmod.client.MenuTabs;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Глобальная карта Хальгвента: нарисованная карта (GlobalMapData) лежит плоскостью, камера смотрит на неё
 * строго сверху (TILT) и вращается только вокруг вертикали (ПКМ-перетаскивание по горизонтали, Q / E).
 * ЛКМ-перетаскивание — сдвиг, колёсико — приблизить / отдалить; дальше предела — смена масштаба.
 * На время рисования рисунка окно ставит свою перспективную проекцию, интерфейс поверх — обычный.
 *
 * «Весь Хальгвент»: весь рисунок, границы регионов и только главные локации (MapPoint.major). Регион под курсором
 * поднимается над плоскостью с тенью, контур светится, рядом — название; ЛКМ — пролёт в фокус на регионе.
 * «Фокус на регионе»: регион на весь экран, остальные затемнены, видны все его локации. Камера ушла серединой
 * в соседний регион — фокус переходит на него; в море или за край рисунка — сдвиг в ту сторону не принимается.
 * Esc, ПКМ-клик или отдаление — обратно на «Весь Хальгвент»; там Esc закрывает карту.
 * ЛКМ по локации — окно локации: миниатюра её карты, сведения и описание, «Открыть карту» (LocationMapScreen
 * любой локации — неизведанная откроется туманом).
 * Режим выбора места (окно точки администрации): ЛКМ ставит место, Enter или «Готово» — вернуть, Esc — без изменений.
 */
public class GlobalMapScreen extends Screen implements MenuTabScreen {
    private static final float TILT = 90;            // градусов над плоскостью: взгляд строго сверху
    private static final float FOV = 35;             // градусов по вертикали
    private static final float LIFT = 6;             // ⚠️ подъём региона под курсором, пикселей интерфейса
    private static final float LIFT_SECONDS = 0.15F; // ⚠️
    private static final float FLY_SECONDS = 0.4F;   // ⚠️
    private static final float FOCUS_DIM = 0.55F;    // ⚠️
    private static final int CLICK_SLOP = 3;         // как на карте мира
    private static final float ROTATE_STEP = 5;      // градусов на нажатие Q / E
    private static final float ROTATE_PER_PIXEL = 0.4F;
    private static final float ZOOM_FACTOR = 0.85F;
    private static final float WHOLE_MIN = 0.35F;    // ближе — доля расстояния «весь рисунок»
    private static final float FOCUS_MIN = 0.25F;
    private static final float FIT_MARGIN = 1.12F;

    private static final ResourceLocation POINTS = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID,
            "textures/gui/global_map/points.png");
    private static final int POINT_CELL = 32;
    private static final int POINT_SIZE = 16;
    private static final int POINT_LIFT = 3;         // точка под курсором приподнимается
    private static final int CELL_NORMAL = 0;
    private static final int CELL_MAJOR = 1;
    private static final int CELL_UNKNOWN = 2;

    private static final int MARGIN = 4;
    private static final int COLOR_BACK = 0xFF1A2430;      // море вокруг рисунка
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_DIM = 0xFFB8A888;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_PANEL = 0xF0241C14;
    private static final int COLOR_FRAME = 0xFF6E6250;
    private static final int OUTLINE = 0xC03A2A1A;
    private static final int OUTLINE_HOVER = 0xFFFFE8A0;
    private static final int SHADOW = 0x70000000;

    // Окно локации
    private static final int WINDOW_MAX_WIDTH = 280;  // ⚠️
    private static final float WINDOW_SHARE = 0.45F;  // ⚠️ доля ширины экрана
    private static final int WINDOW_PAD = 8;
    private static final int OPEN_BUTTON_WIDTH = 110;

    private enum Mode { WHOLE, FOCUS }

    private Mode mode = Mode.WHOLE;
    @Nullable
    private GlobalMapData.Region focused;
    private double targetX;
    private double targetZ;
    private double distance;
    private float yaw;
    private boolean placed;
    // Чем окно попросили открыть: точкой (её регион) или ближайшей к игроку
    @Nullable
    private MapPoint pendingFocus;
    private boolean pendingNearest;

    // Пролёт
    private long flyStart = -1;
    private double flyFromX;
    private double flyFromZ;
    private double flyFromDistance;
    private double flyToX;
    private double flyToZ;
    private double flyToDistance;

    // Подъём регионов под курсором: 0..1
    private final Map<String, Float> lifts = new HashMap<>();
    private long lastFrame = Util.getMillis();
    @Nullable
    private GlobalMapData.Region hovered;

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f combined = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();

    private int pressedButton = -1;
    private double pressX;
    private double pressY;
    private boolean dragged;

    // Окно локации; 0 — закрыто
    private int windowPoint;
    private int windowScroll;
    private Button openButton;

    // Выбор места для точки администрации
    @Nullable
    private final Screen pickParent;
    @Nullable
    private final BiConsumer<Integer, Integer> pickResult;
    private int pickX = MapPoint.NO_GLOBAL;
    private int pickY = MapPoint.NO_GLOBAL;

    public GlobalMapScreen() {
        this(null, null, MapPoint.NO_GLOBAL, MapPoint.NO_GLOBAL);
    }

    private GlobalMapScreen(@Nullable Screen pickParent, @Nullable BiConsumer<Integer, Integer> pickResult, int x, int y) {
        super(Component.translatable("gui.halgventmod.global_map.title"));
        this.pickParent = pickParent;
        this.pickResult = pickResult;
        this.pickX = x;
        this.pickY = y;
    }

    /** Выбор места точки на рисунке: result получает (x, y) после «Готово»; Esc — без изменений. */
    public static GlobalMapScreen pick(Screen parent, int x, int y, BiConsumer<Integer, Integer> result) {
        return new GlobalMapScreen(parent, result, x, y);
    }

    private boolean picking() {
        return pickResult != null;
    }

    @Override
    public MenuTab tab() {
        return MenuTab.MAP;
    }

    /** Открыться в фокусе на регионе этой локации (не на рисунке — «Весь Хальгвент»). */
    public void focusOn(MapPoint point) {
        pendingFocus = point.copy();
        pendingNearest = false;
        windowPoint = 0;
        if (placed) {
            applyPending(true);
        }
    }

    /** Открыться в фокусе на регионе ближайшей к игроку локации этого измерения. */
    public void focusNearest() {
        pendingNearest = true;
        pendingFocus = null;
    }

    // ===== Окно =====

    @Override
    protected void init() {
        if (!picking()) {
            for (MenuTabButton button : MenuTabs.createButtons(font, 0, width, MenuTab.MAP)) {
                addRenderableWidget(button);
            }
        }
        if (GlobalMapData.loaded() && !placed) {
            placed = true;
            targetX = GlobalMapData.width() / 2.0;
            targetZ = GlobalMapData.height() / 2.0;
            distance = wholeDistance();
            applyPending(false);
        }
        if (picking()) {
            int y = height - MARGIN - 20;
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.global_map.pick_done"),
                    button -> finishPick(true)).bounds(width / 2 - 104, y, 100, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> finishPick(false))
                    .bounds(width / 2 + 4, y, 100, 20).build());
        }
        if (windowPoint != 0) {
            int[] box = windowBox();
            openButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.global_map.open_map"),
                    button -> openLocationMap()).bounds(box[0] + box[2] - WINDOW_PAD - OPEN_BUTTON_WIDTH,
                    box[1] + box[3] - WINDOW_PAD - 20, OPEN_BUTTON_WIDTH, 20).build());
        }
    }

    private void applyPending(boolean fly) {
        MapPoint point = pendingFocus;
        if (pendingNearest) {
            point = nearestLocation();
        }
        pendingFocus = null;
        pendingNearest = false;
        GlobalMapData.Region region = point == null ? null : GlobalMapData.regionOf(point);
        if (region == null) {
            return; // не на рисунке — «Весь Хальгвент»
        }
        if (fly) {
            focus(region);
        } else {
            mode = Mode.FOCUS;
            focused = region;
            targetX = region.centerX();
            targetZ = region.centerY();
            distance = focusDistance(region);
        }
    }

    @Nullable
    private MapPoint nearestLocation() {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }
        MapPoint best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MapPoint point : PointShapes.here()) {
            if (point.isSpawn() || !point.onGlobalMap()) {
                continue;
            }
            double d = player.distanceToSqr(point.x + 0.5, player.getY(), point.z + 0.5);
            if (d < bestDistance) {
                bestDistance = d;
                best = point;
            }
        }
        return best;
    }

    // ===== Камера =====

    private double fitDistance(double w, double h) {
        double tanHalf = Math.tan(Math.toRadians(FOV / 2));
        double aspect = (double) width / height;
        double byHeight = h * Math.sin(Math.toRadians(TILT)) / (2 * tanHalf);
        double byWidth = w / (2 * tanHalf * aspect);
        return Math.max(byHeight, byWidth) * FIT_MARGIN;
    }

    private double wholeDistance() {
        return fitDistance(GlobalMapData.width(), GlobalMapData.height());
    }

    private double focusDistance(GlobalMapData.Region region) {
        return fitDistance(region.maxX() - region.minX() + 1, region.maxY() - region.minY() + 1);
    }

    private double minDistance() {
        return mode == Mode.FOCUS && focused != null ? focusDistance(focused) * FOCUS_MIN : wholeDistance() * WHOLE_MIN;
    }

    private double maxDistance() {
        return mode == Mode.FOCUS && focused != null ? focusDistance(focused) : wholeDistance();
    }

    private void focus(GlobalMapData.Region region) {
        mode = Mode.FOCUS;
        focused = region;
        fly(region.centerX(), region.centerY(), focusDistance(region));
    }

    private void whole() {
        mode = Mode.WHOLE;
        focused = null;
        fly(GlobalMapData.width() / 2.0, GlobalMapData.height() / 2.0, wholeDistance());
    }

    private void fly(double x, double z, double toDistance) {
        flyFromX = targetX;
        flyFromZ = targetZ;
        flyFromDistance = distance;
        flyToX = x;
        flyToZ = z;
        flyToDistance = toDistance;
        flyStart = Util.getMillis();
    }

    private void updateFlight() {
        if (flyStart < 0) {
            return;
        }
        float t = Math.min(1, (Util.getMillis() - flyStart) / (FLY_SECONDS * 1000));
        float eased = t * t * (3 - 2 * t);
        targetX = Mth.lerp(eased, flyFromX, flyToX);
        targetZ = Mth.lerp(eased, flyFromZ, flyToZ);
        distance = Math.exp(Mth.lerp(eased, Math.log(flyFromDistance), Math.log(flyToDistance)));
        if (t >= 1) {
            flyStart = -1;
        }
    }

    private void updateMatrices() {
        double pitch = Math.toRadians(TILT);
        double angle = Math.toRadians(yaw);
        float eyeX = (float) (targetX + distance * Math.sin(angle) * Math.cos(pitch));
        float eyeY = (float) (distance * Math.sin(pitch));
        float eyeZ = (float) (targetZ + distance * Math.cos(angle) * Math.cos(pitch));
        // Верх экрана — по повороту камеры: при взгляде сверху вертикаль совпадает с направлением взгляда
        view.identity().lookAt(eyeX, eyeY, eyeZ, (float) targetX, 0, (float) targetZ,
                (float) -Math.sin(angle), 0, (float) -Math.cos(angle));
        projection.identity().perspective((float) Math.toRadians(FOV), (float) width / height,
                (float) (distance * 0.05), (float) (distance * 20));
        projection.mul(view, combined);
        combined.invert(inverse);
    }

    /** Точка плоскости (x; высота; z) — на экран (пиксели интерфейса); null — за камерой. */
    @Nullable
    private float[] project(double x, double y, double z) {
        Vector4f clip = new Vector4f((float) x, (float) y, (float) z, 1).mul(combined);
        if (clip.w <= 0) {
            return null;
        }
        return new float[] {(clip.x / clip.w + 1) / 2 * width, (1 - clip.y / clip.w) / 2 * height};
    }

    /** Луч из курсора на плоскость: {x, z} в пикселях рисунка; null — мимо. */
    @Nullable
    private double[] hit(double mouseX, double mouseY) {
        float ndcX = (float) (mouseX / width * 2 - 1);
        float ndcY = (float) (1 - mouseY / height * 2);
        Vector4f near = new Vector4f(ndcX, ndcY, -1, 1).mul(inverse);
        Vector4f far = new Vector4f(ndcX, ndcY, 1, 1).mul(inverse);
        Vector3f from = new Vector3f(near.x / near.w, near.y / near.w, near.z / near.w);
        Vector3f to = new Vector3f(far.x / far.w, far.y / far.w, far.z / far.w);
        float dy = to.y - from.y;
        if (Math.abs(dy) < 1e-6) {
            return null;
        }
        float t = -from.y / dy;
        if (t < 0) {
            return null;
        }
        return new double[] {from.x + (to.x - from.x) * t, from.z + (to.z - from.z) * t};
    }

    /** Пикселей рисунка на пиксель интерфейса у середины экрана. */
    private double worldPerPixel() {
        return 2 * distance * Math.tan(Math.toRadians(FOV / 2)) / height;
    }

    // Сдвиг камеры: в фокусе — середина экрана не уходит в море и за край; в другой регион — фокус на нём
    private void moveTarget(double newX, double newZ) {
        if (mode == Mode.WHOLE || picking()) {
            targetX = Mth.clamp(newX, 0, GlobalMapData.width());
            targetZ = Mth.clamp(newZ, 0, GlobalMapData.height());
            return;
        }
        double[][] tries = {{newX, newZ}, {newX, targetZ}, {targetX, newZ}};
        for (double[] candidate : tries) {
            GlobalMapData.Region region = GlobalMapData.regionAt(candidate[0], candidate[1]);
            if (region != null) {
                targetX = candidate[0];
                targetZ = candidate[1];
                if (focused == null || !region.id().equals(focused.id())) {
                    focused = region; // камера ушла в соседний регион
                }
                return;
            }
        }
    }

    // ===== Рисование =====

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, COLOR_BACK);
        if (!GlobalMapData.loaded()) {
            return;
        }
        updateFlight();
        updateMatrices();
        long now = Util.getMillis();
        float dt = Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        hovered = windowPoint == 0 && mode == Mode.WHOLE && !picking() && pointAtMouse(mouseX, mouseY) == null
                && mouseY > MenuTabs.TAB_HEIGHT ? regionAtMouse(mouseX, mouseY) : null;
        for (GlobalMapData.Region region : GlobalMapData.regions()) {
            float target = region == hovered ? 1 : 0;
            float current = lifts.getOrDefault(region.id(), 0F);
            float step = dt / LIFT_SECONDS;
            lifts.put(region.id(), current < target ? Math.min(target, current + step) : Math.max(target, current - step));
        }
        renderPlane(graphics);
        renderLabels(graphics, mouseX, mouseY);
        if (windowPoint != 0) {
            renderWindow(graphics, mouseX, mouseY);
        }
    }

    private void renderPlane(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(combined, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Рисунок кусками
        int w = GlobalMapData.width();
        int h = GlobalMapData.height();
        for (int row = 0; row < GlobalMapData.rows(); row++) {
            for (int column = 0; column < GlobalMapData.columns(); column++) {
                int x0 = column * GlobalMapData.TILE;
                int z0 = row * GlobalMapData.TILE;
                quad(GlobalMapData.tile(column, row), x0, z0, Math.min(w, x0 + GlobalMapData.TILE),
                        Math.min(h, z0 + GlobalMapData.TILE), 0, 0xFFFFFFFF);
            }
        }
        double lift = LIFT * worldPerPixel();
        if (mode == Mode.FOCUS && focused != null) {
            // Остальное затемнено, регион в фокусе — как есть
            colorQuad(0, 0, w, h, 0.01F, (int) (FOCUS_DIM * 255) << 24);
            regionQuad(focused.cutout(), focused, 0.02F, 0xFFFFFFFF, 0);
        }
        for (GlobalMapData.Region region : GlobalMapData.regions()) {
            regionQuad(region.outline(), region, 0.03F, OUTLINE, 0);
        }
        // Поднятые регионы: тень на плоскости, сам кусок выше, контур светится
        for (GlobalMapData.Region region : GlobalMapData.regions()) {
            float amount = lifts.getOrDefault(region.id(), 0F);
            if (amount <= 0) {
                continue;
            }
            float eased = amount * amount * (3 - 2 * amount);
            float y = (float) (lift * eased);
            regionQuad(region.cutout(), region, 0.04F, (int) (eased * (SHADOW >>> 24)) << 24, (float) (lift * eased * 0.6));
            regionQuad(region.cutout(), region, y, 0xFFFFFFFF, 0);
            regionQuad(region.outline(), region, y + 0.01F, OUTLINE_HOVER, 0);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    private void regionQuad(ResourceLocation texture, GlobalMapData.Region region, float y, int color, float shift) {
        quad(texture, region.minX() + shift, region.minY() + shift, region.maxX() + 1 + shift, region.maxY() + 1 + shift,
                y, color);
    }

    // Прямоугольник текстуры на плоскости на высоте y; color — ARGB-множитель
    private static void quad(ResourceLocation texture, float x0, float z0, float x1, float z1, float y, int color) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(x0, y, z0).setUv(0, 0).setColor(color);
        builder.addVertex(x0, y, z1).setUv(0, 1).setColor(color);
        builder.addVertex(x1, y, z1).setUv(1, 1).setColor(color);
        builder.addVertex(x1, y, z0).setUv(1, 0).setColor(color);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        draw(builder);
    }

    private static void colorQuad(float x0, float z0, float x1, float z1, float y, int color) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(x0, y, z0).setColor(color);
        builder.addVertex(x0, y, z1).setColor(color);
        builder.addVertex(x1, y, z1).setColor(color);
        builder.addVertex(x1, y, z0).setColor(color);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        draw(builder);
    }

    private static void draw(BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    // Точки (стоят на плоскости, к камере лицом), их названия, название региона
    private void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MapPoint hoveredPoint = windowPoint == 0 ? pointAtMouse(mouseX, mouseY) : null;
        RenderSystem.enableBlend();
        for (MapPoint point : visiblePoints()) {
            float[] at = project(point.globalX + 0.5, 0, point.globalY + 0.5);
            if (at == null) {
                continue;
            }
            boolean open = PointShapes.discovered(point);
            int cell = !open ? CELL_UNKNOWN : point.major ? CELL_MAJOR : CELL_NORMAL;
            int x = Math.round(at[0]);
            int y = Math.round(at[1]) - (point == hoveredPoint ? POINT_LIFT : 0);
            if (point == hoveredPoint) {
                graphics.setColor(1.0F, 0.95F, 0.7F, 1.0F); // подсветка
            }
            graphics.blit(POINTS, x - POINT_SIZE / 2, y - POINT_SIZE, POINT_SIZE, POINT_SIZE, cell * POINT_CELL, 0,
                    POINT_CELL, POINT_CELL, 3 * POINT_CELL, POINT_CELL);
            graphics.setColor(1, 1, 1, 1);
            if (open) {
                graphics.drawCenteredString(font, point.name, x, y + 2, point == hoveredPoint ? COLOR_TITLE : COLOR_TEXT);
            }
        }
        RenderSystem.disableBlend();
        if (picking() && pickX != MapPoint.NO_GLOBAL) {
            float[] at = project(pickX + 0.5, 0, pickY + 0.5);
            if (at != null) {
                graphics.blit(POINTS, Math.round(at[0]) - POINT_SIZE / 2, Math.round(at[1]) - POINT_SIZE, POINT_SIZE,
                        POINT_SIZE, CELL_MAJOR * POINT_CELL, 0, POINT_CELL, POINT_CELL, 3 * POINT_CELL, POINT_CELL);
            }
        }
        // Название региона под курсором — над ним; в фокусе — вверху
        if (hovered != null) {
            float amount = lifts.getOrDefault(hovered.id(), 0F);
            float[] at = project(hovered.centerX(), LIFT * worldPerPixel() * amount, hovered.centerY());
            if (at != null) {
                graphics.drawCenteredString(font, hovered.title(), Math.round(at[0]), Math.round(at[1]), COLOR_TITLE);
            }
        }
    }

    /** Точки на экране: «Весь Хальгвент» — главные, фокус — все локации региона, выбор места — все. */
    private List<MapPoint> visiblePoints() {
        List<MapPoint> result = new ArrayList<>();
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.isSpawn() || !point.onGlobalMap()) {
                continue;
            }
            if (picking() || mode == Mode.WHOLE && point.major) {
                result.add(point);
            } else if (mode == Mode.FOCUS && focused != null) {
                GlobalMapData.Region region = GlobalMapData.regionOf(point);
                if (region != null && region.id().equals(focused.id())) {
                    result.add(point);
                }
            }
        }
        return result;
    }

    @Nullable
    private MapPoint pointAtMouse(double mouseX, double mouseY) {
        if (picking() || !GlobalMapData.loaded()) {
            return null;
        }
        updateMatrices();
        MapPoint found = null;
        double best = Double.MAX_VALUE;
        for (MapPoint point : visiblePoints()) {
            float[] at = project(point.globalX + 0.5, 0, point.globalY + 0.5);
            if (at == null) {
                continue;
            }
            // Значок стоит над точкой: от неё вверх на POINT_SIZE
            if (Math.abs(mouseX - at[0]) <= POINT_SIZE / 2.0 && mouseY <= at[1] && mouseY >= at[1] - POINT_SIZE) {
                double d = Math.abs(mouseX - at[0]) + Math.abs(mouseY - at[1]);
                if (d < best) {
                    best = d;
                    found = point;
                }
            }
        }
        return found;
    }

    @Nullable
    private GlobalMapData.Region regionAtMouse(double mouseX, double mouseY) {
        double[] at = hit(mouseX, mouseY);
        return at == null ? null : GlobalMapData.regionAt(at[0], at[1]);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!GlobalMapData.loaded()) {
            graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.global_map.missing"), width / 2,
                    height / 2, COLOR_TEXT);
            return;
        }
        int top = MenuTabs.TAB_HEIGHT + MARGIN;
        if (picking()) {
            graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.global_map.pick"), width / 2, MARGIN,
                    COLOR_TITLE);
            GlobalMapData.Region region = pickX == MapPoint.NO_GLOBAL ? null : GlobalMapData.regionAt(pickX + 0.5, pickY + 0.5);
            Component where = region != null ? region.title()
                    : Component.translatable("gui.halgventmod.global_map.not_on_map");
            graphics.drawCenteredString(font, where, width / 2, MARGIN + 12, COLOR_TEXT);
            return;
        }
        Component heading = mode == Mode.FOCUS && focused != null ? focused.title()
                : Component.translatable("gui.halgventmod.global_map.whole");
        graphics.drawCenteredString(font, heading, width / 2, top + 4, COLOR_TITLE);
        if (windowPoint == 0) {
            MapPoint point = pointAtMouse(mouseX, mouseY);
            if (point != null && !PointShapes.discovered(point) && !point.hoverText.isEmpty()) {
                graphics.renderTooltip(font, font.split(Component.literal(point.hoverText), 200), mouseX, mouseY);
            }
        }
    }

    // ===== Окно локации =====

    @Nullable
    private MapPoint windowPointData() {
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.id == windowPoint) {
                return point;
            }
        }
        return null;
    }

    /** {x, y, ширина, высота}. */
    private int[] windowBox() {
        int w = Math.min(WINDOW_MAX_WIDTH, Math.round(width * WINDOW_SHARE));
        int top = MenuTabs.TAB_HEIGHT + 2 * MARGIN + 12;
        int h = height - top - 2 * MARGIN;
        return new int[] {(width - w) / 2, top, w, h};
    }

    private int[] miniatureBox() {
        int[] box = windowBox();
        int size = Math.min(box[2] - 2 * WINDOW_PAD, box[3] / 2);
        return new int[] {box[0] + (box[2] - size) / 2, box[1] + WINDOW_PAD + 14, size};
    }

    private int[] textBox() {
        int[] box = windowBox();
        int[] mini = miniatureBox();
        int top = mini[1] + mini[2] + WINDOW_PAD;
        int bottom = box[1] + box[3] - WINDOW_PAD - 20 - 4;
        return new int[] {box[0] + WINDOW_PAD, top, box[2] - 2 * WINDOW_PAD - 6, Math.max(0, bottom - top)};
    }

    private boolean overClose(double mouseX, double mouseY) {
        int[] box = windowBox();
        return mouseX >= box[0] + box[2] - 16 && mouseX < box[0] + box[2] - 4 && mouseY >= box[1] + 4
                && mouseY < box[1] + 16;
    }

    private boolean overWindow(double mouseX, double mouseY) {
        int[] box = windowBox();
        return mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3];
    }

    private void openWindow(MapPoint point) {
        windowPoint = point.id;
        windowScroll = 0;
        rebuildWidgets();
    }

    private void closeWindow() {
        windowPoint = 0;
        rebuildWidgets();
    }

    private void openLocationMap() {
        if (windowPoint != 0) {
            minecraft.setScreen(new LocationMapScreen(windowPoint, this));
        }
    }

    // Строки сведений и описания: у открытой — info, потом description («Слово: текст» — слово тусклым); у неоткрытой — hoverText
    private List<FormattedCharSequence> windowLines(MapPoint point, int textWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (!PointShapes.discovered(point)) {
            for (String paragraph : point.hoverText.split("\n")) {
                lines.addAll(font.split(Component.literal(paragraph).withColor(COLOR_TEXT), textWidth));
            }
            return lines;
        }
        if (!point.info.isEmpty()) {
            lines.addAll(font.split(Component.translatable("gui.halgventmod.point.info").withColor(COLOR_TITLE), textWidth));
            for (String paragraph : point.info.split("\n")) {
                lines.addAll(font.split(keyed(paragraph), textWidth));
            }
        }
        if (!point.description.isEmpty()) {
            lines.addAll(font.split(Component.translatable("gui.halgventmod.point.description").withColor(COLOR_TITLE),
                    textWidth));
            for (String paragraph : point.description.split("\n")) {
                lines.addAll(font.split(keyed(paragraph), textWidth));
            }
        }
        return lines;
    }

    private static MutableComponent keyed(String line) {
        int colon = line.indexOf(": ");
        if (colon <= 0) {
            return Component.literal(line).withColor(COLOR_TEXT);
        }
        return Component.literal(line.substring(0, colon + 1)).withColor(COLOR_DIM)
                .append(Component.literal(line.substring(colon + 1)).withColor(COLOR_TEXT));
    }

    private void renderWindow(GuiGraphics graphics, int mouseX, int mouseY) {
        MapPoint point = windowPointData();
        if (point == null) {
            windowPoint = 0; // точку удалили
            return;
        }
        int[] box = windowBox();
        graphics.fill(box[0] - 1, box[1] - 1, box[0] + box[2] + 1, box[1] + box[3] + 1, COLOR_FRAME);
        graphics.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], COLOR_PANEL);
        boolean open = PointShapes.discovered(point);
        Component title = open ? Component.literal(point.name) : Component.translatable("gui.halgventmod.point.unknown");
        graphics.drawCenteredString(font, title, box[0] + box[2] / 2, box[1] + WINDOW_PAD - 2, COLOR_TITLE);
        graphics.drawString(font, "×", box[0] + box[2] - 13, box[1] + 5,
                overClose(mouseX, mouseY) ? COLOR_TITLE : COLOR_DIM, false);

        // Миниатюра карты локации: лист, вписанный в квадрат
        int[] mini = miniatureBox();
        graphics.fill(mini[0] - 1, mini[1] - 1, mini[0] + mini[2] + 1, mini[1] + mini[2] + 1, COLOR_FRAME);
        MapView miniView = miniatureView(point, mini);
        boolean here = minecraft.level != null && point.dimension.equals(minecraft.level.dimension().location().toString());
        graphics.enableScissor(mini[0], mini[1], mini[0] + mini[2], mini[1] + mini[2]);
        graphics.blit(LocationSheet.TABLE, mini[0], mini[1], 0, 0, mini[2], mini[2], LocationSheet.TABLE_SIZE,
                LocationSheet.TABLE_SIZE);
        LocationSheet.render(graphics, miniView, point, open && here, mini[0], mini[1], mini[0] + mini[2], mini[1] + mini[2]);
        if (!open) {
            LocationSheet.renderUnexplored(graphics, font, miniView, point);
        }
        graphics.disableScissor();

        // Сведения и описание с прокруткой
        int[] text = textBox();
        List<FormattedCharSequence> lines = windowLines(point, text[2]);
        int visible = Math.max(1, text[3] / font.lineHeight);
        windowScroll = Mth.clamp(windowScroll, 0, Math.max(0, lines.size() - visible));
        graphics.enableScissor(text[0], text[1], text[0] + text[2] + 6, text[1] + text[3]);
        for (int i = 0; i < visible && windowScroll + i < lines.size(); i++) {
            graphics.drawString(font, lines.get(windowScroll + i), text[0], text[1] + i * font.lineHeight, COLOR_TEXT, false);
        }
        graphics.disableScissor();
        if (lines.size() > visible) {
            int barX = text[0] + text[2] + 2;
            int barHeight = Math.max(8, text[3] * visible / lines.size());
            int barY = text[1] + (text[3] - barHeight) * windowScroll / Math.max(1, lines.size() - visible);
            graphics.fill(barX, text[1], barX + 3, text[1] + text[3], 0x60000000);
            graphics.fill(barX, barY, barX + 3, barY + barHeight, COLOR_FRAME);
        }
    }

    // Лист вписан в квадрат миниатюры
    private static MapView miniatureView(MapPoint point, int[] mini) {
        int[] rect = LocationSheet.rect(point);
        double centerX = (rect[0] + rect[2]) / 2.0;
        double centerZ = (rect[1] + rect[3]) / 2.0;
        float zoom = (float) mini[2] / Math.max(rect[2] - rect[0], rect[3] - rect[1]);
        double middleX = mini[0] + mini[2] / 2.0;
        double middleY = mini[1] + mini[2] / 2.0;
        return new MapView() {
            @Override
            public float screenX(double worldX) {
                return (float) (middleX + (worldX - centerX) * zoom);
            }

            @Override
            public float screenY(double worldZ) {
                return (float) (middleY + (worldZ - centerZ) * zoom);
            }

            @Override
            public double worldX(double screenX) {
                return centerX + (screenX - middleX) / zoom;
            }

            @Override
            public double worldZ(double screenY) {
                return centerZ + (screenY - middleY) / zoom;
            }

            @Override
            public float zoom() {
                return zoom;
            }
        };
    }

    // ===== Управление =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (windowPoint != 0) {
            if (overClose(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                closeWindow();
                return true;
            }
            if (!overWindow(mouseX, mouseY)) {
                closeWindow(); // клик мимо — закрыть
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (super.mouseClicked(mouseX, mouseY, button) || !GlobalMapData.loaded()) {
            return true;
        }
        pressedButton = button;
        pressX = mouseX;
        pressY = mouseY;
        dragged = false;
        flyStart = -1;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressedButton != button || windowPoint != 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (Math.abs(mouseX - pressX) > CLICK_SLOP || Math.abs(mouseY - pressY) > CLICK_SLOP) {
            dragged = true;
        }
        updateMatrices();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double[] before = hit(mouseX - dragX, mouseY - dragY);
            double[] after = hit(mouseX, mouseY);
            if (before != null && after != null) {
                moveTarget(targetX - (after[0] - before[0]), targetZ - (after[1] - before[1]));
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            yaw -= (float) dragX * ROTATE_PER_PIXEL; // только вокруг вертикали
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressedButton == button) {
            pressedButton = -1;
            if (!dragged) {
                clicked(mouseX, mouseY, button);
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void clicked(double mouseX, double mouseY, int button) {
        updateMatrices();
        if (picking()) {
            double[] at = hit(mouseX, mouseY);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && at != null && at[0] >= 0 && at[1] >= 0
                    && at[0] < GlobalMapData.width() && at[1] < GlobalMapData.height()) {
                pickX = Mth.floor(at[0]);
                pickY = Mth.floor(at[1]);
            }
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (mode == Mode.FOCUS) {
                whole();
            }
            return;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        MapPoint point = pointAtMouse(mouseX, mouseY);
        if (point != null) {
            openWindow(point);
            return;
        }
        if (mode == Mode.WHOLE) {
            GlobalMapData.Region region = regionAtMouse(mouseX, mouseY);
            if (region != null) {
                focus(region);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (windowPoint != 0) {
            int[] text = textBox();
            if (mouseX >= text[0] && mouseX < text[0] + text[2] + 6 && mouseY >= text[1] && mouseY < text[1] + text[3]) {
                windowScroll -= (int) Math.signum(scrollY);
            }
            return true;
        }
        if (scrollY == 0 || !GlobalMapData.loaded()) {
            return false;
        }
        flyStart = -1;
        double wanted = scrollY > 0 ? distance * ZOOM_FACTOR : distance / ZOOM_FACTOR;
        if (wanted > maxDistance() * 1.001 && mode == Mode.FOCUS && !picking()) {
            whole(); // отдалили дальше региона — весь Хальгвент
            return true;
        }
        distance = Mth.clamp(wanted, minDistance(), maxDistance());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (windowPoint != 0 && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeWindow();
            return true;
        }
        if (picking()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishPick(true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishPick(false);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_Q) {
            yaw += ROTATE_STEP;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E) {
            yaw -= ROTATE_STEP;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && mode == Mode.FOCUS && !picking()) {
            whole();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void finishPick(boolean done) {
        if (done && pickResult != null) {
            pickResult.accept(pickX, pickY);
        }
        minecraft.setScreen(pickParent);
    }

    @Override
    public void onClose() {
        if (picking()) {
            finishPick(false);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Название региона точки или «не на карте» — для окна точки. */
    public static Component regionName(int x, int y) {
        GlobalMapData.Region region = x == MapPoint.NO_GLOBAL ? null : GlobalMapData.regionAt(x + 0.5, y + 0.5);
        return region != null ? region.title() : Component.translatable("gui.halgventmod.global_map.not_on_map")
                .withStyle(ChatFormatting.GRAY);
    }
}
