package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.Locale;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;
import com.nrdhvnstd.halgventmod.carpentry.WorkShape;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Обстругивание ножом. Брусок виден сбоку — он круглый, поэтому верх и низ одинаковы; карандашом проведён профиль
 * будущей вещи (рукоять с пояском, веретено, лодочка челнока…). Зажмите ЛКМ и ведите нож вдоль бруска: лезвие
 * снимает древесину до той высоты, где идёт мышь, но не толще стружки за проход — так профиль выводят по линии.
 * <ul>
 *   <li>от себя, по волокну (стрелка) — чисто; против волокна нож задирает;</li>
 *   <li>быстро по сучку — нож срывается и режет глубже;</li>
 *   <li>глубже разметки — перерезано.</li>
 * </ul>
 * Весь брусок до разметки — этап закончен; Enter — раньше.
 */
final class WhittleStep extends WorkStep {
    // Меняйте здесь
    private static final int COLUMNS = 48;
    private static final float BITE = 1.3F;             // толщина стружки за проход, единиц
    private static final float FAST = 0.7F;             // пикселей в мс: быстрее по сучку — срыв
    private static final float TEAR_AGAINST = 0.35F;
    private static final float TEAR_DEPTH = 0.7F;
    private static final float SLIP_DEPTH = 1.0F;
    private static final float OVER = 0.4F;             // глубже разметки — перерезано
    private static final float CLOSE = 0.45F;           // всё не дальше этого от разметки — готово

    private final WorkShape shape;
    private final float[] radius = new float[COLUMNS];
    private final float[] target = new float[COLUMNS];
    private final boolean[] knot = new boolean[COLUMNS];
    private final boolean[] over = new boolean[COLUMNS];
    private final int grain;
    private int tears;
    private int slips;
    private boolean pressed;
    private boolean finished;
    private double lastX = Double.NaN;
    private int lastColumn = Integer.MIN_VALUE; // последний срезанный столбец — не резать его дважды за проход
    private double lastY;
    private long lastAt;
    private long lastShaving;
    // Поле, как его нарисовали в последний раз
    private int stickLeft;
    private int centerY;
    private int columnWidth = 5;
    private float scale = 7;

    WhittleStep(BenchWorkScreen screen, BenchProducts.Product product, BenchProducts.Step step, Random random) {
        super(screen, product, step, random);
        this.shape = step.shape();
        this.grain = random.nextBoolean() ? 1 : -1;
        for (int i = 0; i < COLUMNS; i++) {
            target[i] = shape.radius(i / (float) (COLUMNS - 1));
            radius[i] = WorkShape.MAX_RADIUS - 0.3F + random.nextFloat() * 0.5F;
        }
        for (int k = 0; k < product.knots(); k++) {
            int at = 4 + random.nextInt(COLUMNS - 10);
            knot[at] = knot[at + 1] = true;
        }
    }

    private float deviation() {
        float sum = 0;
        for (int i = 0; i < COLUMNS; i++) {
            sum += Math.abs(radius[i] - target[i]);
        }
        return sum / COLUMNS;
    }

    private int overcut() {
        int n = 0;
        for (boolean o : over) {
            if (o) {
                n++;
            }
        }
        return n;
    }

