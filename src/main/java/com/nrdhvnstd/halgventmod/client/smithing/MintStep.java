package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Чеканка: кружок-заготовка на нижнем штемпеле, верхний штемпель ходит над ним. Зажмите ЛКМ — замах, отпустите,
 * когда штемпель точно над кружком: удар должен быть тяжёлым — иначе оттиск слабый. Мимо середины — кривой чекан.
 */
final class MintStep extends SmithStep {
    private final int coins;
    private final Swing swing = new Swing();
    private int current;
    private float offTotal;
    private int weak;
    private float speedX;
    private float speedY;
    private float phase;
    private long struckAt = -1;

    MintStep(SmithWorkScreen screen, Random random, int coins) {
        super(screen, random);
        this.coins = Math.max(1, coins);
        next();
    }

    private void next() {
        speedX = 1.1F + random.nextFloat() * 0.8F;
        speedY = 1.4F + random.nextFloat() * 0.9F;
        phase = random.nextFloat() * 6.28F;
    }

    private float[] die(long now) {
        double t = now / 1000.0;
        return new float[] {(float) Math.sin(t * speedX * 2 + phase) * 14, (float) Math.sin(t * speedY * 2 + phase * 1.7) * 10};
    }

    @Override
    boolean done() {
        return current >= coins;
    }

    @Override
    int rawScore() {
        return clampScore(100 - (current == 0 ? 0 : offTotal / current * 5) - weak * 5);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.mint_status", Math.min(current + 1, coins), coins, weak);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.mint_hint");
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int cx = x + (w - 20) / 2;
        int cy = y + h / 2 + 10;
        g.fill(cx - 16, cy + 6, cx + 16, cy + 30, IRON_DARK);
        for (int dy = -8; dy <= 8; dy++) {
            int half = (int) Math.sqrt(64 - dy * dy);
            g.fill(cx - half, cy + dy / 2, cx + half, cy + dy / 2 + 1, 0xFFD8D8E0);
        }
        float[] d = die(now);
        boolean hit = struckAt >= 0 && now - struckAt < 120;
        int dx = cx + Math.round(d[0]);
        int dy = cy - 14 + Math.round(d[1]) - (hit ? 0 : swing.lift(now));
        g.fill(dx - 8, dy - 26, dx + 8, dy, IRON_LIGHT);
        g.fill(dx - 8, dy - 2, dx + 8, dy, IRON_DARK);
        g.fill(cx - 1, y + 10, cx + 1, y + 16, MARK);
        int my = y + 13;
        g.fill(cx + Math.round(d[0]) - 1, my - 3, cx + Math.round(d[0]) + 2, my + 3,
                Math.hypot(d[0], d[1]) <= 3 ? GOOD : BAD);
        // Отчеканенное — стопкой справа
        for (int i = 0; i < Math.min(current, 20); i++) {
            g.fill(x + w - 60, y + h - 14 - i * 2, x + w - 44, y + h - 12 - i * 2, 0xFFE0D8A0);
        }
        swing.render(g, x + w - 16, y + 14, h - 32, now);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && current < coins) {
            swing.press(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !swing.pressed()) {
            return false;
        }
        long now = System.currentTimeMillis();
        float power = swing.release(now);
        float[] d = die(now);
        float off = (float) Math.hypot(d[0], d[1]);
        offTotal += off;
        if (power < Swing.HEAVY - 0.1F) {
            weak++;
            screen.show("mint_weak", WARN);
        } else {
            screen.show(off <= 3 ? "mint_clean" : "mint_off", off <= 3 ? GOOD : WARN);
        }
        struckAt = now;
        screen.sound(SoundEvents.ANVIL_PLACE, 2.0F);
        current++;
        next();
        return true;
    }
}
