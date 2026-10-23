package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.Locale;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Сверление коловоротом. На заготовке карандашом помечены дыры; сверлят по одной. Зажмите ЛКМ и водите мышью
 * кругами вокруг метки по часовой стрелке — это рукоять коловорота; против часовой — сверло выкручивается.
 * <ul>
 *   <li>в меру (зелёное на шкале) — сверло идёт ровно;</li>
 *   <li>слишком быстро — сверло уводит, дыра выходит косой;</li>
 *   <li>последняя пятая часть — тише (жёлтая метка): на быстрых оборотах сверло вырывает древесину с изнанки;</li>
 *   <li>слишком малый круг (у самой метки) — рукоять не проворачивается.</li>
 * </ul>
 */
final class DrillStep extends WorkStep {
    // Меняйте здесь
    private static final float TURNS = 5.0F;            // оборотов на дыру
    private static final float FAST = 1.9F;             // оборотов в секунду: быстрее — уводит
    private static final float SLOW_END = 1.0F;         // на выходе — не быстрее
    private static final float END = 0.8F;              // с какой глубины выход
    private static final float WANDER_RATE = 0.9F;      // как быстро уводит за лишний оборот в секунду
    private static final int MIN_RADIUS = 12;           // круг мыши меньше — рукоять не проворачивается
    private static final float MAX_SPEED = 3.5F;        // для шкалы

    private final int holes;
    private final float[] depth;
    private final float[] wander;
    private final boolean[] wandered;
    private final boolean[] blown;
    private int current;
    private boolean pressed;
    private double lastAngle = Double.NaN;
    private long lastAt;
    private float speed;               // оборотов в секунду, сглаженно
    private float crank;               // угол рукояти для отрисовки
    private long lastCreak;
    // Поле, как его нарисовали в последний раз
    private int[] holeX = new int[0];
    private int holeY;

    DrillStep(BenchWorkScreen screen, BenchProducts.Product product, BenchProducts.Step step, Random random) {
        super(screen, product, step, random);
        this.holes = Math.max(1, step.amount());
        this.depth = new float[holes];
        this.wander = new float[holes];
        this.wandered = new boolean[holes];
        this.blown = new boolean[holes];
    }

    private int holeScore(int i) {
        return clampScore(100 - (wandered[i] ? 35 : wander[i] * 20) - (blown[i] ? 30 : 0));
    }

    @Override
    boolean done() {
        return current >= holes;
    }

    @Override
    int score() {
        int total = 0;
        int count = Math.max(1, Math.min(holes, current + 1));
        for (int i = 0; i < count; i++) {
            total += holeScore(i);
        }
        return Math.round(total / (float) count);
    }

    @Override
    Component status() {
        int shown = Math.min(current, holes - 1);
        return Component.translatable("gui.halgventmod.bench_work.drill_status", Math.min(current + 1, holes), holes,
                Math.round(depth[shown] * 100), String.format(Locale.ROOT, "%.1f", speed));
    }

    @Override
    Component hint() {
        return Component.translatable(current < holes && depth[current] >= END ? "gui.halgventmod.bench_work.drill_hint_end"
                : "gui.halgventmod.bench_work.drill_hint");
    }

