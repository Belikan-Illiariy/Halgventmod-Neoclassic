package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.Random;

import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Один этап работы на верстаке (BenchWorkScreen): своя мини-игра в поле окна. Рисует себя, принимает мышь
 * и клавиши, знает, закончен ли, и свой счёт 0–100 (50 — посредственно).
 */
abstract class WorkStep {
    // Общие цвета этапов
    static final int TEXT = 0xFFE9DCB4;
    static final int TEXT_DIM = 0xFFAA9C80;
    static final int WOOD = 0xFFD2AE78;
    static final int WOOD_SHADE = 0xFFB08A58;
    static final int WOOD_DARK = 0xFF8A6438;
    static final int WOOD_TOP = 0xFFEED2A0;
    static final int KNOT = 0xFF6A4424;
    static final int HOLLOW = 0xFF2A1A0E;
    static final int PENCIL = 0xFF4A4A5E;
    static final int STEEL = 0xFFB4BCC4;
    static final int STEEL_DARK = 0xFF5E666E;
    static final int HANDLE = 0xFF6E4626;
    static final int BENCH = 0xFF4A3220;
    static final int TRACK = 0xFF2A2018;
    static final int ZONE = 0xFF3E7A2E;
    static final int GOOD = 0xFF8FD16A;
    static final int WARN = 0xFFE8B04A;
    static final int BAD = 0xFFE0604A;

    protected final BenchWorkScreen screen;
    protected final BenchProducts.Product product;
    protected final BenchProducts.Step step;
    protected final Random random;

    WorkStep(BenchWorkScreen screen, BenchProducts.Product product, BenchProducts.Step step, Random random) {
        this.screen = screen;
        this.product = product;
        this.step = step;
        this.random = random;
    }

    /** Нарисовать поле этапа: (x, y) — левый верх, w × h — размер. */
    abstract void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now);

    /** Этап закончен — пора к следующему. */
    abstract boolean done();

    /** Счёт этапа 0–100 (пока не закончен — каким выйдет, если закончить сейчас). */
    abstract int score();

    /** Строка «как идёт дело». */
    abstract Component status();

    /** Подсказка управления. */
    abstract Component hint();

    /** Enter: закончить этап раньше (где можно). */
    void finishEarly() {
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    void mouseMoved(double mouseX, double mouseY) {
    }

    boolean mouseScrolled(double scrollY) {
        return false;
    }

    static int clampScore(double score) {
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    /** Полоска со шкалой: доля value, зелёная зона [low, high]. Вертикальная, снизу вверх. */
    static void gauge(GuiGraphics g, int x, int y, int w, int h, float value, float low, float high, int marker) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0E0A06);
        g.fill(x, y, x + w, y + h, TRACK);
        g.fill(x, y + h - Math.round(h * high), x + w, y + h - Math.round(h * low), ZONE);
        int my = y + h - Math.round(h * Math.max(0, Math.min(1, value)));
        g.fill(x - 3, my - 1, x + w + 3, my + 1, marker);
    }
}
