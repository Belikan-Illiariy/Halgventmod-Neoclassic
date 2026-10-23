package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Сборка молотком: гвозди, заклёпки, зубья чесалки, клин насадки — по одному. Гвоздь в заготовке качается.
 * Зажмите ЛКМ — молоток набирает замах (шкала ходит туда и обратно), отпустите — удар:
 * <ul>
 *   <li>гвоздь стоит криво (вне зелёного на дуге) — гнётся: выдернуть и забить новый;</li>
 *   <li>слабо — чуть вошёл; в меру — на треть; крепко — на половину;</li>
 *   <li>со всей силы — по последнему удару молоток оставляет вмятину, а раньше — гвоздь может согнуть.</li>
 * </ul>
 * Каждый лишний удар сверх трёх на гвоздь немного снижает счёт.
 */
final class NailStep extends WorkStep {
    // Меняйте здесь
    private static final float AMPLITUDE = 22.0F;       // качание гвоздя, градусов
    private static final float STRAIGHT = 7.0F;         // прямо — не дальше этого
    private static final int SWING_MS = 900;            // замах: от нуля до полного и обратно
    private static final float WEAK = 0.4F;             // замах ниже — слабо
    private static final float FIRM = 0.8F;             // выше — крепко
    private static final float FULL = 0.95F;            // выше — со всей силы
    private static final float BEND_ON_FULL = 0.4F;     // шанс согнуть со всей силы, если гвоздь ещё торчит

    private final int nails;
    private int current;
    private float height = 1.0F;        // сколько торчит, 0–1
    private int bent;
    private int dents;
    private int strikes;
    private float period;
    private float phase;
    private boolean pressed;
    private long pressedAt;
    private long struckAt = -1;
    private float lastPower;

    NailStep(BenchWorkScreen screen, BenchProducts.Product product, BenchProducts.Step step, Random random) {
        super(screen, product, step, random);
        this.nails = Math.max(1, step.amount());
        nextNail();
    }

    private void nextNail() {
        height = 1.0F;
        period = 1200 + random.nextInt(600);
        phase = random.nextFloat() * 6.28F;
    }

    private float tilt(long now) {
        return AMPLITUDE * (float) Math.sin(now * Math.PI * 2 / period + phase);
    }

    private float power(long now) {
        if (!pressed) {
            return 0;
        }
        float t = ((now - pressedAt) % SWING_MS) / (float) SWING_MS;
        return t < 0.5F ? t * 2 : 2 - t * 2;
    }

    private void strike(long now) {
        float power = power(now);
        float tilt = tilt(now);
        lastPower = power;
        struckAt = now;
        strikes++;
        if (Math.abs(tilt) > STRAIGHT || power > FULL && height > 0.5F && random.nextFloat() < BEND_ON_FULL) {
            bent++;
            screen.show("nail_bent", BAD);
            screen.sound(SoundEvents.CHAIN_HIT, 1.4F);
            nextNail();
            return;
        }
        float drive = power < WEAK ? 0.12F : power < FIRM ? 0.34F : 0.5F;
        height -= drive;
        screen.sound(SoundEvents.ANVIL_PLACE, 1.6F + 0.3F * power);
        if (height <= 0) {
            if (power > FULL) {
                dents++;
                screen.show("nail_dent", BAD);
            } else {
                screen.show("nail_home", GOOD);
            }
            current++;
            if (current < nails) {
                nextNail();
            }
        } else if (power < WEAK) {
            screen.show("nail_weak", TEXT);
        }
    }

    @Override
    boolean done() {
        return current >= nails;
    }

    @Override
    int score() {
        int extra = Math.max(0, strikes - 3 * Math.min(nails, current + 1));
        return clampScore(100 - bent * 14 - dents * 8 - extra * 3);
    }

    @Override
    Component status() {
        return Component.translatable(nails > 1 ? "gui.halgventmod.bench_work.nail_status"
                : "gui.halgventmod.bench_work.wedge_status", Math.min(current + 1, nails), nails, bent, dents);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.bench_work.nail_hint");
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int cx = x + w / 2 - 20;
        int boardTop = y + h - 34;
        // Доска сбоку и уже забитые гвозди
        g.fill(x + 20, boardTop, x + w - 60, boardTop + 18, WOOD);
        g.fill(x + 20, boardTop, x + w - 60, boardTop + 1, WOOD_TOP);
        g.fill(x + 20, boardTop + 17, x + w - 60, boardTop + 18, WOOD_DARK);
        for (int i = 0; i < Math.min(current, nails); i++) {
            int hx = x + 30 + i * 10;
            g.fill(hx - 2, boardTop - 1, hx + 3, boardTop + 1, STEEL_DARK);
        }
        if (current < nails) {
            // Гвоздь: наклонён, торчит на height
            float tilt = (float) Math.toRadians(tilt(now));
            int length = Math.round(8 + 32 * Math.max(0, height));
            int tipX = cx;
            int headX = tipX + (int) Math.round(Math.sin(tilt) * length);
            int headY = boardTop - (int) Math.round(Math.cos(tilt) * length);
            int steps = Math.max(1, length);
            for (int s = 0; s <= steps; s++) {
                int px = tipX + (headX - tipX) * s / steps;
                int py = boardTop + (headY - boardTop) * s / steps;
                g.fill(px - 1, py, px + 1, py + 1, STEEL);
            }
            g.fill(headX - 4, headY - 2, headX + 4, headY, STEEL_DARK);
            // Дуга прямоты над гвоздём: зелёное — бить можно
            int arcY = y + 14;
            for (int a = -30; a <= 30; a += 2) {
                int px = cx + a * 2;
                boolean ok = Math.abs(a) <= STRAIGHT;
                g.fill(px, arcY + Math.abs(a) / 4, px + 2, arcY + Math.abs(a) / 4 + 2, ok ? ZONE : TRACK);
            }
            float deg = tilt(now);
            int mx = cx + Math.round(deg * 2);
            g.fill(mx - 1, arcY - 3 + Math.round(Math.abs(deg) / 4), mx + 2, arcY + 6 + Math.round(Math.abs(deg) / 4),
                    Math.abs(deg) <= STRAIGHT ? GOOD : BAD);
            // Молоток: поднимается с замахом, после удара — на шляпке
            float power = power(now);
            boolean hit = struckAt >= 0 && now - struckAt < 150;
            int lift = hit ? 0 : Math.round(power * 26) + 4;
            int hx = headX + 6;
            int hy = headY - 6 - lift;
            g.fill(hx, hy, hx + 32, hy + 3, HANDLE);
            g.fill(hx - 8, hy - 5, hx + 2, hy + 8, 0xFF5E666E);
        }
        // Замах: шкала справа
        float shown = pressed ? power(now) : lastPower;
        gauge(g, x + w - 30, y + 18, 10, h - 40, shown, WEAK, FIRM, shown > FULL ? BAD : TEXT);
        g.fill(x + w - 33, y + 18 + Math.round((h - 40) * (1 - FULL)), x + w - 17, y + 19 + Math.round((h - 40) * (1 - FULL)), BAD);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && current < nails) {
            pressed = true;
            pressedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressed) {
            long now = System.currentTimeMillis();
            strike(now);
            pressed = false;
            return true;
        }
        return false;
    }
}
