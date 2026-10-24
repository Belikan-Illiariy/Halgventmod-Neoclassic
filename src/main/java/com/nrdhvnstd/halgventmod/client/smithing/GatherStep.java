package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Стеклодув — набор: трубку в горшок со стекломассой. Держите ЛКМ — на конец наматывается капля, и всё время
 * вращайте трубку (водите мышью влево-вправо): стоит — масса стекает (капли). Отпустите, когда капля в зелёной зоне
 * (её размер — от изделия). Мало — допустите ещё, много — тяжело и толсто.
 */
final class GatherStep extends SmithStep {
    // ⚠️
    private static final float GATHER_RATE = 0.22F;  // доли капли в секунду
    private static final float SPIN_DECAY = 1.6F;    // вращение гаснет, в секунду
    private static final float SPIN_PER_PIXEL = 0.012F;
    private static final float DRIP_AT = 0.25F;      // медленнее — стекает
    private static final float DRIP_SECONDS = 0.7F;

    private final float low;
    private final float high;
    private float gob;
    private float spin;
    private float dripTimer;
    private int drips;
    private boolean holding;
    private boolean finished;
    private double lastX = -1;

    /** @param size 0 — мелкое (склянки, бокал), 1 — среднее, 2 — крупное (бутыль, окно) */
    GatherStep(SmithWorkScreen screen, Random random, int size) {
        super(screen, random);
        this.low = 0.3F + 0.18F * size;
        this.high = low + 0.16F;
    }

    @Override
    boolean done() {
        return finished;
    }

    @Override
    int rawScore() {
        float center = (low + high) / 2;
        float miss = gob >= low && gob <= high ? Math.abs(gob - center) * 60 : Math.abs(gob - center) * 220;
        return clampScore(100 - miss - drips * 8);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.gather_status", Math.round(gob * 100), drips);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.gather_hint");
    }

    @Override
    void finishEarly() {
        if (gob >= low * 0.8F) {
            finished = true;
        }
    }

    @Override
    void tick(float dt, long now) {
        spin = Math.max(0, spin - SPIN_DECAY * dt);
        if (!holding || finished) {
            return;
        }
        gob = Math.min(1, gob + GATHER_RATE * dt);
        if (spin < DRIP_AT) {
            dripTimer += dt;
            if (dripTimer >= DRIP_SECONDS) {
                dripTimer = 0;
                drips++;
                gob = Math.max(0, gob - 0.05F);
                screen.show("gather_drip", WARN);
                screen.sound(SoundEvents.LAVA_POP, 1.4F);
            }
        } else {
            dripTimer = 0;
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        // Горшок со стекломассой
        int potX = x + w / 2 + 20;
        int potY = y + h - 30;
        g.fill(potX - 34, potY - 26, potX + 34, potY + 6, 0xFF5A2E1E);
        g.fill(potX - 30, potY - 22, potX + 30, potY + 2, 0xFFF0A040);
        g.fill(potX - 30, potY - 22, potX + 30, potY - 18, 0xFFFFD890);
        // Трубка и капля на конце: вращение — блики бегут
        int tipX = potX - 6;
        int tipY = potY - 20;
        g.fill(x + 10, tipY - 40, tipX, tipY - 37, IRON_LIGHT);
        for (int i = 0; i < 6; i++) {
            g.fill(x + 10 + i * (tipX - x - 10) / 6, tipY - 40, x + 11 + i * (tipX - x - 10) / 6, tipY - 37, IRON_DARK);
        }
        int r = 3 + Math.round(gob * 22);
        int color = mix(0xFFF0A040, 0xFFFFE0A0, spin);
        g.fill(tipX - r, tipY - 38 - r / 2, tipX + r, tipY - 38 + r, color);
        float phase = (now % 1000) / 1000F * spin;
        int glint = tipX - r + Math.round((r * 2 - 2) * ((phase * 3) % 1));
        g.fill(glint, tipY - 38 - r / 2 + 2, glint + 2, tipY - 38 + r - 2, 0xFFFFF4D8);
        // Шкала капли
        gauge(g, x + 14, y + 12, 8, h - 40, gob, low, high, TEXT);
        bar(g, x + 32, y + 12, 80, 4, Math.min(1, spin), DRIP_AT, 1, spin < DRIP_AT ? BAD : GOOD);
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (lastX >= 0) {
            spin = Math.min(1.2F, spin + (float) Math.abs(mouseX - lastX) * SPIN_PER_PIXEL);
        }
        lastX = mouseX;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || finished) {
            return false;
        }
        holding = true;
        screen.sound(SoundEvents.LAVA_AMBIENT, 1.2F);
        return true;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !holding) {
            return false;
        }
        holding = false;
        if (gob >= low) {
            finished = true; // вынули — набор окончен
        } else {
            screen.show("gather_more", TEXT_DIM);
        }
        return true;
    }
}
