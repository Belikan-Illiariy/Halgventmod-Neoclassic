package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Толчение в ступе: пест поднимается и опускается в ритм — ЛКМ, когда он внизу (зелёная вспышка). В такт — камень
 * дробится, вразнобой — пест скользит и толчёное просыпается.
 */
final class StampStep extends SmithStep {
    private static final int BEAT_MS = 650;
    private static final int WINDOW_MS = 120;
    private static final float CRUSH = 0.2F;

    private final long start = System.currentTimeMillis();
    private final int count;
    private final boolean gold;
    private int current;
    private float crushed;
    private int onBeat;
    private int offBeat;
    private long struckAt = -1;

    StampStep(SmithWorkScreen screen, Random random, int what, int count) {
        super(screen, random);
        this.gold = what == 0;
        this.count = Math.max(1, count);
    }

    private long offset(long now) {
        long phase = (now - start) % BEAT_MS;
        return Math.min(phase, BEAT_MS - phase);
    }

    @Override
    boolean done() {
        return current >= count;
    }

    @Override
    int rawScore() {
        int total = onBeat + offBeat;
        return clampScore(total == 0 ? 50 : onBeat / (float) total * 110 - offBeat * 2);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.stamp_status", Math.min(current + 1, count), count,
                Math.round(crushed * 100), offBeat);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.stamp_hint");
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int cx = x + (w - 20) / 2;
        int bottom = y + h - 16;
        g.fill(cx - 30, bottom - 40, cx + 30, bottom, 0xFFA07038);
        g.fill(cx - 22, bottom - 36, cx + 22, bottom - 8, 0xFF3A2A18);
        int stuff = gold ? mix(0xFFE8E4D8, 0xFFC8B060, crushed) : mix(0xFFA02018, 0xFFE03A20, crushed);
        g.fill(cx - 20, bottom - 18, cx + 20, bottom - 8, stuff);
        double phase = ((now - start) % BEAT_MS) / (double) BEAT_MS;
        int lift = (int) Math.round((1 - Math.cos(phase * Math.PI * 2)) / 2 * 40);
        if (struckAt >= 0 && now - struckAt < 100) {
            lift = 0;
        }
        g.fill(cx - 5, bottom - 70 - lift, cx + 5, bottom - 18 - lift, 0xFF8A8070);
        boolean window = offset(now) <= WINDOW_MS;
        g.fill(x + w - 40, y + 16, x + w - 28, y + 28, window ? GOOD : TRACK);
        bar(g, x + 14, y + 10, w - 70, 4, crushed, 0, 0, TEXT);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || done()) {
            return false;
        }
        long now = System.currentTimeMillis();
        struckAt = now;
        if (offset(now) <= WINDOW_MS) {
            onBeat++;
            crushed += CRUSH;
            screen.sound(SoundEvents.STONE_HIT, 0.9F);
        } else {
            offBeat++;
            crushed += CRUSH * 0.3F;
            screen.show("stamp_off", WARN);
            screen.sound(SoundEvents.GRAVEL_HIT, 1.3F);
        }
        if (crushed >= 1) {
            crushed = 0;
            current++;
            screen.sound(SoundEvents.GRAVEL_BREAK, 1.0F);
        }
        return true;
    }
}
