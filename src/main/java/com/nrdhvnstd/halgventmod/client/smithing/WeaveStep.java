package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Плетение кольчуги «четыре в одно»: каждое новое кольцо продевают в два кольца ряда выше и в соседнее слева —
 * щёлкайте их (ЛКМ), потом заклепайте кольцо: ЛКМ, когда бегунок в зелёном. На кольцо даётся немного времени;
 * продели не туда — разрыв в полотне, не успели — кольцо сплетено криво.
 */
final class WeaveStep extends SmithStep {
    private static final int COLS = 8;
    private static final float RING_SECONDS = 3.0F;   // ⚠️ на кольцо
    private static final float RIVET_SPEED = 1.6F;

    private final int rings;
    private int current;
    private final List<Integer> needed = new ArrayList<>();
    private final List<Integer> linked = new ArrayList<>();
    private float timer;
    private boolean riveting;
    private float rivetPos;
    private float rivetDir = 1;
    private float zone;
    private int wrong;
    private int late;
    private int badRivets;
    private int gx;
    private int gy;
    private int cell = 14;
    private final int rows;

    WeaveStep(SmithWorkScreen screen, Random random, int rings) {
        super(screen, random);
        this.rings = Math.max(4, rings);
        this.rows = (this.rings + COLS - 1) / COLS;
        prepare();
    }

    private int row(int ring) {
        return ring / COLS;
    }

    private int col(int ring) {
        return ring % COLS;
    }

    private void prepare() {
        needed.clear();
        linked.clear();
        timer = 0;
        riveting = false;
        zone = 0.3F + random.nextFloat() * 0.4F;
        if (current >= rings) {
            return;
        }
        int r = row(current);
        int c = col(current);
        if (r > 0) {
            int left = r % 2 == 1 ? c : c - 1;
            for (int cc = left; cc <= left + 1; cc++) {
                if (cc >= 0 && cc < COLS) {
                    needed.add((r - 1) * COLS + cc);
                }
            }
        }
        if (c > 0) {
            needed.add(current - 1);
        }
        if (needed.isEmpty()) {
            riveting = true; // первое кольцо — только заклепать
        }
    }

    @Override
    boolean done() {
        return current >= rings;
    }

    @Override
    int rawScore() {
        return clampScore(100 - wrong * 4 - late * 5 - badRivets * 3);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.weave_status", Math.min(current + 1, rings), rings,
                wrong, badRivets);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.weave_hint");
    }

    @Override
    void tick(float dt, long now) {
        if (current >= rings) {
            return;
        }
        if (riveting) {
            rivetPos += rivetDir * RIVET_SPEED * dt;
            if (rivetPos > 1 || rivetPos < 0) {
                rivetDir = -rivetDir;
                rivetPos = Math.max(0, Math.min(1, rivetPos));
            }
            return;
        }
        timer += dt;
        if (timer > RING_SECONDS) {
            late++;
            screen.show("weave_late", WARN);
            riveting = true;
        }
    }

    private int[] center(int ring) {
        int r = row(ring);
        int c = col(ring);
        int cx = gx + c * cell + (r % 2 == 1 ? cell / 2 : 0) + cell / 2;
        int cy = gy + r * (cell * 3 / 4) + cell / 2;
        return new int[] {cx, cy};
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        cell = Math.max(10, Math.min((w - 40) / (COLS + 1), (h - 40) * 4 / 3 / Math.max(1, Math.min(rows, 8))));
        int shownRows = Math.min(rows, Math.max(1, (h - 40) * 4 / 3 / cell));
        int firstRow = Math.max(0, row(Math.min(current, rings - 1)) - shownRows + 2);
        gx = x + 20;
        gy = y + 12 - firstRow * (cell * 3 / 4);
        for (int ring = Math.max(0, firstRow * COLS); ring < rings; ring++) {
            int[] c = center(ring);
            if (c[1] > y + h - 30) {
                break;
            }
            int radius = cell / 2 - 1;
            int color = ring < current ? IRON_LIGHT : ring == current ? WARN : 0xFF2E2A26;
            if (needed.contains(ring)) {
                color = linked.contains(ring) ? GOOD : mix(IRON_LIGHT, MARK, 0.5F);
            }
            ringShape(g, c[0], c[1], radius, color);
        }
        if (riveting && current < rings) {
            int bx = x + 20;
            int by = y + h - 18;
            int bw = w - 60;
            g.fill(bx, by, bx + bw, by + 6, TRACK);
            g.fill(bx + Math.round(bw * (zone - 0.08F)), by, bx + Math.round(bw * (zone + 0.08F)), by + 6, ZONE);
            int mx = bx + Math.round(bw * rivetPos);
            g.fill(mx - 1, by - 2, mx + 1, by + 8, TEXT);
        } else if (current < rings) {
            bar(g, x + 20, y + h - 18, w - 60, 4, 1 - timer / RING_SECONDS, 0, 0, timer > RING_SECONDS * 0.7F ? BAD : TEXT);
        }
    }

    private static void ringShape(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int a = 0; a < 24; a++) {
            double t = a * Math.PI * 2 / 24;
            int px = cx + (int) Math.round(Math.cos(t) * r);
            int py = cy + (int) Math.round(Math.sin(t) * r * 0.7);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || current >= rings) {
            return false;
        }
        if (riveting) {
            boolean ok = Math.abs(rivetPos - zone) <= 0.08F;
            if (!ok) {
                badRivets++;
                screen.show("weave_rivet_bad", WARN);
            }
            screen.sound(SoundEvents.CHAIN_PLACE, ok ? 1.6F : 1.0F);
            current++;
            prepare();
            return true;
        }
        int best = -1;
        double bestDistance = cell * 0.6;
        for (int ring = 0; ring < current; ring++) {
            int[] c = center(ring);
            double d = Math.hypot(mouseX - c[0], mouseY - c[1]);
            if (d < bestDistance) {
                bestDistance = d;
                best = ring;
            }
        }
        if (best < 0) {
            return true;
        }
        if (needed.contains(best)) {
            if (!linked.contains(best)) {
                linked.add(best);
                screen.sound(SoundEvents.CHAIN_STEP, 1.3F);
            }
            if (linked.size() >= needed.size()) {
                riveting = true;
            }
        } else {
            wrong++;
            screen.show("weave_wrong", BAD);
            screen.sound(SoundEvents.CHAIN_BREAK, 1.6F);
        }
        return true;
    }
}
