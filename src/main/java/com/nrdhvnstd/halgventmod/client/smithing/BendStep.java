package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.smithing.ForgeShape;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Гибка на роге наковальни: пунктир — дуга загиба ({@link ForgeShape#arc}). Зажмите ЛКМ у начала (или там, где
 * остановились) и ведите конец заготовки по дуге. Сошли с дуги — кривой загиб; рванули быстро по остывающей —
 * надлом. Отпустили — можно продолжить с того же места.
 */
final class BendStep extends SmithStep {
    private static final int SAMPLES = 64;
    private static final float GRAB = 9;         // пикселей: насколько близко к концу загиба надо взяться
    private static final float FAST = 260;       // ⚠️ пикселей в секунду — «рванули»

    private final ForgeShape shape;
    private float progress;          // 0–1 вдоль дуги
    private boolean dragging;
    private double deviation;
    private int samples;
    private int kinks;
    private double lastX;
    private double lastY;
    private long lastAt;
    private int fx;
    private int fy;
    private int fw;
    private int fh;

    BendStep(SmithWorkScreen screen, Random random, ForgeShape shape) {
        super(screen, random);
        this.shape = shape;
    }

    @Override
    boolean hot() {
        return true;
    }

    @Override
    float minHeat() {
        return 0.5F;
    }

    @Override
    boolean done() {
        return progress >= 0.97F;
    }

    @Override
    int rawScore() {
        double avg = samples == 0 ? 0 : deviation / samples;
        return clampScore(100 - avg * 4.5 - kinks * 12);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.bend_status", Math.round(progress * 100), kinks);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.bend_hint");
    }

    private double[] point(float t) {
        float[] p = shape.arc(t);
        return new double[] {fx + p[0] * fw, fy + p[1] * fh};
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        fx = x + 20;
        fy = y + 14;
        fw = w - 40;
        fh = h - 28;
        // Рог наковальни у начала дуги
        double[] start = point(0);
        g.fill((int) start[0] - 40, (int) start[1] + 4, (int) start[0] + 4, (int) start[1] + 12, ANVIL_TOP);
        // Дуга: пунктир впереди, металл — пройденное
        int color = glow(screen.heat());
        for (int i = 0; i <= SAMPLES; i++) {
            float t = i / (float) SAMPLES;
            double[] p = point(t);
            if (t <= progress) {
                g.fill((int) p[0] - 2, (int) p[1] - 2, (int) p[0] + 3, (int) p[1] + 3, color);
            } else if (i % 3 == 0) {
                g.fill((int) p[0], (int) p[1], (int) p[0] + 1, (int) p[1] + 1, MARK);
            }
        }
        // Прямой хвост заготовки до начала дуги
        g.fill((int) start[0] - 70, (int) start[1] - 2, (int) start[0], (int) start[1] + 3, color);
        double[] grip = point(progress);
        g.renderOutline((int) grip[0] - 4, (int) grip[1] - 4, 9, 9, dragging ? GOOD : WARN);
        double[] end = point(1);
        g.fill((int) end[0] - 2, (int) end[1] - 2, (int) end[0] + 3, (int) end[1] + 3, ZONE);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        double[] grip = point(progress);
        if (Math.hypot(mouseX - grip[0], mouseY - grip[1]) <= GRAB) {
            dragging = true;
            lastX = mouseX;
            lastY = mouseY;
            lastAt = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (!dragging) {
            return;
        }
        long now = System.currentTimeMillis();
        double dt = Math.max(1, now - lastAt) / 1000.0;
        double speed = Math.hypot(mouseX - lastX, mouseY - lastY) / dt;
        lastX = mouseX;
        lastY = mouseY;
        lastAt = now;
        // Ближайшая точка дуги впереди (немного вперёд от пройденного)
        float best = progress;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i <= 12; i++) {
            float t = Math.min(1, progress + i * 0.01F);
            double[] p = point(t);
            double d = Math.hypot(mouseX - p[0], mouseY - p[1]);
            if (d < bestDistance) {
                bestDistance = d;
                best = t;
            }
        }
        if (best > progress) {
            if (!screen.strike(this, minHeat())) {
                return;
            }
            deviation += bestDistance;
            samples++;
            float heat = screen.copper() ? 0.7F : screen.heat();
            if (speed > FAST && heat < 0.62F && random.nextFloat() < 0.15F) {
                kinks++;
                screen.show("bend_kink", BAD);
                screen.sound(SoundEvents.ANVIL_LAND, 1.7F);
            }
            if (bestDistance > 14) {
                screen.show("bend_off", WARN);
            }
            progress = best;
            if (samples % 6 == 0) {
                screen.sound(SoundEvents.CHAIN_STEP, 0.8F);
            }
        }
    }
}
