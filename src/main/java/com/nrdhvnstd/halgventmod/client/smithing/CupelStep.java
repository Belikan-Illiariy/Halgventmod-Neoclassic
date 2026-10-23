package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.smithing.CupelBlockEntity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Купель: ЛКМ — качать мехи, жар держите в полосе — тогда свинец выгорает в глёт и уходит в стенки чаши. Когда
 * свинца почти не останется, серебро вспыхнет — «проблеск»: ПКМ — снять купель с огня. Рано — серебро со свинцом,
 * поздно — часть серебра выгорит.
 */
final class CupelStep extends SmithStep {
    private static final float LOW = 0.68F;
    private static final float HIGH = 0.85F;
    private static final float PUMP = 0.07F;
    private static final float DECAY = 0.05F;
    private static final float FLASH_SECONDS = 1.4F;
    private static final float LATE_AFTER = 2.5F;

    private final float needed;
    private float heat = 0.4F;
    private float burned;
    private float time;
    private float inBand;
    private float flashAt = -1;
    private int timing = -1;

    CupelStep(SmithWorkScreen screen, Random random, int count) {
        super(screen, random);
        this.needed = 14 + count * 3 + random.nextFloat() * 6;
    }

    @Override
    boolean done() {
        return timing >= 0;
    }

    @Override
    int rawScore() {
        int base = clampScore(time <= 0 ? 50 : inBand / Math.max(1, time) * 110);
        return timing == CupelBlockEntity.ON_TIME ? base : base - 15;
    }

    @Override
    List<Integer> result() {
        return List.of(score(), Math.max(0, timing));
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.cupel_status", Math.round(Math.min(1, burned / needed) * 100));
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.cupel_hint");
    }

    @Override
    void tick(float dt, long now) {
        if (done()) {
            return;
        }
        time += dt;
        heat = Math.max(0.2F, heat - DECAY * dt);
        if (heat >= LOW && heat <= HIGH) {
            inBand += dt;
            burned += dt;
        } else if (heat > HIGH) {
            burned += dt * 0.6F;
        }
        if (flashAt < 0 && burned >= needed) {
            flashAt = time;
            screen.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F);
        }
        if (flashAt >= 0 && time - flashAt > LATE_AFTER) {
            timing = CupelBlockEntity.LATE;
            screen.show("cupel_late", BAD);
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int cx = x + (w - 30) / 2;
        int cy = y + h / 2 + 4;
        g.fill(cx - 46, cy - 4, cx + 46, cy + 22, 0xFFB8B0A0);
        float left = Math.max(0.06F, 1 - burned / needed);
        int r = Math.round(34 * left + 4);
        boolean flash = flashAt >= 0 && time - flashAt < FLASH_SECONDS;
        int pool = flash ? 0xFFFFFFFF : flashAt >= 0 ? 0xFFD0D4DC : glow(0.5F + heat * 0.4F);
        for (int dy = -8; dy <= 8; dy++) {
            int half = (int) (r * Math.sqrt(1 - dy * dy / 64.0));
            g.fill(cx - half, cy + dy / 2, cx + half, cy + dy / 2 + 1, pool);
        }
        // Глёт по краю — желтеет
        g.fill(cx - 46, cy - 4, cx + 46, cy - 2, mix(0xFFB8B0A0, 0xFFE0B040, Math.min(1, burned / needed)));
        if (flash) {
            g.fill(cx - r - 6, cy - 10, cx + r + 6, cy + 10, 0x40FFFFFF);
        }
        gauge(g, x + w - 14, y + 14, 6, h - 30, heat, LOW, HIGH, heat > HIGH ? BAD : TEXT);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (done()) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            heat = Math.min(1, heat + PUMP);
            screen.sound(SoundEvents.FIRECHARGE_USE, 0.5F + heat * 0.4F);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (flashAt < 0) {
                timing = CupelBlockEntity.EARLY;
                screen.show("cupel_early", WARN);
            } else if (time - flashAt <= FLASH_SECONDS + 0.4F) {
                timing = CupelBlockEntity.ON_TIME;
                screen.show("cupel_on_time", GOOD);
            } else {
                timing = CupelBlockEntity.LATE;
                screen.show("cupel_late", BAD);
            }
            screen.sound(SoundEvents.CHAIN_PLACE, 1.0F);
            return true;
        }
        return false;
    }
}
