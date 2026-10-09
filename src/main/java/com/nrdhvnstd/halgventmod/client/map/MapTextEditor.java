package com.nrdhvnstd.halgventmod.client.map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * Рамка выделенной надписи, как трансформация текста в Photoshop.
 * Тянуть за саму надпись — двигать; за угловые квадраты — менять размер;
 * за ромб посередине верхней стороны — изгибать дугой (вверх — горкой, вниз — чашей);
 * за кружок над рамкой — вращать (с Shift — шагами по 15°).
 * Рамка рисуется по границам букв, поэтому у изогнутой надписи она тоже точная.
 */
final class MapTextEditor {
    private static final int HANDLE = 3;            // полуразмер маркера, пикселей экрана
    private static final int GRAB = 5;              // насколько далеко от маркера ещё можно его схватить
    private static final float ROTATE_OFFSET = 16;  // кружок поворота — на столько пикселей над рамкой
    private static final float FRAME_PAD = 2;       // рамка чуть шире букв, в пикселях экрана
    private static final float SNAP_DEGREES = 15;
    private static final float ARC_SNAP = 4;        // почти прямая — прямая

    private static final int COLOR_FRAME = 0xFFE8DCC0;
    private static final int COLOR_SHADOW = 0xA0000000;
    private static final int COLOR_HANDLE = 0xFFFFFFFF;
    private static final int COLOR_BEND = 0xFFFFE8A0;
    private static final int COLOR_ROTATE = 0xFF8FD0FF;

    /** Чем занят перетаскиванием. */
    private enum Drag { NONE, MOVE, SCALE, BEND, ROTATE }

    /** Перевод мира в экран — из окна карты. */
    interface View {
        float screenX(double worldX);

        float screenY(double worldZ);

        float zoom();
    }

    private final View view;
    private MapText selected;
    private Drag drag = Drag.NONE;
    private boolean changed;

    // Что было в начале перетаскивания
    private double startMouseX;
    private double startMouseY;
    private int startX;
    private int startZ;
    private float startScale;
    private float startArc;
    private float startLocalY;
    private float startDistance;

    MapTextEditor(View view) {
        this.view = view;
    }

    MapText selected() {
        return selected;
    }

    void select(MapText text) {
        selected = text;
        drag = Drag.NONE;
    }

    boolean dragging() {
        return drag != Drag.NONE;
    }

    private float centerX() {
        return view.screenX(selected.x + 0.5);
    }

    private float centerY() {
        return view.screenY(selected.z + 0.5);
    }

    // ===== Геометрия рамки, в пикселях экрана =====

    /** Углы рамки: левый верхний, правый верхний, правый нижний, левый нижний — по 2 числа. */
    private float[] corners(Font font) {
        float[] b = MapText.bounds(font, selected.text, selected.arc);
        float pad = FRAME_PAD / selected.screenScale(view.zoom());
        float[] tl = selected.toScreen(view.zoom(), centerX(), centerY(), b[0] - pad, b[1] - pad);
        float[] tr = selected.toScreen(view.zoom(), centerX(), centerY(), b[2] + pad, b[1] - pad);
        float[] br = selected.toScreen(view.zoom(), centerX(), centerY(), b[2] + pad, b[3] + pad);
        float[] bl = selected.toScreen(view.zoom(), centerX(), centerY(), b[0] - pad, b[3] + pad);
        return new float[] {tl[0], tl[1], tr[0], tr[1], br[0], br[1], bl[0], bl[1]};
    }

    /** Середина верхней стороны — маркер изгиба. */
    private float[] bendHandle(float[] c) {
        return new float[] {(c[0] + c[2]) / 2, (c[1] + c[3]) / 2};
    }

    /** Кружок поворота: над серединой верхней стороны, по направлению «вверх» надписи. */
    private float[] rotateHandle(float[] c) {
        float[] top = bendHandle(c);
        float angle = selected.rotation * Mth.DEG_TO_RAD;
        return new float[] {top[0] + Mth.sin(angle) * ROTATE_OFFSET, top[1] - Mth.cos(angle) * ROTATE_OFFSET};
    }

    private static boolean near(float[] point, double mouseX, double mouseY) {
        return Math.abs(mouseX - point[0]) <= GRAB && Math.abs(mouseY - point[1]) <= GRAB;
    }

    // ===== Мышь =====

    /**
     * Нажатие левой кнопкой при выделенной надписи: маркер или сама надпись — начинаем тянуть (true).
     * Мимо — false: окно карты решит, выделить другую надпись или снять выделение.
     */
    boolean press(Font font, double mouseX, double mouseY) {
        if (selected == null) {
            return false;
        }
        float[] c = corners(font);
        Drag what = Drag.NONE;
        if (near(rotateHandle(c), mouseX, mouseY)) {
            what = Drag.ROTATE;
        } else if (near(bendHandle(c), mouseX, mouseY)) {
            what = Drag.BEND;
        } else {
            for (int i = 0; i < 4; i++) {
                if (near(new float[] {c[i * 2], c[i * 2 + 1]}, mouseX, mouseY)) {
                    what = Drag.SCALE;
                    break;
                }
            }
        }
        if (what == Drag.NONE && selected.contains(font, view.zoom(), centerX(), centerY(), mouseX, mouseY)) {
            what = Drag.MOVE;
        }
        if (what == Drag.NONE) {
            return false;
        }
        begin(what, mouseX, mouseY);
        return true;
    }

