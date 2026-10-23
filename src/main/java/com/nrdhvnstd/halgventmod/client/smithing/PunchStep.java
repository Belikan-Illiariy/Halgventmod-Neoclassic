package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Пробивка бородком: на заготовке метки отверстий. Бородок в руке ходит туда-сюда над меткой; ЛКМ — удар, когда он
 * точно над ней. На дыру — два удара: наметить и пробить насквозь (второй должен лечь туда же). Пробивают по
 * жёлтому; по холодному бородок вязнет.
 */
final class PunchStep extends SmithStep {
    private static final float AMPLITUDE = 16;   // пикселей
    private final int holes;
    private final float[] offsets;
    private int current;
    private int stroke;            // 0 — наметить, 1 — пробить
    private float firstOffset;
    private float period;
    private float phase;
    private float totalOffset;
    private long struckAt = -1;
    private int markX;
    private int markY;

    PunchStep(SmithWorkScreen screen, Random random, int holes) {
        super(screen, random);
        this.holes = Math.max(1, holes);
        this.offsets = new float[this.holes];
        next();
    }

    private void next() {
        period = 900 + random.nextInt(500);
        phase = random.nextFloat() * 6.28F;
    }

    @Override
    boolean hot() {
        return true;
    }

    @Override
    float minHeat() {
        return 0.75F;
    }

    @Override
    boolean done() {
        return current >= holes;
    }

    @Override
    int rawScore() {
        int made = Math.max(1, current);
        return clampScore(100 - totalOffset / made * 6);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.punch_status", Math.min(current + 1, holes), holes,
                stroke == 0 ? 1 : 2);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.punch_hint");
    }

    private float sway(long now) {
        return AMPLITUDE * (float) Math.sin(now * Math.PI * 2 / period + phase);
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int base = y + h - 40;
        anvil(g, x + 10, base, w - 40);
        int left = x + 30;
        int right = x + w - 50;
        int color = glow(screen.heat());
        g.fill(left, base - 14, right, base, color);
        for (int i = 0; i < holes; i++) {
            int hx = left + (right - left) * (i + 1) / (holes + 1);
            if (i < current) {
                int off = Math.round(offsets[i]);
                g.fill(hx + off - 2, base - 14, hx + off + 2, base, 0xFF0E0A06);
            } else {
                g.fill(hx - 1, base - 16, hx + 1, base - 14, MARK);
                if (i == current && stroke == 1) {
                    int off = Math.round(firstOffset);
                    g.fill(hx + off - 2, base - 15, hx + off + 2, base - 13, shade(color, 0.5F));
                }
            }
            if (i == current) {
                markX = hx;
                markY = base - 14;
            }
        }
        if (current < holes) {
            boolean hit = struckAt >= 0 && System.currentTimeMillis() - struckAt < 120;
            int px = markX + Math.round(sway(now));
            int tip = markY - (hit ? 0 : 4);
            g.fill(px - 2, tip - 30, px + 2, tip, IRON_LIGHT);
            g.fill(px - 3, tip - 36, px + 3, tip - 30, IRON_DARK);
            hammer(g, px, tip - 36, hit ? 0 : 10);
            // Точность: зелёное — над меткой
            g.fill(markX - 3, y + 10, markX + 3, y + 13, ZONE);
            g.fill(markX - Math.round(AMPLITUDE), y + 11, markX + Math.round(AMPLITUDE), y + 12, TRACK);
            g.fill(px, y + 8, px + 1, y + 15, Math.abs(px - markX) <= 3 ? GOOD : BAD);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || current >= holes) {
            return false;
        }
        if (!screen.strike(this, minHeat())) {
            return true;
        }
        long now = System.currentTimeMillis();
        float off = sway(now);
        struckAt = now;
        screen.sound(SoundEvents.ANVIL_PLACE, 1.7F);
        if (stroke == 0) {
            firstOffset = off;
            stroke = 1;
            next();
        } else {
            float total = Math.abs(firstOffset) + Math.abs(off - firstOffset) * 1.5F;
            offsets[current] = (firstOffset + off) / 2;
            totalOffset += total / 2;
            screen.show(total / 2 <= 4 ? "punch_clean" : "punch_off", total / 2 <= 4 ? GOOD : WARN);
            current++;
            stroke = 0;
            next();
        }
        return true;
    }
}
