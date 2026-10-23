package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.smithing.ForgeShape;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Выколотка на шпераке: лист сверху, светлее — глубже. Точки — где ещё мелко, красные крестики — где выбили глубже
 * разметки ({@link ForgeShape#depth}). ЛКМ (замах) — выколотить, ПКМ — проглаживать: сглаживает бугры вокруг.
 * Готово, когда форма сошлась (или Enter). По холодному листу трещит. Молотобоец бьёт туда, где курсор.
 */
final class RaiseStep extends SmithStep {
    private static final int N = 12;
    private static final float DEEPEN = 0.2F;      // ⚠️ тяжёлый удар по жёлтому в середине
    private static final float TOLERANCE = 0.08F;
    private static final float SIGMA = 1.5F;

    private final ForgeShape shape;
    private final float[] depth = new float[N * N];
    private final float[] target = new float[N * N];
    private final boolean[] plate = new boolean[N * N];
    private final Swing swing = new Swing();
    private int strikes;
    private int overdeep;
    private boolean finished;
    private int gx;
    private int gy;
    private int cell = 8;
    private long struckAt = -1;
    private int struckIndex = -1;

    RaiseStep(SmithWorkScreen screen, Random random, ForgeShape shape) {
        super(screen, random);
        this.shape = shape;
        for (int v = 0; v < N; v++) {
            for (int u = 0; u < N; u++) {
                int i = v * N + u;
                float du = (u + 0.5F) / N - 0.5F;
                float dv = (v + 0.5F) / N - 0.5F;
                plate[i] = du * du + dv * dv <= 0.27F;
                target[i] = plate[i] ? shape.depth((u + 0.5F) / N, (v + 0.5F) / N) : 0;
                depth[i] = plate[i] ? random.nextFloat() * 0.04F : 0;
            }
        }
    }

    @Override
    boolean hot() {
        return true;
    }

    @Override
    float minHeat() {
        return 0.55F;
    }

    @Override
    boolean helped() {
        return true;
    }

    private float error() {
        float total = 0;
        int count = 0;
        for (int i = 0; i < depth.length; i++) {
            if (plate[i]) {
                total += Math.abs(depth[i] - target[i]);
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    @Override
    boolean done() {
        if (finished) {
            return true;
        }
        for (int i = 0; i < depth.length; i++) {
            if (plate[i] && depth[i] < target[i] - TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    @Override
    void finishEarly() {
        finished = true;
    }

    @Override
    int rawScore() {
        return clampScore(100 - error() * 330 - overdeep * 1.5F - Math.max(0, strikes - 80) * 0.3F);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.raise_status", Math.round(error() * 100), overdeep);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.raise_hint");
    }

    private int indexAt(double mouseX, double mouseY) {
        int u = (int) Math.floor((mouseX - gx) / cell);
        int v = (int) Math.floor((mouseY - gy) / cell);
        if (u < 0 || v < 0 || u >= N || v >= N || !plate[v * N + u]) {
            return -1;
        }
        return v * N + u;
    }

    private void blow(int index, float power) {
        if (index < 0 || !screen.strike(this, minHeat())) {
            return;
        }
        strikes++;
        float heat = screen.copper() ? 0.7F : screen.heat();
        float give = Math.max(0.2F, Math.min(1.2F, (heat - 0.35F) / 0.45F));
        int u0 = index % N;
        int v0 = index / N;
        for (int v = 0; v < N; v++) {
            for (int u = 0; u < N; u++) {
                int i = v * N + u;
                if (!plate[i]) {
                    continue;
                }
                float d = (float) Math.hypot(u - u0, v - v0) / SIGMA;
                float k = (float) Math.exp(-d * d / 2);
                if (k < 0.05F) {
                    continue;
                }
                boolean wasOk = depth[i] <= target[i] + TOLERANCE;
                depth[i] = Math.min(1, depth[i] + DEEPEN * (0.2F + power) * give * k);
                if (wasOk && depth[i] > target[i] + TOLERANCE * 1.5F) {
                    overdeep++;
                }
            }
        }
        struckAt = System.currentTimeMillis();
        struckIndex = index;
        screen.sound(SoundEvents.ANVIL_PLACE, 1.5F + power * 0.2F);
    }

    /** Проглаживание: выравнивает соседние клетки к среднему, чуть мелче. */
    private void planish(int index) {
        if (index < 0 || !screen.strike(this, 0.3F)) {
            return;
        }
        int u0 = index % N;
        int v0 = index / N;
        float sum = 0;
        int count = 0;
        for (int v = v0 - 1; v <= v0 + 1; v++) {
            for (int u = u0 - 1; u <= u0 + 1; u++) {
                if (u >= 0 && v >= 0 && u < N && v < N && plate[v * N + u]) {
                    sum += depth[v * N + u];
                    count++;
                }
            }
        }
        float avg = sum / Math.max(1, count);
        for (int v = v0 - 1; v <= v0 + 1; v++) {
            for (int u = u0 - 1; u <= u0 + 1; u++) {
                if (u >= 0 && v >= 0 && u < N && v < N && plate[v * N + u]) {
                    int i = v * N + u;
                    depth[i] += (avg - depth[i]) * 0.5F;
                }
            }
        }
        screen.sound(SoundEvents.ANVIL_PLACE, 2.0F);
    }

    @Override
    void helperBlow(int accuracy, double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            screen.show("striker_aim", TEXT_DIM);
            return;
        }
        blow(index, 0.9F + accuracy / 250F);
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        cell = Math.min((w - 60) / N, (h - 16) / N);
        gx = x + (w - 30 - N * cell) / 2;
        gy = y + (h - N * cell) / 2;
        int color = glow(screen.heat());
        int hover = indexAt(mouseX, mouseY);
        for (int i = 0; i < depth.length; i++) {
            if (!plate[i]) {
                continue;
            }
            int cx = gx + (i % N) * cell;
            int cy = gy + (i / N) * cell;
            int c = mix(shade(color, 0.6F), mix(color, 0xFFFFFFFF, 0.35F), depth[i]);
            g.fill(cx, cy, cx + cell, cy + cell, i == hover ? mix(c, 0xFFFFFFFF, 0.2F) : c);
            if (depth[i] < target[i] - TOLERANCE) {
                g.fill(cx + cell / 2 - 1, cy + cell / 2 - 1, cx + cell / 2 + 1, cy + cell / 2 + 1, MARK);
            } else if (depth[i] > target[i] + TOLERANCE) {
                g.fill(cx + 1, cy + cell / 2, cx + cell - 1, cy + cell / 2 + 1, BAD);
                g.fill(cx + cell / 2, cy + 1, cx + cell / 2 + 1, cy + cell - 1, BAD);
            }
        }
        boolean hit = struckAt >= 0 && System.currentTimeMillis() - struckAt < 120;
        if (hit && struckIndex >= 0) {
            hammer(g, gx + (struckIndex % N) * cell + cell / 2, gy + (struckIndex / N) * cell + cell / 2, 0);
        } else if (hover >= 0) {
            hammer(g, mouseX, mouseY, swing.lift(now));
        }
        swing.render(g, x + w - 16, y + 14, h - 32, now);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            swing.press(System.currentTimeMillis());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            planish(indexAt(mouseX, mouseY));
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && swing.pressed()) {
            blow(indexAt(mouseX, mouseY), swing.release(System.currentTimeMillis()));
            return true;
        }
        return false;
    }
}
