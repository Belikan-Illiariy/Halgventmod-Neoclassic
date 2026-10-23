package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.smithing.ForgeShape;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Вытяжка: заготовка сбоку на наковальне, пунктир — до какой толщины тянуть ({@link ForgeShape#target}). Зажмите ЛКМ
 * над местом — молот набирает замах, отпустите — удар: сминает металл вокруг, тем сильнее, чем крепче замах и
 * горячее заготовка. Протянули тоньше разметки — уже не вернуть. Готово — когда вся заготовка по разметке
 * (или Enter). Молотобоец бьёт туда, где курсор.
 */
final class DrawStep extends SmithStep {
    private static final int COLUMNS = 28;
    private static final float TOLERANCE = 0.06F;    // ⚠️ в пределах разметки
    private static final float HEAVY_DRAW = 0.2F;    // столько снимает тяжёлый удар в середине
    private static final float SIGMA = 1.3F;         // ширина удара, в столбцах
    private static final float MIN = 0.06F;

    private final ForgeShape shape;
    private final float[] thickness = new float[COLUMNS];
    private final float[] target = new float[COLUMNS];
    private final Swing swing = new Swing();
    private int strikes;
    private int overdrawn;
    private boolean finished;
    private int fieldX;
    private int fieldW;
    private long struckAt = -1;
    private int struckColumn = -1;

    DrawStep(SmithWorkScreen screen, Random random, ForgeShape shape) {
        super(screen, random);
        this.shape = shape;
        for (int i = 0; i < COLUMNS; i++) {
            thickness[i] = 0.92F + random.nextFloat() * 0.08F;
            target[i] = shape.target(i / (float) (COLUMNS - 1));
        }
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
    boolean helped() {
        return true;
    }

    @Override
    boolean done() {
        if (finished) {
            return true;
        }
        for (int i = 0; i < COLUMNS; i++) {
            if (thickness[i] > target[i] + TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    @Override
    void finishEarly() {
        finished = true;
    }

    private float error() {
        float total = 0;
        for (int i = 0; i < COLUMNS; i++) {
            total += Math.abs(thickness[i] - target[i]);
        }
        return total / COLUMNS;
    }

    @Override
    int rawScore() {
        return clampScore(100 - error() * 380 - overdrawn * 3 - Math.max(0, strikes - 40) * 0.4);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.draw_status", Math.round(error() * 100), overdrawn,
                strikes);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.draw_hint");
    }

    private int columnAt(double mouseX) {
        if (fieldW <= 0) {
            return -1;
        }
        int c = (int) Math.floor((mouseX - fieldX) / (fieldW / (float) COLUMNS));
        return c >= 0 && c < COLUMNS ? c : -1;
    }

    /** Удар в столбец с силой power (0–1, у молотобойца — больше). */
    private void blow(int column, float power) {
        if (column < 0 || !screen.strike(this, minHeat())) {
            return;
        }
        strikes++;
        float heat = screen.copper() ? 0.7F : screen.heat();
        float give = Math.max(0.15F, Math.min(1.2F, (heat - 0.3F) / 0.5F));
        float depth = HEAVY_DRAW * (0.15F + power) * give;
        for (int i = 0; i < COLUMNS; i++) {
            float d = (i - column) / SIGMA;
            float k = (float) Math.exp(-d * d / 2);
            if (k < 0.05F) {
                continue;
            }
            boolean wasAbove = thickness[i] >= target[i] - TOLERANCE;
            thickness[i] = Math.max(MIN, thickness[i] - depth * k * thickness[i]);
            if (wasAbove && thickness[i] < target[i] - TOLERANCE * 1.4F) {
                overdrawn++;
            }
        }
        struckAt = System.currentTimeMillis();
        struckColumn = column;
        screen.sound(SoundEvents.ANVIL_PLACE, 1.4F + power * 0.3F - give * 0.3F);
        if (power > Swing.HEAVY) {
            screen.sound(SoundEvents.ANVIL_USE, 1.6F);
        }
    }

    @Override
    void helperBlow(int accuracy, double mouseX, double mouseY) {
        int column = columnAt(mouseX);
        if (column < 0) {
            screen.show("striker_aim", TEXT_DIM);
            return;
        }
        // Промахнулся с тактом — удар вбок, на соседний столбец
        if (accuracy < 50) {
            column = Math.max(0, Math.min(COLUMNS - 1, column + (random.nextBoolean() ? 1 : -1)));
        }
        blow(column, 1.0F + accuracy / 200F);
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        fieldX = x + 24;
        fieldW = w - 64;
        int base = y + h - 40;
        int maxH = 46;
        anvil(g, x + 10, base, w - 40);
        float cw = fieldW / (float) COLUMNS;
        int color = glow(screen.heat());
        int hover = columnAt(mouseX);
        for (int i = 0; i < COLUMNS; i++) {
            int cx0 = fieldX + Math.round(i * cw);
            int cx1 = fieldX + Math.round((i + 1) * cw);
            int top = base - Math.round(thickness[i] * maxH);
            g.fill(cx0, top, cx1, base, i == hover ? mix(color, 0xFFFFFFFF, 0.15F) : color);
            g.fill(cx0, top, cx1, top + 1, mix(color, 0xFFFFFFFF, 0.3F));
            int ty = base - Math.round(target[i] * maxH);
            if (i % 2 == 0) {
                g.fill(cx0, ty, cx1, ty + 1, MARK);
            }
            if (thickness[i] < target[i] - TOLERANCE) {
                g.fill(cx0, base + 1, cx1, base + 3, BAD);
            }
        }
        // Молот над курсором; после удара — на месте удара
        boolean hit = struckAt >= 0 && System.currentTimeMillis() - struckAt < 120;
        int hx = hit ? fieldX + Math.round((struckColumn + 0.5F) * cw) : hover >= 0 ? mouseX : -100;
        if (hx > 0) {
            int column = hit ? struckColumn : hover;
            int top = base - Math.round(thickness[Math.max(0, column)] * maxH);
            hammer(g, hx, top, hit ? 0 : swing.lift(now));
        }
        if (hit) {
            g.fill(hx - 6, base - 2, hx + 6, base, SPARK);
        }
        swing.render(g, x + w - 16, y + 14, h - 32, now);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            swing.press(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && swing.pressed()) {
            float power = swing.release(System.currentTimeMillis());
            blow(columnAt(mouseX), power);
            return true;
        }
        return false;
    }
}