    /** Начать тянуть только что выделенную надпись за неё саму. */
    void beginMove(double mouseX, double mouseY) {
        begin(Drag.MOVE, mouseX, mouseY);
    }

    private void begin(Drag what, double mouseX, double mouseY) {
        drag = what;
        startMouseX = mouseX;
        startMouseY = mouseY;
        startX = selected.x;
        startZ = selected.z;
        startScale = selected.scale;
        startArc = selected.arc;
        startLocalY = selected.toLocal(view.zoom(), centerX(), centerY(), mouseX, mouseY)[1];
        startDistance = (float) Math.max(1, Math.hypot(mouseX - centerX(), mouseY - centerY()));
    }

    void drag(Font font, double mouseX, double mouseY, boolean snap) {
        if (selected == null) {
            return;
        }
        switch (drag) {
            case MOVE -> {
                selected.x = startX + (int) Math.round((mouseX - startMouseX) / view.zoom());
                selected.z = startZ + (int) Math.round((mouseY - startMouseY) / view.zoom());
            }
            case SCALE -> {
                float distance = (float) Math.hypot(mouseX - centerX(), mouseY - centerY());
                selected.scale = Mth.clamp(startScale * distance / startDistance, MapText.MIN_SCALE, MapText.MAX_SCALE);
            }
            case BEND -> {
                // Вверх на полширины надписи — полукруг; поворот надписи учитывается
                float halfWidth = Math.max(1, font.width(selected.text) / 2.0F);
                float localY = selected.toLocal(view.zoom(), centerX(), centerY(), mouseX, mouseY)[1];
                float arc = startArc + (startLocalY - localY) / halfWidth * 180;
                selected.arc = Math.abs(arc) < ARC_SNAP ? 0 : Mth.clamp(arc, -MapText.MAX_ARC, MapText.MAX_ARC);
            }
            case ROTATE -> {
                float angle = (float) Math.toDegrees(Math.atan2(mouseY - centerY(), mouseX - centerX())) + 90;
                if (snap) {
                    angle = Math.round(angle / SNAP_DEGREES) * SNAP_DEGREES;
                }
                selected.rotation = Mth.wrapDegrees(angle);
            }
            case NONE -> {
                return;
            }
        }
        changed = true;
    }

    /** Отпустили кнопку: true — надпись поменялась, её пора записать. */
    boolean release() {
        boolean wasChanged = changed;
        drag = Drag.NONE;
        changed = false;
        return wasChanged;
    }

    // ===== Рисование =====

    void render(GuiGraphics graphics, Font font) {
        if (selected == null) {
            return;
        }
        float[] c = corners(font);
        float[] top = bendHandle(c);
        float[] rotate = rotateHandle(c);
        // Рамка с тенью, чтобы её было видно и на светлой, и на тёмной карте
        for (int pass = 0; pass < 2; pass++) {
            int color = pass == 0 ? COLOR_SHADOW : COLOR_FRAME;
            float offset = pass == 0 ? 1 : 0;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                line(graphics, c[i * 2] + offset, c[i * 2 + 1] + offset, c[j * 2] + offset, c[j * 2 + 1] + offset, color);
            }
            line(graphics, top[0] + offset, top[1] + offset, rotate[0] + offset, rotate[1] + offset, color);
        }
        for (int i = 0; i < 4; i++) {
            square(graphics, c[i * 2], c[i * 2 + 1], COLOR_HANDLE);
        }
        diamond(graphics, top[0], top[1], COLOR_BEND);
        square(graphics, rotate[0], rotate[1], COLOR_ROTATE);
    }

    private static void square(GuiGraphics graphics, float x, float y, int color) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        graphics.fill(ix - HANDLE - 1, iy - HANDLE - 1, ix + HANDLE + 1, iy + HANDLE + 1, 0xFF000000);
        graphics.fill(ix - HANDLE, iy - HANDLE, ix + HANDLE, iy + HANDLE, color);
    }

    // Ромб — квадрат, повёрнутый на 45°, с тёмной каймой
    private static void diamond(GuiGraphics graphics, float x, float y, int color) {
        quad(graphics, x, y - HANDLE - 2.5F, x + HANDLE + 2.5F, y, x, y + HANDLE + 2.5F, x - HANDLE - 2.5F, y, 0xFF000000);
        quad(graphics, x, y - HANDLE - 1, x + HANDLE + 1, y, x, y + HANDLE + 1, x - HANDLE - 1, y, color);
    }

    // Отрезок толщиной в пиксель под любым углом
    private static void line(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length < 0.01F) {
            return;
        }
        float nx = -(y2 - y1) / length * 0.5F;
        float ny = (x2 - x1) / length * 0.5F;
        quad(graphics, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, color);
    }

    // Четырёхугольник; обход в обе стороны, чтобы его не отбросило отсечение задних граней
    private static void quad(GuiGraphics graphics, float x1, float y1, float x2, float y2, float x3, float y3,
                             float x4, float y4, int color) {
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        consumer.addVertex(pose, x1, y1, 0).setColor(color);
        consumer.addVertex(pose, x2, y2, 0).setColor(color);
        consumer.addVertex(pose, x3, y3, 0).setColor(color);
        consumer.addVertex(pose, x4, y4, 0).setColor(color);
        consumer.addVertex(pose, x4, y4, 0).setColor(color);
        consumer.addVertex(pose, x3, y3, 0).setColor(color);
        consumer.addVertex(pose, x2, y2, 0).setColor(color);
        consumer.addVertex(pose, x1, y1, 0).setColor(color);
    }
}
