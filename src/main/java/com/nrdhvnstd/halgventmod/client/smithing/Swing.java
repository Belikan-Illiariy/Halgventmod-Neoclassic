package com.nrdhvnstd.halgventmod.client.smithing;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Замах молота: зажали ЛКМ — сила ходит от нуля до полной и обратно ({@link #PERIOD_MS}), отпустили — удар с той
 * силой, что была. Лёгкий удар — ниже {@link #LIGHT}, тяжёлый — выше {@link #HEAVY}.
 */
final class Swing {
    static final int PERIOD_MS = 1000;
    static final float LIGHT = 0.35F;
    static final float HEAVY = 0.75F;

    private boolean pressed;
    private long pressedAt;
    private float last;

    void press(long now) {
        pressed = true;
        pressedAt = now;
    }

    boolean pressed() {
        return pressed;
    }

    /** Отпустить: сила удара 0–1. */
    float release(long now) {
        last = power(now);
        pressed = false;
        return last;
    }

    float power(long now) {
        if (!pressed) {
            return 0;
        }
        float t = ((now - pressedAt) % PERIOD_MS) / (float) PERIOD_MS;
        return t < 0.5F ? t * 2 : 2 - t * 2;
    }

    /** Подъём молота для отрисовки, в пикселях. */
    int lift(long now) {
        return pressed ? Math.round(power(now) * 22) + 3 : 3;
    }

    /** Шкала замаха: вертикальная, с отметками лёгкого и тяжёлого. */
    void render(GuiGraphics g, int x, int y, int h, long now) {
        float shown = pressed ? power(now) : last;
        SmithStep.gauge(g, x, y, 8, h, shown, LIGHT, HEAVY, shown > HEAVY ? SmithStep.WARN : SmithStep.TEXT);
    }
}
