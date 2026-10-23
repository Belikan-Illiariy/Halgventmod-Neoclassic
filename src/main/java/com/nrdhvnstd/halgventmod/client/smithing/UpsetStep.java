package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Осадка: заготовка стоит торцом вверх, удары по торцу садят её — торец раздаётся в головку (боёк, шляпка, навершие).
 * Удар левее середины клонит заготовку вправо, правее — влево: держите её прямо, поправляя ударами с другой
 * стороны. Наклонили слишком — заготовку повело (штраф). Готово, когда головка набрана. Молотобоец бьёт в середину.
 */
final class UpsetStep extends SmithStep {
    private static final float BEND_AT = 14;      // ⚠️ градусов — повело
    private static final float GROW = 0.11F;      // столько головки за тяжёлый удар по жёлтому

    private final Swing swing = new Swing();
    private float grown;
    private float lean;
    private float leanTotal;
    private int strikes;
    private int bends;
    private int axisX;
    private int topY;
    private long struckAt = -1;

    UpsetStep(SmithWorkScreen screen, Random random) {
        super(screen, random);
        lean = (random.nextFloat() - 0.5F) * 4;
    }

    @Override
    boolean hot() {
        return true;
    }

    @Override
    float minHeat() {
        return 0.65F;
    }

    @Override
    boolean helped() {
        return true;
    }

    @Override
    boolean done() {
        return grown >= 1;
    }

    @Override
    int rawScore() {
        float avgLean = strikes == 0 ? Math.abs(lean) : leanTotal / strikes;
        return clampScore(100 - avgLean * 2.6F - bends * 9 - Math.max(0, strikes - 14) * 1.0F);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.upset_status", Math.round(Math.min(1, grown) * 100),
                Math.round(lean), bends);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.upset_hint");
    }

    private void blow(float offset, float power) {
        if (!screen.strike(this, minHeat())) {
            return;
        }
        strikes++;
        float heat = screen.copper() ? 0.75F : screen.heat();
        float give = Math.max(0.2F, Math.min(1.2F, (heat - 0.4F) / 0.45F));
        grown += GROW * (0.25F + power) * give;
        float side = Math.max(-1, Math.min(1, offset));
        lean -= side * 5.5F * (0.3F + power) + (random.nextFloat() - 0.5F) * 1.2F;
        if (Math.abs(lean) > BEND_AT) {
            bends++;
            penalize(0);
            lean = Math.signum(lean) * BEND_AT * 0.5F;
            screen.show("upset_bent", BAD);
            screen.sound(SoundEvents.ANVIL_LAND, 1.5F);
        }
        leanTotal += Math.abs(lean);
        struckAt = System.currentTimeMillis();
        screen.sound(SoundEvents.ANVIL_PLACE, 1.1F + power * 0.2F);
    }

    @Override
    void helperBlow(int accuracy, double mouseX, double mouseY) {
        blow((100 - accuracy) / 100F * (random.nextBoolean() ? 1 : -1) * 0.6F, 1.1F);
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int base = y + h - 34;
        anvil(g, x + 10, base, w - 40);
        axisX = x + (w - 30) / 2;
        int length = Math.round(70 - Math.min(1, grown) * 18);
        int color = glow(screen.heat());
        double rad = Math.toRadians(lean);
        int half = 5;
        for (int s = 0; s < length; s++) {
            int px = axisX + (int) Math.round(Math.sin(rad) * s);
            int py = base - s;
            float headShare = Math.max(0, (s - length + 14) / 14F);
            int wHalf = half + Math.round(headShare * Math.min(1, grown) * 9);
            g.fill(px - wHalf, py - 1, px + wHalf, py, color);
        }
        int tipX = axisX + (int) Math.round(Math.sin(rad) * length);
        topY = base - length;
        // Целевая головка — рамка
        g.renderOutline(tipX - half - 9, topY - 1, (half + 9) * 2, 14, MARK);
        boolean hit = struckAt >= 0 && System.currentTimeMillis() - struckAt < 120;
        int hx = mouseX > 0 ? mouseX : tipX;
        hammer(g, hit ? hx : hx, topY, hit ? 0 : swing.lift(now));
        if (mouseX > 0) {
            g.fill(mouseX, topY - 3, mouseX + 1, topY, WARN);
        }
        // Уровень наклона
        int ly = y + 12;
        g.fill(axisX - 40, ly, axisX + 40, ly + 3, TRACK);
        g.fill(axisX - 6, ly, axisX + 6, ly + 3, ZONE);
        int mx = axisX + Math.round(lean / BEND_AT * 40);
        g.fill(mx - 1, ly - 2, mx + 2, ly + 5, Math.abs(lean) > BEND_AT * 0.6F ? BAD : TEXT);
        swing.render(g, x + w - 16, y + 14, h - 32, now);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            swing.press(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && swing.pressed()) {
            float power = swing.release(System.currentTimeMillis());
            double tipX = axisX + Math.sin(Math.toRadians(lean)) * (70 - Math.min(1, grown) * 18);
            blow((float) ((mouseX - tipX) / 10.0), power);
            return true;
        }
        return false;
    }
}
