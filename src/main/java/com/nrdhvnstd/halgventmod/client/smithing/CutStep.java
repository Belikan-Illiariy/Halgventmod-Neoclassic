package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Рубка горячим зубилом: метки, где рубить. Зубило ведите мышью, ЛКМ — удар. Чтобы перерубить, нужно
 * {@link #STRIKES} удара в одно место; удар мимо метки оставляет зарубку (брак). Чем ближе удары к метке, тем
 * чище рез.
 */
final class CutStep extends SmithStep {
    private static final int STRIKES = 3;
    private static final int SNAP = 6;        // пикселей: удар засчитывается в эту метку

    private final int cuts;
    private final float[] marks;
    private final int[] depth;
    private int stray;
    private float offsetTotal;
    private int hits;
    private int left;
    private int right;
    private int barY;
    private long struckAt = -1;
    private int struckX;

    CutStep(SmithWorkScreen screen, Random random, int cuts) {
        super(screen, random);
        this.cuts = Math.max(1, cuts);
        this.marks = new float[this.cuts];
        this.depth = new int[this.cuts];
        for (int i = 0; i < this.cuts; i++) {
            marks[i] = (i + 1) / (float) (this.cuts + 1) + (random.nextFloat() - 0.5F) * 0.04F;
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
    boolean done() {
        for (int d : depth) {
            if (d < STRIKES) {
                return false;
            }
        }
        return true;
    }

    @Override
    int rawScore() {
        return clampScore(100 - stray * 7 - (hits == 0 ? 0 : offsetTotal / hits * 5));
    }

    @Override
    Component status() {
        int made = 0;
        for (int d : depth) {
            made += d >= STRIKES ? 1 : 0;
        }
        return Component.translatable("gui.halgventmod.smith_work.cut_status", made, cuts, stray);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.cut_hint");
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int base = y + h - 40;
        anvil(g, x + 10, base, w - 40);
        left = x + 26;
        right = x + w - 30;
        barY = base - 12;
        int color = glow(screen.heat());
        g.fill(left, barY, right, base, color);
        for (int i = 0; i < cuts; i++) {
            int mx = left + Math.round((right - left) * marks[i]);
            int d = Math.min(STRIKES, depth[i]);
            if (d > 0) {
                g.fill(mx - 1, barY, mx + 2, barY + Math.round(12F * d / STRIKES), 0xFF0E0A06);
            }
            g.fill(mx, barY - 4, mx + 1, barY - 1, d >= STRIKES ? GOOD : MARK);
        }
        boolean hit = struckAt >= 0 && System.currentTimeMillis() - struckAt < 120;
        int cx = hit ? struckX : mouseX;
        if (cx > 0) {
            int tip = barY - (hit ? 0 : 5);
            g.fill(cx - 1, tip - 26, cx + 2, tip, IRON_LIGHT);
            g.fill(cx - 3, tip - 32, cx + 4, tip - 26, IRON_DARK);
            hammer(g, cx, tip - 32, hit ? 0 : 10);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || done()) {
            return false;
        }
        if (!screen.strike(this, minHeat())) {
            return true;
        }
        struckAt = System.currentTimeMillis();
        struckX = (int) mouseX;
        int best = -1;
        float bestOff = Float.MAX_VALUE;
        for (int i = 0; i < cuts; i++) {
            if (depth[i] >= STRIKES) {
                continue;
            }
            float off = (float) Math.abs(mouseX - (left + (right - left) * marks[i]));
            if (off < bestOff) {
                bestOff = off;
                best = i;
            }
        }
        if (best >= 0 && bestOff <= SNAP) {
            depth[best]++;
            hits++;
            offsetTotal += bestOff;
            screen.sound(SoundEvents.ANVIL_PLACE, 1.9F);
            if (depth[best] >= STRIKES) {
                screen.sound(SoundEvents.CHAIN_BREAK, 1.2F);
            }
        } else {
            stray++;
            screen.show("cut_stray", BAD);
            screen.sound(SoundEvents.ANVIL_PLACE, 1.5F);
        }
        return true;
    }
}
