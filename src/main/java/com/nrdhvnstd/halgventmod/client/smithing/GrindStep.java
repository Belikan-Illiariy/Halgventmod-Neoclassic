package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Заточка на точиле: клинок лежит кромкой на круге, тёмные места кромки — тупые. Мышью ведите клинок влево-вправо —
 * точится то место, что над кругом (светлая отметка). Зажмите ЛКМ — прижать: чем дольше держите, тем сильнее нажим,
 * тем быстрее точится, но тем быстрее греется кромка (рыжеет). Перегрели — синеет: закалка там пропала. ПКМ —
 * полить водой: кромка остывает. Готово — когда наточено по всей длине (или Enter).
 */
final class GrindStep extends SmithStep {
    private static final int SEGMENTS = 16;
    private static final float RATE = 1.1F;          // ⚠️ заточки в секунду при полном нажиме
    private static final float HEAT_RATE = 1.3F;     // нагрев в секунду при полном нажиме (растёт как квадрат нажима)
    private static final float COOL = 0.22F;         // остывание в секунду
    private static final float POUR_COOL = 0.55F;       // столько снимает полив
    private static final float WATER_COOLDOWN = 1.2F;
    private static final float PRESS_UP = 1.6F;      // нажим в секунду, пока держите ЛКМ
    private static final float SHARP = 0.08F;        // тупее этого — ещё точить

    private final float[] dull = new float[SEGMENTS];
    private final float[] heat = new float[SEGMENTS];
    private final boolean[] burnt = new boolean[SEGMENTS];
    private float position = 0.5F;
    private float pressure;
    private boolean pressing;
    private float waterCooldown;
    private float splash;
    private float spin;
    private boolean finished;
    private int fieldX;
    private int fieldW;

    /** wear — износ вещи (0–100) при подточке на точиле; −1 — новая кромка на наковальне. */
    GrindStep(SmithWorkScreen screen, Random random, int wear) {
        super(screen, random);
        float base = wear < 0 ? 0.85F : Math.max(0.3F, wear / 100F);
        for (int i = 0; i < SEGMENTS; i++) {
            dull[i] = Math.min(1, base * (0.65F + random.nextFloat() * 0.5F));
        }
    }

    @Override
    boolean done() {
        if (finished) {
            return true;
        }
        for (float d : dull) {
            if (d > SHARP) {
                return false;
            }
        }
        return true;
    }

    @Override
    void finishEarly() {
        finished = true;
    }

    private float dullness() {
        float total = 0;
        for (float d : dull) {
            total += d;
        }
        return total / SEGMENTS;
    }

    private int burntCount() {
        int n = 0;
        for (boolean b : burnt) {
            n += b ? 1 : 0;
        }
        return n;
    }

