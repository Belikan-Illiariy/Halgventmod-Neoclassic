package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Отпуск: закалённая вещь лежит над краем горна, по ней ползут цвета побежалости — соломенный, бронзовый, пурпурный,
 * синий, серый. Мышью двигайте вещь над жаром: где ближе к огню, там цвет бежит быстрее. Цель — ровный цвет по всей
 * длине: инструмент — соломенный, клинок — пурпурный, пружина — синий. ЛКМ — снять, когда пора.
 */
final class TemperStep extends SmithStep {
    private static final int SEGMENTS = 12;
    private static final float RATE = 0.16F;      // ⚠️ отпуска в секунду прямо над жаром
    private static final float[] TARGETS = {0.3F, 0.3F, 0.55F, 0.72F};
    private static final String[] COLORS = {"straw", "straw", "purple", "blue"};

    private final float target;
    private final String color;
    private final float[] temper = new float[SEGMENTS];
    private float position = 0.5F;   // где жар под вещью, 0–1 по длине
    private boolean taken;
    private int fieldX;
    private int fieldW;

    TemperStep(SmithWorkScreen screen, Random random, int kind) {
        super(screen, random);
        int k = Math.max(0, Math.min(TARGETS.length - 1, kind));
        this.target = TARGETS[k];
        this.color = COLORS[k];
    }

    @Override
    boolean done() {
        return taken;
    }

    private float error() {
        float total = 0;
        for (float t : temper) {
            total += Math.abs(t - target);
        }
        return total / SEGMENTS;
    }

    @Override
    int rawScore() {
        return clampScore(100 - error() * 320);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.temper_status",
                Component.translatable("gui.halgventmod.smith_work.temper_" + color), Math.round(error() * 100));
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.temper_hint");
    }

    /** Цвет побежалости по степени отпуска 0–1. */
    static int temperColor(float t) {
        int[] stops = {0xFFC8CCD2, 0xFFE8D890, 0xFFC89040, 0xFF8A3A7A, 0xFF3A4AB0, 0xFF6E7C8A, 0xFF8A8A8A};
        float s = Math.max(0, Math.min(0.999F, t)) * (stops.length - 1);
        int i = (int) s;
        return mix(stops[i], stops[i + 1], s - i);
    }

    @Override
    void tick(float dt, long now) {
        if (taken) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float at = (i + 0.5F) / SEGMENTS;
            float d = Math.abs(at - position);
            float k = Math.max(0, 1 - d / 0.22F);
            temper[i] = Math.min(1, temper[i] + RATE * k * k * dt * (0.7F + screen.forgeHeat() * 0.6F));
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        fieldX = x + 20;
        fieldW = w - 40;
        int bladeY = y + h / 2 - 6;
        // Угли под вещью
        int fireX = fieldX + Math.round(position * fieldW);
        for (int i = 0; i < 18; i++) {
            int cx = fireX + random.nextInt(50) - 25;
            int cy = bladeY + 22 + random.nextInt(12);
            g.fill(cx, cy, cx + 3, cy + 2, i % 3 == 0 ? 0xFFFFA040 : 0xFFB03010);
        }
        float sw = fieldW / (float) SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            int sx0 = fieldX + Math.round(i * sw);
            int sx1 = fieldX + Math.round((i + 1) * sw);
            g.fill(sx0, bladeY, sx1, bladeY + 12, temperColor(temper[i]));
            g.fill(sx0, bladeY + 12, sx1, bladeY + 14, shade(temperColor(temper[i]), 0.6F));
        }
        // Шкала цветов с целью
        int sy = y + 14;
        for (int i = 0; i < fieldW; i++) {
            g.fill(fieldX + i, sy, fieldX + i + 1, sy + 5, temperColor(i / (float) fieldW));
        }
        int tx = fieldX + Math.round(target * fieldW);
        g.fill(tx - 1, sy - 3, tx + 2, sy + 8, MARK);
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (fieldW > 0) {
            position = (float) Math.max(0, Math.min(1, (mouseX - fieldX) / fieldW));
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !taken) {
            taken = true;
            screen.sound(SoundEvents.CHAIN_PLACE, 1.2F);
            return true;
        }
        return false;
    }

    @Override
    void finishEarly() {
        taken = true;
    }
}
