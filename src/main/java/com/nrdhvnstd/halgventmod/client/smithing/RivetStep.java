package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Клёпка и насадка: заклёпка стоит в отверстии и качается. На каждую — два удара: первый тяжёлый (посадить),
 * второй в меру (оформить головку); бить, когда стоит прямо (зелёное на дуге). Криво — заклёпку гнёт, слабо —
 * болтается, слишком сильно вторым — головку плющит.
 */
final class RivetStep extends SmithStep {
    private static final float AMPLITUDE = 20;
    private static final float STRAIGHT = 7;

    private final int rivets;
    private final Swing swing = new Swing();
    private int current;
    private int stroke;
    private int bent;
    private int loose;
    private int flat;
    private float period;
    private float phase;
    private long struckAt = -1;

    RivetStep(SmithWorkScreen screen, Random random, int rivets) {
        super(screen, random);
        this.rivets = Math.max(1, rivets);
        next();
    }

    private void next() {
        period = 1100 + random.nextInt(600);
        phase = random.nextFloat() * 6.28F;
    }

    private float tilt(long now) {
        return AMPLITUDE * (float) Math.sin(now * Math.PI * 2 / period + phase);
    }

    @Override
    boolean done() {
        return current >= rivets;
    }

    @Override
    int rawScore() {
        return clampScore(100 - bent * 10 - loose * 6 - flat * 5);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.rivet_status", Math.min(current + 1, rivets), rivets,
                stroke == 0 ? 1 : 2, bent + loose + flat);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.rivet_hint");
    }

    private void strike(long now) {
        float power = swing.release(now);
        float tilt = tilt(now);
        struckAt = now;
        screen.sound(SoundEvents.ANVIL_PLACE, 1.8F + power * 0.2F);
        if (Math.abs(tilt) > STRAIGHT) {
            bent++;
            screen.show("rivet_bent", BAD);
            next();
            stroke = 0;
            return;
        }
        if (stroke == 0) {
            if (power < Swing.HEAVY - 0.1F) {
                loose++;
                screen.show("rivet_loose", WARN);
            }
            stroke = 1;
            next();
        } else {
            if (power > Swing.HEAVY) {
                flat++;
                screen.show("rivet_flat", WARN);
            } else if (power < Swing.LIGHT - 0.1F) {
                loose++;
                screen.show("rivet_loose", WARN);
            } else {
                screen.show("rivet_good", GOOD);
            }
            stroke = 0;
            current++;
            next();
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int base = y + h - 40;
        anvil(g, x + 10, base, w - 40);
        int plateTop = base - 10;
        g.fill(x + 30, plateTop, x + w - 50, base, IRON);
        g.fill(x + 30, plateTop, x + w - 50, plateTop + 1, IRON_LIGHT);
        for (int i = 0; i < Math.min(current, 12); i++) {
            int hx = x + 38 + i * 10;
            g.fill(hx - 2, plateTop - 2, hx + 3, plateTop, IRON_LIGHT);
        }
        if (current < rivets) {
            int cx = x + (w - 20) / 2;
            float tilt = (float) Math.toRadians(tilt(now));
            int length = stroke == 0 ? 14 : 6;
            int headX = cx + (int) Math.round(Math.sin(tilt) * length);
            int headY = plateTop - (int) Math.round(Math.cos(tilt) * length);
            for (int s = 0; s <= length; s++) {
                int px = cx + (headX - cx) * s / Math.max(1, length);
                int py = plateTop + (headY - plateTop) * s / Math.max(1, length);
                g.fill(px - 1, py, px + 2, py + 1, IRON_LIGHT);
            }
            g.fill(headX - 3, headY - 2, headX + 4, headY, stroke == 0 ? IRON_LIGHT : IRON_DARK);
            int arcY = y + 14;
            for (int a = -30; a <= 30; a += 2) {
                int px = cx + a * 2;
                g.fill(px, arcY + Math.abs(a) / 4, px + 2, arcY + Math.abs(a) / 4 + 2, Math.abs(a) <= STRAIGHT ? ZONE : TRACK);
            }
            float deg = tilt(now);
            int mx = cx + Math.round(deg * 2);
            g.fill(mx - 1, arcY - 3 + Math.round(Math.abs(deg) / 4), mx + 2, arcY + 6 + Math.round(Math.abs(deg) / 4),
                    Math.abs(deg) <= STRAIGHT ? GOOD : BAD);
            boolean hit = struckAt >= 0 && now - struckAt < 120;
            hammer(g, headX, headY, hit ? 0 : swing.lift(now));
        }
        swing.render(g, x + w - 16, y + 14, h - 32, now);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && current < rivets) {
            swing.press(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && swing.pressed()) {
            strike(System.currentTimeMillis());
            return true;
        }
        return false;
    }
}
