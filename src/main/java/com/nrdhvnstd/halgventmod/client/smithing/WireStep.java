package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Волочение: прут тянут клещами через всё более узкие отверстия доски. Держите ЛКМ — тянуть; натяжение растёт —
 * отпустите, пока не лопнуло (красное). С каждым проходом проволока наклёпывается и тянется туже: ПКМ —
 * отжечь в горне (наклёп снимается). Лопнула — часть прохода заново.
 */
final class WireStep extends SmithStep {
    private static final float PULL = 0.32F;      // ⚠️ прохода в секунду
    private static final float TENSION_UP = 0.55F;
    private static final float TENSION_DOWN = 1.6F;
    private static final float ANNEAL = 1.2F;

    private final int passes;
    private int pass;
    private float progress;
    private float tension;
    private float hardness;
    private boolean pulling;
    private float annealing;
    private int breaks;
    private float overHard;

    WireStep(SmithWorkScreen screen, Random random, int passes) {
        super(screen, random);
        this.passes = Math.max(1, passes);
    }

    @Override
    boolean done() {
        return pass >= passes;
    }

    @Override
    int rawScore() {
        return clampScore(100 - breaks * 10 - overHard * 6);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.wire_status", Math.min(pass + 1, passes), passes,
                Math.round(hardness * 100), breaks);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.wire_hint");
    }

    @Override
    void tick(float dt, long now) {
        if (annealing > 0) {
            annealing -= dt;
            if (annealing <= 0) {
                hardness = 0;
                screen.show("annealed", GOOD);
            }
            return;
        }
        if (pulling && pass < passes) {
            float tight = 1 + hardness * 0.9F;
            progress += PULL * dt / tight;
            tension += TENSION_UP * tight * dt * (0.8F + (float) Math.abs(Math.sin(now / 170.0)) * 0.6F);
            if (hardness > 0.9F) {
                overHard += dt;
            }
            if (tension >= 1) {
                breaks++;
                tension = 0;
                progress = Math.max(0, progress - 0.3F);
                pulling = false;
                screen.show("wire_broke", BAD);
                screen.sound(SoundEvents.CHAIN_BREAK, 1.4F);
            }
            if (progress >= 1) {
                pass++;
                progress = 0;
                hardness = Math.min(1, hardness + 0.34F);
                screen.sound(SoundEvents.CHAIN_PLACE, 1.3F);
            }
        } else {
            tension = Math.max(0, tension - TENSION_DOWN * dt);
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int plateX = x + w / 2 - 20;
        int midY = y + h / 2;
        g.fill(plateX - 4, midY - 30, plateX + 4, midY + 30, IRON_DARK);
        for (int i = 0; i < 6; i++) {
            int hy = midY - 24 + i * 10;
            g.fill(plateX - 2, hy, plateX + 2, hy + 2 + (5 - i) / 2, i == Math.min(5, pass) ? 0xFF0E0A06 : 0xFF1A1A1A);
        }
        int wireY = midY - 24 + Math.min(5, pass) * 10;
        int thick = Math.max(1, 4 - pass / 2);
        int color = annealing > 0 ? glow(0.7F) : mix(IRON_LIGHT, 0xFFB4B8C0, hardness);
        g.fill(x + 20, wireY, plateX, wireY + thick + 1, color);
        int pulled = plateX + Math.round(progress * (w / 2F - 10));
        g.fill(plateX, wireY, pulled, wireY + thick, color);
        g.fill(pulled, wireY - 4, pulled + 8, wireY + thick + 4, IRON_DARK);
        bar(g, x + 14, y + 12, w - 40, 5, tension, 0, 0.8F, tension > 0.8F ? BAD : WARN);
        bar(g, x + 14, y + h - 14, w - 40, 4, hardness, 0, 0.6F, mix(GOOD, BAD, hardness));
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (annealing > 0) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pulling = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hardness > 0) {
            pulling = false;
            annealing = ANNEAL;
            screen.sound(SoundEvents.FIRE_AMBIENT, 1.2F);
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pulling = false;
            return true;
        }
        return false;
    }
}
