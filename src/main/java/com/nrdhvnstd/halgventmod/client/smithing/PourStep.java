package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Заливка формы: держите ЛКМ — тигель наклоняется, отпустите — выпрямляется. Лить надо ровной струёй: слишком
 * тонкая — металл стынет в литнике (спай), слишком толстая — плещет мимо. Остановитесь, когда форма полна
 * (зелёная метка): перелили — заусенцы, недолили — недолив. Расплав остывает — не тяните.
 */
final class PourStep extends SmithStep {
    private static final float FLOW_LOW = 0.25F;
    private static final float FLOW_HIGH = 0.75F;
    private static final float FILL_RATE = 0.32F;    // ⚠️ формы в секунду при полной струе
    private static final float WINDOW = 9;           // секунд, пока расплав жидкий

    private float tilt;
    private boolean holding;
    private float fill;
    private float time;
    private float thin;
    private float splash;
    private boolean finished;
    private boolean started;

    PourStep(SmithWorkScreen screen, Random random) {
        super(screen, random);
    }

    private float flow() {
        return Math.max(0, (tilt - 0.25F) / 0.75F);
    }

    @Override
    boolean done() {
        return finished;
    }

    @Override
    int rawScore() {
        float fillError = fill < 0.95F ? (0.95F - fill) * 160 : fill > 1.05F ? (fill - 1.05F) * 120 : 0;
        return clampScore(100 - fillError - thin * 9 - splash * 10);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.pour_status", Math.round(fill * 100),
                Math.max(0, Math.round(WINDOW - time)));
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.pour_hint");
    }

    @Override
    void tick(float dt, long now) {
        if (finished) {
            return;
        }
        tilt = holding ? Math.min(1, tilt + dt * 0.8F) : Math.max(0, tilt - dt * 1.6F);
        float flow = flow();
        if (flow > 0) {
            started = true;
            fill += FILL_RATE * flow * dt;
            if (flow < FLOW_LOW) {
                thin += dt;
            } else if (flow > FLOW_HIGH) {
                splash += dt;
            }
        }
        if (started) {
            time += dt;
        }
        // Остановились у полной формы — готово; расплав застыл — тоже
        if (started && flow <= 0 && fill >= 0.9F || time >= WINDOW) {
            finished = true;
            screen.sound(SoundEvents.LAVA_EXTINGUISH, 1.0F);
        }
    }

    @Override
    void finishEarly() {
        finished = true;
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        // Форма в земле справа
        int moldX = x + w / 2 + 10;
        int moldY = y + h - 50;
        int moldW = 70;
        int moldH = 34;
        g.fill(moldX - 6, moldY - 6, moldX + moldW + 6, moldY + moldH + 6, 0xFF6A5236);
        g.fill(moldX, moldY, moldX + moldW, moldY + moldH, 0xFF2A1E14);
        int filled = Math.round(Math.min(1.1F, fill) / 1.1F * moldH);
        int metal = glow(Math.max(0.5F, 0.95F - time / WINDOW * 0.4F));
        g.fill(moldX, moldY + moldH - filled, moldX + moldW, moldY + moldH, metal);
        int full = moldY + moldH - Math.round(1.0F / 1.1F * moldH);
        g.fill(moldX - 8, full, moldX - 2, full + 1, ZONE);
        // Тигель слева, наклоняется
        int cx = x + w / 2 - 50;
        int cy = y + 40;
        double a = Math.toRadians(tilt * 80);
        for (int i = 0; i < 20; i++) {
            for (int j = -10; j <= 10; j++) {
                int px = cx + (int) Math.round(j * Math.cos(a) - i * Math.sin(a) * -0.2);
                int py = cy + (int) Math.round(j * Math.sin(a) + i);
                g.fill(px, py, px + 1, py + 1, 0xFF7A4A2A);
            }
        }
        float flow = flow();
        if (flow > 0) {
            int lipX = cx + (int) Math.round(10 * Math.cos(a));
            int lipY = cy + (int) Math.round(10 * Math.sin(a));
            int width = Math.max(1, Math.round(flow * 5));
            g.fill(lipX, lipY, lipX + width, moldY + moldH - filled, metal);
            if (flow > FLOW_HIGH) {
                for (int i = 0; i < 4; i++) {
                    g.fill(moldX + random.nextInt(moldW), moldY - random.nextInt(8), moldX + random.nextInt(moldW) + 1,
                            moldY - random.nextInt(8) + 1, SPARK);
                }
            }
        }
        bar(g, x + 14, y + 12, w - 40, 5, flow, FLOW_LOW, FLOW_HIGH, flow > FLOW_HIGH ? BAD : TEXT);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            holding = true;
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            holding = false;
            return true;
        }
        return false;
    }
}