    @Override
    int rawScore() {
        return clampScore(100 - dullness() * 130 - burntCount() * 8);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.grind_status", Math.round((1 - dullness()) * 100),
                burntCount());
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.grind_hint");
    }

    private int under() {
        return Math.max(0, Math.min(SEGMENTS - 1, (int) ((1 - position) * SEGMENTS))); // клинок идёт за мышью
    }

    @Override
    void tick(float dt, long now) {
        spin += dt * (2 + pressure * 3);
        waterCooldown = Math.max(0, waterCooldown - dt);
        splash = Math.max(0, splash - dt);
        pressure = pressing ? Math.min(1, pressure + PRESS_UP * dt) : Math.max(0, pressure - PRESS_UP * 2 * dt);
        int at = under();
        for (int i = 0; i < SEGMENTS; i++) {
            float k = i == at ? 1 : Math.abs(i - at) == 1 ? 0.35F : 0;
            if (k > 0 && pressure > 0) {
                dull[i] = Math.max(0, dull[i] - RATE * pressure * k * dt * (burnt[i] ? 0.5F : 1));
                heat[i] += HEAT_RATE * pressure * pressure * k * dt;
            }
            heat[i] = Math.max(0, heat[i] - COOL * dt);
            if (heat[i] >= 1 && !burnt[i]) {
                burnt[i] = true;
                screen.show("grind_burnt", BAD);
                screen.sound(SoundEvents.FIRE_EXTINGUISH, 1.6F);
            }
        }
        if (pressure > 0.1F && random.nextFloat() < dt * 6) {
            screen.sound(SoundEvents.GRINDSTONE_USE, 0.8F + pressure * 0.6F);
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        fieldX = x + 16;
        fieldW = w - 50;
        int cx = x + (w - 30) / 2;
        int r = 34;
        int cy = y + h - 10 + r / 3;
        // Круг: камень с бегущими штрихами, внизу корыто с водой
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0xFF8A847A);
        }
        for (int i = 0; i < 8; i++) {
            double a = spin + i * Math.PI / 4;
            int px = cx + (int) Math.round(Math.cos(a) * (r - 6));
            int py = cy + (int) Math.round(Math.sin(a) * (r - 6));
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xFF5E5A54);
        }
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, WOOD);
        // Клинок над кругом: под кругом — та часть кромки, что выбрана мышью
        float sw = fieldW / (float) SEGMENTS;
        int edgeY = cy - r - 1;
        int shift = Math.round((0.5F - (under() + 0.5F) / SEGMENTS) * fieldW) + (cx - (fieldX + fieldW / 2));
        int bladeTop = edgeY - 12 - Math.round(pressure * 2);
        for (int i = 0; i < SEGMENTS; i++) {
            int sx0 = fieldX + Math.round(i * sw) + shift;
            int sx1 = fieldX + Math.round((i + 1) * sw) + shift;
            int steel = burnt[i] ? 0xFF3A4AB0 : mix(0xFFE4E8EE, 0xFF4E525A, dull[i]);
            int hot = mix(steel, 0xFFFF8A3A, Math.min(1, heat[i]) * 0.75F);
            g.fill(sx0, bladeTop, sx1, edgeY - 3, mix(IRON_LIGHT, hot, 0.3F));
            g.fill(sx0, edgeY - 3, sx1, edgeY + Math.round(pressure * 2), hot);
            if (i == under()) {
                g.fill(sx0, bladeTop - 4, sx1, bladeTop - 2, MARK);
            }
        }
        g.fill(fieldX + shift - 22, bladeTop + 1, fieldX + shift, edgeY - 4, WOOD);
        // Искры от нажима и брызги от полива
        if (pressure > 0.15F) {
            for (int i = 0; i < 3 + Math.round(pressure * 8); i++) {
                int sx = cx + random.nextInt(24) + 2;
                int sy = edgeY - random.nextInt(10);
                g.fill(sx, sy, sx + 1, sy + 1, SPARK);
            }
        }
        if (splash > 0) {
            for (int i = 0; i < 14; i++) {
                int sx = cx - 40 + random.nextInt(80);
                int sy = edgeY - 4 - random.nextInt(16);
                g.fill(sx, sy, sx + 1, sy + 2, 0xC07AB8E0);
            }
        }
        // Нажим — шкала справа; полив — точка готовности
        gauge(g, x + w - 18, y + 14, 8, h - 40, pressure, 0.35F, 0.75F, pressure > 0.75F ? WARN : TEXT);
        g.fill(x + w - 20, y + h - 18, x + w - 8, y + h - 12, waterCooldown <= 0 ? WATER : TRACK);
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (fieldW > 0) {
            position = (float) Math.max(0, Math.min(0.999, (mouseX - fieldX) / fieldW));
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressing = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (waterCooldown <= 0) {
                for (int i = 0; i < SEGMENTS; i++) {
                    heat[i] = Math.max(0, heat[i] - POUR_COOL);
                }
                waterCooldown = WATER_COOLDOWN;
                splash = 0.5F;
                screen.sound(SoundEvents.GENERIC_SPLASH, 1.4F);
            }
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressing = false;
            return true;
        }
        return false;
    }
}
