package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Позолота огнём. Сначала зажатой ЛКМ кистью нанесите амальгаму (золото в ртути) на узор — пунктир; мимо узора —
 * золото зря. Нанесли (Enter или почти весь узор) — над огнём выпаривайте ртуть: держите ЛКМ — ближе к углям, отпустите
 * — дальше; жар держите в зелёной полосе, пока ртуть не уйдёт. Перегрели — позолота тускнеет и вздувается.
 */
final class GildStep extends SmithStep {
    private static final int COLS = 18;
    private static final int ROWS = 8;
    private static final float BAND_LOW = 0.45F;
    private static final float BAND_HIGH = 0.65F;
    private static final float DRIVE_SECONDS = 6;

    private final boolean[] pattern = new boolean[COLS * ROWS];
    private final boolean[] painted = new boolean[COLS * ROWS];
    private int patternCells;
    private int wasted;
    private boolean painting;
    private boolean fire;
    private boolean closer;
    private float temp;
    private float driven;
    private float burnt;
    private boolean finished;
    private int gx;
    private int gy;
    private int cell = 8;

    GildStep(SmithWorkScreen screen, Random random) {
        super(screen, random);
        int variant = random.nextInt(3);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                boolean on = switch (variant) {
                    case 0 -> r == 1 || r == ROWS - 2 || (c + r) % 5 == 0 && r > 1 && r < ROWS - 2;
                    case 1 -> Math.abs(r - ROWS / 2 + 0.5) + Math.abs((c % 6) - 2.5) < 3;
                    default -> c % 4 == 1 || r == 0 || r == ROWS - 1;
                };
                pattern[r * COLS + c] = on;
                patternCells += on ? 1 : 0;
            }
        }
    }

    private float coverage() {
        int n = 0;
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] && painted[i]) {
                n++;
            }
        }
        return n / (float) Math.max(1, patternCells);
    }

    @Override
    boolean done() {
        return finished;
    }

    @Override
    int rawScore() {
        return clampScore(100 - (1 - coverage()) * 70 - wasted * 1.5F - burnt * 12
                - (fire ? (1 - Math.min(1, driven / DRIVE_SECONDS)) * 40 : 40));
    }

    @Override
    Component status() {
        if (!fire) {
            return Component.translatable("gui.halgventmod.smith_work.gild_paint", Math.round(coverage() * 100), wasted);
        }
        return Component.translatable("gui.halgventmod.smith_work.gild_fire",
                Math.round(Math.min(1, driven / DRIVE_SECONDS) * 100));
    }

    @Override
    Component hint() {
        return Component.translatable(fire ? "gui.halgventmod.smith_work.gild_fire_hint"
                : "gui.halgventmod.smith_work.gild_paint_hint");
    }

    @Override
    void finishEarly() {
        if (!fire) {
            fire = true;
            painting = false;
        } else {
            finished = true;
        }
    }

    @Override
    void tick(float dt, long now) {
        if (!fire || finished) {
            return;
        }
        float target = closer ? 0.9F : 0.2F;
        temp += (target - temp) * Math.min(1, dt * 1.2F);
        if (temp >= BAND_LOW && temp <= BAND_HIGH) {
            driven += dt;
        } else if (temp > 0.75F) {
            burnt += dt * 0.5F;
        }
        if (driven >= DRIVE_SECONDS) {
            finished = true;
            screen.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F);
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        cell = Math.min((w - 30) / COLS, (h - 40) / ROWS);
        gx = x + (w - COLS * cell) / 2;
        gy = y + 16;
        for (int i = 0; i < pattern.length; i++) {
            int cx = gx + (i % COLS) * cell;
            int cy = gy + (i / COLS) * cell;
            int c = 0xFF8A6A3A; // бронза
            if (painted[i]) {
                c = fire ? mix(0xFFB0B0B8, 0xFFE8C040, Math.min(1, driven / DRIVE_SECONDS)) : 0xFFC0C0C8;
                if (burnt > 0.5F && fire) {
                    c = mix(c, 0xFF6A5A3A, Math.min(1, burnt / 3));
                }
            }
            g.fill(cx, cy, cx + cell, cy + cell, c);
            if (pattern[i] && !painted[i] && (i % 2 == 0)) {
                g.fill(cx + cell / 2, cy + cell / 2, cx + cell / 2 + 1, cy + cell / 2 + 1, MARK);
            }
        }
        if (fire) {
            int by = y + h - 18;
            for (int i = 0; i < 16; i++) {
                int fx = x + 20 + random.nextInt(w - 40);
                g.fill(fx, by + random.nextInt(8), fx + 3, by + 10, i % 3 == 0 ? 0xFFFFA040 : 0xFFB03010);
            }
            gauge(g, x + w - 14, y + 14, 6, h - 40, temp, BAND_LOW, BAND_HIGH, temp > 0.75F ? BAD : TEXT);
        }
    }

    private void paintAt(double mouseX, double mouseY) {
        int c = (int) Math.floor((mouseX - gx) / cell);
        int r = (int) Math.floor((mouseY - gy) / cell);
        if (c < 0 || r < 0 || c >= COLS || r >= ROWS) {
            return;
        }
        int i = r * COLS + c;
        if (!painted[i]) {
            painted[i] = true;
            if (!pattern[i]) {
                wasted++;
            }
            if (coverage() >= 0.96F) {
                fire = true;
                painting = false;
                screen.show("gild_now_fire", GOOD);
            }
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (fire) {
            closer = true;
        } else {
            painting = true;
            paintAt(mouseX, mouseY);
        }
        return true;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            painting = false;
            closer = false;
            return true;
        }
        return false;
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (painting && !fire) {
            paintAt(mouseX, mouseY);
        }
    }
}
