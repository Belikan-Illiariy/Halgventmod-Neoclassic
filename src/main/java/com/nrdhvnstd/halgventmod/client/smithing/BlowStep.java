package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Стеклодув — выдувание. Держите ЛКМ — дуть: растёт давление, а от него пузырь, но только пока стекло горячее
 * (остыло — пробел, разогреть в окне печи). Давление выше черты — пузырь лопается (штраф, пузырь опадает). Пузырь в
 * зелёной зоне — ПКМ: готово. Перераздули — стенки тонкие.
 */
final class BlowStep extends SmithStep {
    // ⚠️
    private static final float PRESSURE_UP = 0.9F;    // в секунду, пока дуют
    private static final float PRESSURE_DOWN = 1.4F;  // в секунду, когда отпустили
    private static final float GROW = 0.35F;          // рост пузыря при полном давлении, в секунду
    private static final float BURST = 0.92F;
    private static final float STIFF = 0.45F;         // холоднее — не раздувается

    private final float low;
    private final float high;
    private float bubble = 0.08F;
    private float pressure;
    private boolean blowing;
    private boolean finished;
    private int bursts;
    private float coldBlow;

    /** @param size 0 — мелкое, 1 — среднее, 2 — крупное */
    BlowStep(SmithWorkScreen screen, Random random, int size) {
        super(screen, random);
        this.low = 0.35F + 0.17F * size;
        this.high = low + 0.15F;
    }

    @Override
    boolean hot() {
        return true;
    }

    @Override
    float minHeat() {
        return STIFF;
    }

    @Override
    boolean done() {
        return finished;
    }

    @Override
    int rawScore() {
        float center = (low + high) / 2;
        float miss = bubble >= low && bubble <= high ? Math.abs(bubble - center) * 60 : Math.abs(bubble - center) * 200;
        return clampScore(100 - miss - bursts * 18 - coldBlow * 4);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.blow_status", Math.round(bubble * 100),
                Math.round(pressure * 100), bursts);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.blow_hint");
    }

    @Override
    void finishEarly() {
        if (bubble >= low) {
            finished = true;
        }
    }

    @Override
    void tick(float dt, long now) {
        if (finished) {
            return;
        }
        if (blowing && !screen.inForge()) {
            pressure = Math.min(1.05F, pressure + PRESSURE_UP * dt);
        } else {
            pressure = Math.max(0, pressure - PRESSURE_DOWN * dt);
        }
        if (screen.heat() >= STIFF) {
            bubble = Math.min(1, bubble + GROW * pressure * pressure * dt * (0.5F + screen.heat()));
        } else if (blowing) {
            coldBlow += dt; // дуют в застывшее — без толку
        }
        if (pressure >= BURST) {
            bursts++;
            pressure = 0;
            blowing = false;
            bubble = Math.max(0.08F, bubble * 0.6F);
            screen.show("blow_burst", BAD);
            screen.sound(SoundEvents.GLASS_BREAK, 1.6F);
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int cy = y + h / 2 + 10;
        int tipX = x + w / 2 - 10;
        g.fill(x + 10, cy - 2, tipX, cy + 1, IRON_LIGHT);
        int r = 5 + Math.round(bubble * 40);
        int color = glow(0.55F + screen.heat() * 0.4F);
        int glass = mix(color, 0xFFF8E8C0, 0.35F);
        // Пузырь: стенка и светлая середина
        g.fill(tipX, cy - r, tipX + r * 2, cy + r, glass);
        g.fill(tipX + 2, cy - r + 2, tipX + r * 2 - 2, cy + r - 2, mix(glass, 0xFF1C1612, 0.55F));
        g.fill(tipX + r / 2, cy - r + 3, tipX + r / 2 + 3, cy - r / 3, 0xFFFFF8E8);
        // Цель — силуэт зоны
        int lowR = 5 + Math.round(low * 40);
        int highR = 5 + Math.round(high * 40);
        g.fill(tipX + lowR * 2, cy + highR + 4, tipX + highR * 2, cy + highR + 6, ZONE);
        // Давление
        gauge(g, x + w - 24, y + 12, 8, h - 40, pressure, 0.3F, BURST - 0.05F, pressure > 0.8F ? BAD : TEXT);
        g.fill(x + w - 28, y + 12 + Math.round((h - 40) * (1 - BURST)), x + w - 12, y + 13 + Math.round((h - 40) * (1 - BURST)), BAD);
        gauge(g, x + 14, y + 12, 8, h - 40, bubble, low, high, TEXT);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (finished) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (bubble >= low) {
                finished = true;
                screen.sound(SoundEvents.GLASS_PLACE, 1.4F);
            } else {
                screen.show("blow_more", TEXT_DIM);
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            blowing = true;
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && blowing) {
            blowing = false;
            return true;
        }
        return false;
    }
}
