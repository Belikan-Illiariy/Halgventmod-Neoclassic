package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Закалка в бочке: дождитесь, пока остывающая вещь дойдёт до вишнёво-оранжевого (полоса на шкале жара), и ЛКМ —
 * окунуть; держите ЛКМ и водите мышью вверх-вниз, пока не перестанет шипеть. Окунули слишком горячей — может
 * треснуть, остывшей — не закалится; не водили — мягкие пятна. Вода закаливает жёстче и чаще трескает, рассол —
 * ровнее, масло — бережнее, но мягче.
 */
final class QuenchStep extends SmithStep {
    private static final float LOW = 0.6F;
    private static final float HIGH = 0.76F;
    private static final float DIP_SECONDS = 2.4F;
    private static final float AGITATION = 140;     // ⚠️ пикселей в секунду — довольно водить

    private final int liquid;
    private int phase;                  // 0 — ждать цвета, 1 — в бочке, 2 — готово
    private float dipHeat;
    private float dipTime;
    private float moved;
    private float heldTime;
    private boolean holding;
    private double lastY;
    private int result = 50;
    private boolean cracked;

    QuenchStep(SmithWorkScreen screen, Random random, int liquid) {
        super(screen, random);
        this.liquid = liquid == 0 ? 1 : liquid;
    }

    @Override
    boolean hot() {
        return phase == 0;
    }

    @Override
    float minHeat() {
        return LOW;
    }

    @Override
    boolean done() {
        return phase == 2;
    }

    @Override
    int rawScore() {
        return phase == 2 ? result : 50;
    }

    @Override
    Component status() {
        if (phase == 0) {
            return Component.translatable("gui.halgventmod.smith_work.quench_wait",
                    Component.translatable("gui.halgventmod.smith_work.liquid_" + liquid));
        }
        return Component.translatable(cracked ? "gui.halgventmod.smith_work.quench_cracked"
                : "gui.halgventmod.smith_work.quench_dip", Math.round(Math.min(1, dipTime / DIP_SECONDS) * 100));
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.quench_hint");
    }

    @Override
    void tick(float dt, long now) {
        if (phase != 1) {
            return;
        }
        dipTime += dt;
        if (holding) {
            heldTime += dt;
        }
        screen.setHeat(Math.max(0.15F, dipHeat * (1 - dipTime / DIP_SECONDS)));
        if (dipTime >= DIP_SECONDS) {
            finish();
        }
    }

    private void finish() {
        float score = 100;
        if (dipHeat > HIGH) {
            score -= (dipHeat - HIGH) * 250;
        } else if (dipHeat < LOW) {
            score -= (LOW - dipHeat) * 300;
        }
        float agitation = Math.min(1, moved / DIP_SECONDS / AGITATION);
        float held = Math.min(1, heldTime / DIP_SECONDS);
        score -= (1 - agitation) * 18 + (1 - held) * 25;
        float crackRisk = Math.max(0, dipHeat - 0.74F) * 4 + (1 - agitation) * 0.15F;
        float factor = liquid == 1 ? 1.0F : liquid == 2 ? 0.8F : 0.35F;
        if (random.nextFloat() < crackRisk * factor) {
            cracked = true;
            score -= 35;
            screen.show("quench_crack", BAD);
            screen.sound(SoundEvents.ANVIL_LAND, 1.9F);
        }
        score += liquid == 2 ? 3 : liquid == 3 ? -6 : 0;
        if (dipHeat < 0.55F) {
            screen.show("quench_soft", WARN);
        }
        result = clampScore(score);
        phase = 2;
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int tubLeft = x + 50;
        int tubRight = x + w - 70;
        int surface = y + h - 60;
        int liquidColor = liquid == 3 ? 0xFF6A5420 : liquid == 2 ? 0xFF3E6A7E : WATER;
        g.fill(tubLeft - 4, surface - 6, tubRight + 4, y + h - 8, WOOD);
        g.fill(tubLeft, surface, tubRight, y + h - 12, liquidColor);
        g.fill(tubLeft, surface, tubRight, surface + 1, mix(liquidColor, 0xFFFFFFFF, 0.3F));
        int bladeX = (tubLeft + tubRight) / 2;
        int bladeTop = phase == 0 ? surface - 56 : surface - 20 + (int) Math.round(Math.sin(now / 70.0) * (holding ? 4 : 0));
        int color = glow(screen.heat());
        g.fill(bladeX - 3, bladeTop, bladeX + 3, bladeTop + 48, color);
        g.fill(bladeX - 2, bladeTop - 18, bladeX + 2, bladeTop, IRON_DARK);
        if (phase == 1) {
            for (int i = 0; i < 8; i++) {
                int sx = bladeX + random.nextInt(30) - 15;
                int sy = surface - random.nextInt(24);
                g.fill(sx, sy, sx + 2, sy + 2, 0xA0E0E0E0);
            }
        }
        // Полоса цвета закалки
        int bx = x + 14;
        int by = y + 12;
        bar(g, bx, by, w - 40, 5, screen.heat(), LOW, HIGH, glow(screen.heat()));
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (phase == 0) {
            dipHeat = screen.heat();
            phase = 1;
            screen.sound(SoundEvents.FIRE_EXTINGUISH, 1.2F);
            screen.sound(SoundEvents.GENERIC_SPLASH, 1.0F);
        }
        holding = true;
        lastY = mouseY;
        return true;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            holding = false;
            return true;
        }
        return false;
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (phase == 1 && holding) {
            moved += (float) Math.abs(mouseY - lastY);
        }
        lastY = mouseY;
    }
}