    private void crank(double mouseX, double mouseY) {
        if (current >= holes || holeX.length <= current) {
            return;
        }
        double dx = mouseX - holeX[current];
        double dy = mouseY - holeY;
        long now = System.currentTimeMillis();
        if (!pressed || dx * dx + dy * dy < MIN_RADIUS * MIN_RADIUS) {
            lastAngle = Double.NaN;
            lastAt = now;
            return;
        }
        double angle = Math.atan2(dy, dx);
        if (Double.isNaN(lastAngle)) {
            lastAngle = angle;
            lastAt = now;
            return;
        }
        double delta = angle - lastAngle;
        if (delta > Math.PI) {
            delta -= Math.PI * 2;
        } else if (delta < -Math.PI) {
            delta += Math.PI * 2;
        }
        lastAngle = angle;
        crank = (float) angle;
        float dt = Math.max(1, now - lastAt) / 1000.0F;
        lastAt = now;
        // На экране Y вниз: по часовой стрелке угол растёт
        float turns = (float) (delta / (Math.PI * 2));
        float instant = Math.abs(turns) / dt;
        speed += (Math.min(instant, MAX_SPEED * 2) - speed) * Math.min(1.0F, dt * 6);
        int i = current;
        if (turns < 0) {
            depth[i] = Math.max(0, depth[i] + turns / TURNS * 0.5F);
            return;
        }
        depth[i] = Math.min(1.0F, depth[i] + turns / TURNS);
        if (speed > FAST) {
            wander[i] = Math.min(1.0F, wander[i] + (speed - FAST) * dt * WANDER_RATE);
            if (wander[i] >= 1.0F && !wandered[i]) {
                wandered[i] = true;
                screen.show("drill_wander", BAD);
                screen.sound(SoundEvents.WOOD_BREAK, 1.2F);
            }
        }
        if (depth[i] >= END && speed > SLOW_END && !blown[i] && random.nextFloat() < dt * 3) {
            blown[i] = true;
            screen.show("drill_blowout", BAD);
            screen.sound(SoundEvents.WOOD_BREAK, 0.8F);
        }
        if (now - lastCreak > 160) {
            lastCreak = now;
            screen.sound(SoundEvents.WOOD_HIT, 1.7F + (speed > FAST ? 0.3F : 0) - depth[i] * 0.4F);
        }
        if (depth[i] >= 1.0F) {
            int score = holeScore(i);
            screen.show(score >= 80 ? "drill_clean" : "drill_done", score >= 80 ? GOOD : TEXT);
            screen.sound(SoundEvents.WOOD_PLACE, 1.4F);
            current++;
            lastAngle = Double.NaN;
            speed = 0;
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        if (now - lastAt > 120) {
            speed *= 0.9F; // рукоять встала — обороты падают
        }
        // Заготовка сверху
        int bx0 = x + 16;
        int bx1 = x + w - 40;
        int by0 = y + 22;
        int by1 = y + h - 14;
        g.fill(bx0 - 2, by0 - 2, bx1 + 2, by1 + 2, WOOD_DARK);
        for (int ry = by0; ry < by1; ry++) {
            g.fill(bx0, ry, bx1, ry + 1, (ry / 3) % 4 == 0 ? WOOD_SHADE : WOOD);
        }
        holeY = (by0 + by1) / 2;
        if (holeX.length != holes) {
            holeX = new int[holes];
        }
        for (int i = 0; i < holes; i++) {
            holeX[i] = bx0 + (bx1 - bx0) * (i + 1) / (holes + 1);
        }
        for (int i = 0; i < holes; i++) {
            int hx = holeX[i];
            if (i < current) {
                int stretch = wandered[i] ? 3 : 0;
                g.fill(hx - 4 - stretch, holeY - 4, hx + 4 + stretch, holeY + 4, HOLLOW);
                if (blown[i]) {
                    g.fill(hx - 6, holeY + 4, hx + 6, holeY + 6, 0xFFE8C894);
                }
            } else {
                g.fill(hx - 4, holeY, hx + 5, holeY + 1, PENCIL);
                g.fill(hx, holeY - 4, hx + 1, holeY + 5, PENCIL);
            }
        }
        if (current < holes) {
            int hx = holeX[current];
            // Начатая дыра темнеет с глубиной, вокруг — кольцо: сколько просверлено
            int r = Math.round(depth[current] * 4);
            if (r > 0) {
                g.fill(hx - r, holeY - r, hx + r, holeY + r, HOLLOW);
            }
            int ring = 18;
            int steps = 48;
            for (int s = 0; s < steps; s++) {
                double a = -Math.PI / 2 + s * Math.PI * 2 / steps;
                boolean filled = s < Math.round(depth[current] * steps);
                boolean exit = s >= Math.round(END * steps);
                int px = hx + (int) Math.round(Math.cos(a) * ring);
                int py = holeY + (int) Math.round(Math.sin(a) * ring);
                g.fill(px, py, px + 2, py + 2, filled ? (exit ? WARN : GOOD) : exit ? 0xFF6A5A30 : 0xFF3A3024);
            }
            // Коловорот: ось в дыре, колено к мыши
            double a = Double.isNaN(lastAngle) ? crank : lastAngle;
            int kx = hx + (int) Math.round(Math.cos(a) * 26);
            int ky = holeY + (int) Math.round(Math.sin(a) * 26);
            line(g, hx, holeY, kx, ky, pressed ? STEEL : STEEL_DARK);
            g.fill(kx - 3, ky - 3, kx + 3, ky + 3, HANDLE);
            g.fill(hx - 3, holeY - 3, hx + 3, holeY + 3, HANDLE);
        }
        // Обороты: шкала справа, зелёное — в меру; у выхода — ниже
        float high = (current < holes && depth[current] >= END ? SLOW_END : FAST) / MAX_SPEED;
        gauge(g, x + w - 26, y + 22, 10, h - 36, speed / MAX_SPEED, 0.6F / MAX_SPEED, high,
                speed > high * MAX_SPEED ? BAD : TEXT);
    }

    /** Отрезок точками. */
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int px = x0 + (x1 - x0) * i / Math.max(1, n);
            int py = y0 + (y1 - y0) * i / Math.max(1, n);
            g.fill(px, py, px + 2, py + 2, color);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = true;
            lastAngle = Double.NaN;
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = false;
            speed = 0;
            return true;
        }
        return false;
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        crank(mouseX, mouseY);
    }
}
