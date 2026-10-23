package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Один этап в окне кузнечества (SmithWorkScreen): своя мини-игра в поле окна. Рисует себя, принимает мышь и
 * клавиши, знает, закончен ли, и свой счёт 0–100 (50 — посредственно). Горячие этапы спрашивают у окна жар
 * заготовки ({@link SmithWorkScreen#heat()}) — пока она в горне, мышь до этапа не доходит.
 */
abstract class SmithStep {
    // Общие цвета
    static final int TEXT = 0xFFE9DCB4;
    static final int TEXT_DIM = 0xFFAA9C80;
    static final int IRON = 0xFF4E5056;
    static final int IRON_LIGHT = 0xFF80848C;
    static final int IRON_DARK = 0xFF2E3034;
    static final int ANVIL = 0xFF3A3C42;
    static final int ANVIL_TOP = 0xFF5C6068;
    static final int WOOD = 0xFF6E4626;
    static final int WOOD_LIGHT = 0xFF8E6238;
    static final int TRACK = 0xFF2A2018;
    static final int ZONE = 0xFF3E7A2E;
    static final int MARK = 0xFFE8D8A0;
    static final int GOOD = 0xFF8FD16A;
    static final int WARN = 0xFFE8B04A;
    static final int BAD = 0xFFE0604A;
    static final int SLAG = 0xFF8A7A5A;
    static final int WATER = 0xFF2E5A7A;
    static final int SPARK = 0xFFFFE6A0;

    protected final SmithWorkScreen screen;
    protected final Random random;
    protected int penalty;
    private Component name = Component.empty();

    SmithStep(SmithWorkScreen screen, Random random) {
        this.screen = screen;
        this.random = random;
    }

    /** Название этапа для строки «Этап 2 из 5: …». */
    Component name() {
        return name;
    }

    SmithStep named(Component name) {
        this.name = name;
        return this;
    }

    /** Нарисовать поле этапа: (x, y) — левый верх, w × h — размер. */
    abstract void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now);

    abstract boolean done();

    /** Счёт без штрафов окна (пережог, трещины): каким выйдет, если закончить сейчас. */
    abstract int rawScore();

    abstract Component status();

    abstract Component hint();

    /** Работают по раскалённому: окно показывает жар, пробел — в горн. */
    boolean hot() {
        return false;
    }

    /** Наименьший жар для этапа (горячим этапам). Ниже — удар может дать трещину. */
    float minHeat() {
        return 0.5F;
    }

    /** Итог этапа для сервера: обычно одно число — счёт. */
    List<Integer> result() {
        return List.of(score());
    }

    final int score() {
        return clampScore(rawScore() - penalty);
    }

    /** Штраф от окна: пережог в горне, удар по холодному. */
    void penalize(int points) {
        penalty += points;
    }

    /** Enter: закончить раньше (где можно). */
    void finishEarly() {
    }

    /** Удар молотобойца: точность 0–100 — тяжёлый удар туда, куда указывает кузнец. */
    void helperBlow(int accuracy, double mouseX, double mouseY) {
    }

    /** Берёт ли этап удары молотобойца. */
    boolean helped() {
        return false;
    }

    /** Окно что-то делает с каждым кадром (жар, ход игры): dt — секунды с прошлого кадра. */
    void tick(float dt, long now) {
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

    boolean keyPressed(int keyCode) {
        return false;
    }

    static int clampScore(double score) {
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    /** Цвет раскалённого металла: тёмный → вишнёвый → оранжевый → жёлтый → белый. */
    static int glow(float heat) {
        float[][] stops = {
                {0.00F, 0x4E, 0x50, 0x56}, {0.35F, 0x52, 0x40, 0x3C}, {0.45F, 0x6A, 0x1E, 0x14}, {0.58F, 0xB0, 0x26, 0x1A},
                {0.72F, 0xE0, 0x66, 0x1C}, {0.85F, 0xF0, 0xC0, 0x40}, {0.95F, 0xFF, 0xF4, 0xD8}, {1.00F, 0xFF, 0xFF, 0xF4}};
        float h = Math.max(0, Math.min(1, heat));
        for (int i = 1; i < stops.length; i++) {
            if (h <= stops[i][0]) {
                float t = (h - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
                int r = Math.round(stops[i - 1][1] + (stops[i][1] - stops[i - 1][1]) * t);
                int gr = Math.round(stops[i - 1][2] + (stops[i][2] - stops[i - 1][2]) * t);
                int b = Math.round(stops[i - 1][3] + (stops[i][3] - stops[i - 1][3]) * t);
                return 0xFF000000 | r << 16 | gr << 8 | b;
            }
        }
        return 0xFFFFFFF4;
    }

    /** Цвет темнее на долю. */
    static int shade(int color, float factor) {
        int r = Math.round(((color >> 16) & 0xFF) * factor);
        int g = Math.round(((color >> 8) & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    /** Смешать два цвета: t = 0 — первый, 1 — второй. */
    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gr = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    /** Полоска со шкалой: доля value, зелёная зона [low, high]. Вертикальная, снизу вверх. */
    static void gauge(GuiGraphics g, int x, int y, int w, int h, float value, float low, float high, int marker) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0E0A06);
        g.fill(x, y, x + w, y + h, TRACK);
        g.fill(x, y + h - Math.round(h * high), x + w, y + h - Math.round(h * low), ZONE);
        int my = y + h - Math.round(h * Math.max(0, Math.min(1, value)));
        g.fill(x - 3, my - 1, x + w + 3, my + 1, marker);
    }

    /** Горизонтальная полоска: доля value слева направо, зона [low, high]. */
    static void bar(GuiGraphics g, int x, int y, int w, int h, float value, float low, float high, int fillColor) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0E0A06);
        g.fill(x, y, x + w, y + h, TRACK);
        if (high > low) {
            g.fill(x + Math.round(w * low), y, x + Math.round(w * high), y + h, ZONE);
        }
        g.fill(x, y + h / 2 - 1, x + Math.round(w * Math.max(0, Math.min(1, value))), y + h / 2 + 1, fillColor);
        int mx = x + Math.round(w * Math.max(0, Math.min(1, value)));
        g.fill(mx - 1, y - 2, mx + 1, y + h + 2, fillColor);
    }

    /** Наковальня внизу поля: рог слева. */
    static void anvil(GuiGraphics g, int x, int y, int w) {
        g.fill(x + 14, y, x + w - 8, y + 6, ANVIL_TOP);
        g.fill(x + 14, y + 6, x + w - 8, y + 10, ANVIL);
        g.fill(x + 30, y + 10, x + w - 24, y + 22, ANVIL);
        g.fill(x + 22, y + 22, x + w - 16, y + 26, IRON_DARK);
        // рог
        for (int i = 0; i < 14; i++) {
            g.fill(x + i, y + 1 + i / 4, x + i + 1, y + 6 - i / 5, ANVIL_TOP);
        }
    }

    /** Молот над точкой (x, y): поднят на lift. */
    static void hammer(GuiGraphics g, int x, int y, int lift) {
        int hy = y - 8 - lift;
        g.fill(x - 6, hy, x + 6, hy + 8, IRON_LIGHT);
        g.fill(x - 6, hy + 6, x + 6, hy + 8, IRON_DARK);
        g.fill(x + 6, hy + 3, x + 30, hy + 5, WOOD);
    }
}
