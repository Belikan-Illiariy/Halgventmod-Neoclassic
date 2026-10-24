package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Стеклодув — катать и формовать (у оконного стекла — раскрутить в лист): вращайте трубку, водя мышью влево-вправо,
 * так, чтобы скорость держалась в зелёной зоне. Медленно — стекло оседает набок, быстро — бьёт и рябит. Форма
 * складывается, пока скорость в зоне и стекло горячее (остыло — пробел, разогреть в окне печи).
 */
final class SpinStep extends SmithStep {
    // ⚠️
    private static final float LOW = 0.4F;
    private static final float HIGH = 0.72F;
    private static final float SPEED_PER_PIXEL = 0.006F;
    private static final float SPEED_DECAY = 1.1F;   // в секунду
    private static final float WORKABLE = 0.35F;

    private final float seconds;
    private final boolean sheet;
    private float speed;
    private float progress;
    private float sag;
    private float wobble;
    private double lastX = -1;
    private float angle;

    /** @param sheet раскрутить оконный лист (дольше) — иначе катать изделие */
    SpinStep(SmithWorkScreen screen, Random random, boolean sheet) {
        super(screen, random);
        this.sheet = sheet;
        this.seconds = sheet ? 9 : 6;
    }

    @Override
    boolean hot() {
        return true;
    }

    @Override
    float minHeat() {
        return WORKABLE;
    }

    @Override
    boolean done() {
        return progress >= 1;
    }

    @Override
    int rawScore() {
        return clampScore(100 - sag * 14 - wobble * 14);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.spin_status", Math.round(progress * 100),
                Math.round(sag + wobble));
    }

    @Override
    Component hint() {
        return Component.translatable(sheet ? "gui.halgventmod.smith_work.spin_sheet_hint" : "gui.halgventmod.smith_work.spin_hint");
    }

    @Override
    void tick(float dt, long now) {
        speed = Math.max(0, speed - SPEED_DECAY * dt);
        angle += speed * dt * 12;
        if (screen.inForge()) {
            return;
        }
        if (speed < LOW) {
            sag += dt * (LOW - speed) * 2; // оседает
        } else if (speed > HIGH) {
            wobble += dt * (speed - HIGH) * 3; // бьёт
        } else if (screen.heat() >= WORKABLE) {
            progress = Math.min(1, progress + dt / seconds);
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int cy = y + h / 2;
        int cx = x + w / 2 + 10;
        g.fill(x + 10, cy - 2, cx - 10, cy + 1, IRON_LIGHT);
        int color = mix(glow(0.5F + screen.heat() * 0.45F), 0xFFF8E8C0, 0.3F);
        int droop = Math.round(Math.min(12, sag * 3));
        if (sheet) {
            // Лист раскрывается диском: ширина — от готовности
            int r = 8 + Math.round(progress * 34);
            int squash = Math.max(2, Math.round(r * Math.abs((float) Math.cos(angle)) * 0.35F) + 2);
            g.fill(cx - squash, cy - r + droop, cx + squash, cy + r + droop, color);
        } else {
            int r = 14;
            g.fill(cx - 10, cy - r + droop, cx + 20, cy + r + droop, color);
            g.fill(cx + 18, cy - 5 + droop, cx + 26, cy + 5 + droop, color);
            int stripe = cx - 10 + Math.round(28 * (float) ((Math.sin(angle) + 1) / 2));
            g.fill(stripe, cy - r + 2 + droop, stripe + 2, cy + r - 2 + droop, 0xFFFFF4D8);
        }
        bar(g, x + 20, y + h - 26, w - 40, 6, Math.min(1, speed), LOW, HIGH, speed < LOW || speed > HIGH ? WARN : GOOD);
        bar(g, x + 20, y + 12, w - 40, 3, progress, 0, 0, GOOD);
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (lastX >= 0) {
            speed = Math.min(1.1F, speed + (float) Math.abs(mouseX - lastX) * SPEED_PER_PIXEL);
        }
        lastX = mouseX;
    }
}
