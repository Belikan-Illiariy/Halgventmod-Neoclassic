package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Мехи сыродутного горна: плавка идёт своё время, жар надо держать в зелёной полосе — она меняется: прогрев,
 * восстановление руды, спекание крицы. ЛКМ — качнуть мехи; часто качать без толку — мехи «захлёбываются». Уголь
 * прогорает: когда его мало, ПКМ — подсыпать. Счёт — сколько плавки жар был в полосе.
 */
final class BellowsStep extends SmithStep {
    private static final float[][] PHASES = {{0.0F, 0.45F, 0.62F}, {0.3F, 0.64F, 0.82F}, {0.75F, 0.76F, 0.9F}};
    private static final float PUMP = 0.07F;
    private static final float FAST = 0.35F;      // секунд — чаще мехи захлёбываются
    private static final float DECAY = 0.055F;
    private static final float BURN = 0.04F;      // угля в секунду

    private final float duration;
    private final boolean coal;
    private final boolean noFlux;
    private float time;
    private float heat = 0.25F;
    private float inBand;
    private float charcoal = 1;
    private float sinceLast = 1;
    private float puffs;

    BellowsStep(SmithWorkScreen screen, Random random, int batches, int flags) {
        super(screen, random);
        this.duration = 36 + batches * 4;
        this.coal = (flags & 1) != 0;
        this.noFlux = (flags & 2) != 0;
    }

    private float[] band() {
        float share = time / duration;
        float[] band = PHASES[0];
        for (float[] phase : PHASES) {
            if (share >= phase[0]) {
                band = phase;
            }
        }
        return band;
    }

    @Override
    boolean done() {
        return time >= duration;
    }

    @Override
    int rawScore() {
        return clampScore(time <= 0 ? 50 : inBand / Math.max(1, time) * 110);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.bellows_status",
                Component.translatable("gui.halgventmod.smith_work.bellows_phase_" + phaseIndex()),
                Math.round(time / duration * 100), Math.round(charcoal * 100));
    }

    private int phaseIndex() {
        float share = time / duration;
        int index = 0;
        for (int i = 0; i < PHASES.length; i++) {
            if (share >= PHASES[i][0]) {
                index = i;
            }
        }
        return index;
    }

    @Override
    Component hint() {
        return Component.translatable(coal ? "gui.halgventmod.smith_work.bellows_hint_coal"
                : noFlux ? "gui.halgventmod.smith_work.bellows_hint_noflux" : "gui.halgventmod.smith_work.bellows_hint");
    }

    @Override
    void tick(float dt, long now) {
        time += dt;
        sinceLast += dt;
        puffs = Math.max(0, puffs - dt * 2);
        charcoal = Math.max(0, charcoal - BURN * dt);
        heat = Math.max(0.1F, heat - DECAY * dt * (charcoal <= 0 ? 3 : 1));
        float[] band = band();
        if (heat >= band[1] && heat <= band[2]) {
            inBand += dt;
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int cx = x + (w - 40) / 2;
        int top = y + 14;
        int bottom = y + h - 16;
        // Глиняная шахта горна
        g.fill(cx - 26, top, cx + 26, bottom, 0xFF8A5A36);
        g.fill(cx - 18, top + 4, cx + 18, bottom - 12, 0xFF1A100A);
        int fill = Math.round((bottom - top - 20) * charcoal);
        int ember = glow(Math.min(1, 0.4F + heat * 0.6F));
        g.fill(cx - 16, bottom - 14 - fill, cx + 16, bottom - 14, shade(ember, 0.6F));
        for (int i = 0; i < 10; i++) {
            int ex = cx - 14 + random.nextInt(28);
            int ey = bottom - 14 - random.nextInt(Math.max(1, fill));
            g.fill(ex, ey, ex + 2, ey + 2, ember);
        }
        // Мехи слева, сопло в фурму
        int bx = cx - 70;
        int open = Math.round(8 + 10 * (1 - puffs));
        g.fill(bx, bottom - 30 - open, bx + 30, bottom - 30, 0xFF6E4626);
        g.fill(bx, bottom - 30, bx + 30, bottom - 26, WOOD_LIGHT);
        g.fill(bx + 30, bottom - 30, cx - 26, bottom - 27, IRON_DARK);
        // Шкала жара с полосой
        float[] band = band();
        gauge(g, x + w - 22, top, 8, bottom - top, heat, band[1], band[2], heat > band[2] ? BAD : TEXT);
        if (charcoal < 0.3F) {
            g.fill(cx - 16, top + 6, cx + 16, top + 8, (now / 300) % 2 == 0 ? WARN : TRACK);
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float push = sinceLast < FAST ? PUMP * 0.35F : PUMP;
            heat = Math.min(1, heat + push * (charcoal > 0 ? 1 : 0.3F));
            sinceLast = 0;
            puffs = 1;
            screen.sound(SoundEvents.FIRECHARGE_USE, 0.4F + heat * 0.4F);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (charcoal < 0.5F) {
                charcoal = 1;
                heat = Math.max(0.1F, heat - 0.08F);
                screen.sound(SoundEvents.GRAVEL_PLACE, 1.0F);
            }
            return true;
        }
        return false;
    }
}