    private boolean shaped() {
        for (int i = 0; i < COLUMNS; i++) {
            if (radius[i] > target[i] + CLOSE) {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean done() {
        return finished || shaped() && !pressed;
    }

    @Override
    void finishEarly() {
        finished = true;
    }

    @Override
    int score() {
        return clampScore(100 - deviation() * 28 - overcut() * 5 - tears * 8 - slips * 10);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.bench_work.whittle_status",
                String.format(Locale.ROOT, "%.1f", deviation()), tears + slips, overcut());
    }

    @Override
    Component hint() {
        return Component.translatable(grain > 0 ? "gui.halgventmod.bench_work.whittle_hint_right"
                : "gui.halgventmod.bench_work.whittle_hint_left");
    }

    /** Провести нож от (x0, y0) до (x1, y1) — в пикселях окна. */
    private void cut(double x0, double y0, double x1, double y1, float speed) {
        int dir = x1 > x0 ? 1 : -1;
        int c0 = (int) Math.floor((x0 - stickLeft) / columnWidth);
        int c1 = (int) Math.floor((x1 - stickLeft) / columnWidth);
        for (int c = c0; dir > 0 ? c <= c1 : c >= c1; c += dir) {
            if (c < 0 || c >= COLUMNS || c == lastColumn) {
                continue;
            }
            double t = c1 == c0 ? 1 : (c - c0) / (double) (c1 - c0);
            double my = y0 + (y1 - y0) * t;
            float level = (float) (Math.abs(my - centerY) / scale);
            if (level >= radius[c]) {
                continue;
            }
            radius[c] = Math.max(level, radius[c] - BITE);
            if (dir != grain && random.nextFloat() < TEAR_AGAINST) {
                radius[c] -= TEAR_DEPTH;
                tears++;
                screen.show("whittle_against", BAD);
                screen.sound(SoundEvents.WOOD_BREAK, 1.6F);
            } else if (knot[c] && speed > FAST) {
                radius[c] -= SLIP_DEPTH;
                slips++;
                screen.show("whittle_slip", BAD);
                screen.sound(SoundEvents.WOOD_BREAK, 1.2F);
            }
            radius[c] = Math.max(0.3F, radius[c]);
            if (radius[c] < target[c] - OVER) {
                over[c] = true;
            }
            long now = System.currentTimeMillis();
            if (now - lastShaving > 80) {
                lastShaving = now;
                screen.sound(SoundEvents.BRUSH_GENERIC, 1.7F + random.nextFloat() * 0.2F);
            }
        }
        lastColumn = c1;
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        columnWidth = Math.max(3, (w - 24) / COLUMNS);
        stickLeft = x + (w - columnWidth * COLUMNS) / 2;
        centerY = y + h / 2 + 4;
        scale = Math.min(7, (h - 24) / 2.0F / WorkShape.MAX_RADIUS);
        for (int i = 0; i < COLUMNS; i++) {
            int cx = stickLeft + i * columnWidth;
            int r = Math.round(radius[i] * scale);
            int core = Math.max(1, r / 3);
            g.fill(cx, centerY - r, cx + columnWidth, centerY + r, WOOD_SHADE);
            g.fill(cx, centerY - r + Math.max(1, r / 4), cx + columnWidth, centerY + core, WOOD); // блик — круглое
            g.fill(cx, centerY - r, cx + columnWidth, centerY - r + 1, WOOD_TOP);
            g.fill(cx, centerY + r - 1, cx + columnWidth, centerY + r, WOOD_DARK);
            if (knot[i]) {
                g.fill(cx, centerY - 2, cx + columnWidth, centerY + 3, KNOT);
            }
            if (over[i]) {
                g.fill(cx, centerY - r, cx + columnWidth, centerY - r + 2, BAD);
                g.fill(cx, centerY + r - 2, cx + columnWidth, centerY + r, BAD);
            }
            // Профиль карандашом, сверху и снизу
            if (i % 2 == 0) {
                int t = Math.round(target[i] * scale);
                g.fill(cx, centerY - t, cx + columnWidth, centerY - t + 1, PENCIL);
                g.fill(cx, centerY + t, cx + columnWidth, centerY + t + 1, PENCIL);
            }
        }
        // Волокно — стрелка под бруском
        int ay = centerY + Math.round(WorkShape.MAX_RADIUS * scale) + 6;
        int ax = stickLeft + columnWidth * COLUMNS / 2;
        g.fill(ax - 14, ay, ax + 14, ay + 1, TEXT_DIM);
        int tip = grain > 0 ? ax + 14 : ax - 14;
        g.fill(tip - grain * 3, ay - 2, tip - grain * 3 + 1, ay + 3, TEXT_DIM);
        // Нож у мыши: лезвие поперёк, рукоять сзади по ходу
        if (mouseX >= 0) {
            int bladeColor = pressed ? STEEL : STEEL_DARK;
            g.fill(mouseX - grain * 9, mouseY - 1, mouseX + 1, mouseY + 1, bladeColor);
            g.fill(mouseX - grain * 17 - 2, mouseY - 2, mouseX - grain * 9 + 1, mouseY + 2, HANDLE);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            lastColumn = Integer.MIN_VALUE;
            pressed = true;
            lastX = mouseX;
            lastY = mouseY;
            lastAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = false;
            lastX = Double.NaN;
            return true;
        }
        return false;
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (!pressed || Double.isNaN(lastX)) {
            return;
        }
        long now = System.currentTimeMillis();
        float speed = (float) (Math.abs(mouseX - lastX) / Math.max(1, now - lastAt));
        if (mouseX != lastX) {
            cut(lastX, lastY, mouseX, mouseY, speed);
        }
        lastX = mouseX;
        lastY = mouseY;
        lastAt = now;
    }
}
