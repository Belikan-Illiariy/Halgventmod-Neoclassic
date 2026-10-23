package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Кузнечная сварка: две части внахлёст. ПКМ — посыпать флюсом (без него металл не схватится), разогреть до белого
 * (в горне качайте мехи) и бить ЛКМ в такт пульсу — каждый удар в такт сваривает шов. Мимо такта — окалина
 * отслаивается; по остывшему ниже белого — не схватывается. Шов готов, когда сварен по всей длине.
 */
final class WeldStep extends SmithStep {
    private static final int BEAT_MS = 460;
    private static final int WINDOW_MS = 120;     // ⚠️ попадание в такт
    private static final float WELD_HEAT = 0.88F;
    private static final float ON_BEAT = 0.14F;
    private static final float OFF_BEAT = 0.04F;

    private final long start = System.currentTimeMillis();
    private boolean flux;
    private float welded;
    private int offBeat;
    private int cold;
    private int noFlux;
    private long struckAt = -1;

    WeldStep(SmithWorkScreen screen, Random random) {
        super(screen, random);
    }

    @Override
    boolean hot() {
        return true;
    }

    @Override
    float minHeat() {
        return WELD_HEAT;
    }

    @Override
    boolean done() {
        return welded >= 1;
    }

    @Override
    int rawScore() {
        return clampScore(100 - offBeat * 3 - cold * 4 - noFlux * 6);
    }

    @Override
    Component status() {
        return Component.translatable(flux ? "gui.halgventmod.smith_work.weld_status"
                : "gui.halgventmod.smith_work.weld_status_noflux", Math.round(welded * 100), offBeat);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.weld_hint");
    }

    /** Насколько удар далёк от такта, мс. */
    private long offset(long now) {
        long phase = (now - start) % BEAT_MS;
        return Math.min(phase, BEAT_MS - phase);
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int base = y + h - 40;
        anvil(g, x + 10, base, w - 40);
        int left = x + 40;
        int right = x + w - 70;
        int mid = (left + right) / 2;
        int color = glow(screen.heat());
        g.fill(left, base - 10, mid + 20, base - 5, color);
        g.fill(mid - 20, base - 15, right, base - 10, shade(color, 0.85F));
        // Шов: сваренное — светлое
        int seam = mid - 20 + Math.round(40 * Math.min(1, welded));
        g.fill(mid - 20, base - 11, seam, base - 9, mix(color, 0xFFFFFFFF, 0.5F));
        if (flux) {
            for (int i = 0; i < 12; i++) {
                int fx = mid - 20 + (i * 37 % 40);
                g.fill(fx, base - 16, fx + 1, base - 15, 0xFFE8E0D0);
            }
        }
        // Пульс такта
        float pulse = 1 - offset(now) / (BEAT_MS / 2F);
        int r = 4 + Math.round(pulse * 6);
        int px = x + w - 46;
        int py = y + 24;
        g.fill(px - r, py - r, px + r, py + r, pulse > 1 - WINDOW_MS / (BEAT_MS / 2F) ? GOOD : TRACK);
        boolean hit = struckAt >= 0 && now - struckAt < 120;
        hammer(g, mid, base - 15, hit ? 0 : 10 + Math.round(pulse * 8));
        if (hit && screen.heat() >= WELD_HEAT) {
            for (int i = 0; i < 10; i++) {
                int sx = mid + random.nextInt(60) - 30;
                int sy = base - 15 - random.nextInt(14);
                g.fill(sx, sy, sx + 1, sy + 1, SPARK);
            }
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (!flux) {
                flux = true;
                screen.sound(SoundEvents.SAND_PLACE, 1.2F);
                screen.show("weld_fluxed", GOOD);
            }
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || done()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (!screen.strike(this, 0)) {
            return true;
        }
        struckAt = now;
        screen.sound(SoundEvents.ANVIL_PLACE, 1.3F);
        if (screen.heat() < WELD_HEAT) {
            cold++;
            screen.show("weld_cold", BAD);
            return true;
        }
        boolean onBeat = offset(now) <= WINDOW_MS;
        float gain = onBeat ? ON_BEAT : OFF_BEAT;
        if (!flux) {
            gain *= 0.3F;
            noFlux++;
            screen.show("weld_noflux", WARN);
        }
        if (!onBeat) {
            offBeat++;
        }
        welded += gain;
        if (welded >= 1) {
            screen.sound(SoundEvents.ANVIL_USE, 1.2F);
        }
        return true;
    }
}
