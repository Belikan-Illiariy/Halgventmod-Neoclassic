package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Обжим крицы: губчатый ком, в нём бурые пятна шлака. Бейте по пятнам (зажать ЛКМ — замах, отпустить — удар):
 * крепкий удар выбивает шлак шире. Крица рыхлая — по холодной (ниже жёлтого) края крошатся и металл теряется.
 * Готово — когда шлака почти не осталось (или Enter). Молотобоец бьёт туда, где курсор.
 */
final class ConsolidateStep extends SmithStep {
    private static final int COLS = 16;
    private static final int ROWS = 8;
    private static final float DONE_LEFT = 0.06F;   // ⚠️ доля шлака, при которой крица чиста

    private final boolean[] metal = new boolean[COLS * ROWS];
    private final float[] slag = new float[COLS * ROWS];
    private final Swing swing = new Swing();
    private final float initial;
    private int crumbled;
    private int strikes;
    private boolean finished;
    private int gx;
    private int gy;
    private int cell = 9;
    private long struckAt = -1;
    private int struckIndex = -1;

    ConsolidateStep(SmithWorkScreen screen, Random random) {
        super(screen, random);
        float total = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float dx = (c + 0.5F - COLS / 2F) / (COLS / 2F);
                float dy = (r + 0.5F - ROWS / 2F) / (ROWS / 2F);
                float wobble = 0.85F + random.nextFloat() * 0.25F;
                int i = r * COLS + c;
                metal[i] = dx * dx + dy * dy <= wobble;
                if (metal[i] && random.nextFloat() < 0.38F) {
                    slag[i] = 0.4F + random.nextFloat() * 0.6F;
                    total += slag[i];
                }
            }
        }
        initial = Math.max(0.01F, total);
    }

    @Override
    boolean hot() {
        return true;
    }

    @Override
    float minHeat() {
        return 0.75F;
    }

    @Override
    boolean helped() {
        return true;
    }

    private float left() {
        float total = 0;
        for (int i = 0; i < slag.length; i++) {
            if (metal[i]) {
                total += slag[i];
            }
        }
        return total / initial;
    }

    @Override
    boolean done() {
        return finished || left() <= DONE_LEFT;
    }

    @Override
    void finishEarly() {
        finished = true;
    }

    @Override
    int rawScore() {
        return clampScore(100 - Math.max(0, left() - DONE_LEFT) * 160 - crumbled * 3 - Math.max(0, strikes - 30) * 0.5);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.consolidate_status", Math.round(left() * 100),
                crumbled, strikes);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.consolidate_hint");
    }

    private int indexAt(double mouseX, double mouseY) {
        int c = (int) Math.floor((mouseX - gx) / cell);
        int r = (int) Math.floor((mouseY - gy) / cell);
        if (c < 0 || r < 0 || c >= COLS || r >= ROWS) {
            return -1;
        }
        return r * COLS + c;
    }

    private boolean edge(int i) {
        int c = i % COLS;
        int r = i / COLS;
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int nc = c + d[0];
            int nr = r + d[1];
            if (nc < 0 || nr < 0 || nc >= COLS || nr >= ROWS || !metal[nr * COLS + nc]) {
                return true;
            }
        }
        return false;
    }

    private void blow(int index, float power) {
        if (index < 0 || !metal[index] || !screen.strike(this, minHeat())) {
            return;
        }
        strikes++;
        float heat = screen.heat();
        float give = Math.max(0.2F, Math.min(1.2F, (heat - 0.45F) / 0.4F));
        float radius = 1.0F + power * 1.6F;
        int c0 = index % COLS;
        int r0 = index / COLS;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int i = r * COLS + c;
                float d = (float) Math.hypot(c - c0, r - r0);
                if (!metal[i] || d > radius) {
                    continue;
                }
                float k = 1 - d / (radius + 0.5F);
                slag[i] = Math.max(0, slag[i] - (0.3F + power * 0.6F) * k * give);
                // Холодная рыхлая крица крошится по краям
                if (heat < minHeat() && edge(i) && d <= 1 && random.nextFloat() < (minHeat() - heat) * 3) {
                    metal[i] = false;
                    slag[i] = 0;
                    crumbled++;
                }
            }
        }
        struckAt = System.currentTimeMillis();
        struckIndex = index;
        screen.sound(SoundEvents.ANVIL_PLACE, 1.0F + power * 0.2F);
        screen.sound(SoundEvents.LAVA_POP, 1.2F);
    }

    @Override
    void helperBlow(int accuracy, double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        if (index < 0) {
            screen.show("striker_aim", TEXT_DIM);
            return;
        }
        blow(index, 0.9F + accuracy / 250F);
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        cell = Math.min((w - 70) / COLS, (h - 50) / ROWS);
        gx = x + (w - 30 - COLS * cell) / 2;
        gy = y + 16;
        anvil(g, x + 10, gy + ROWS * cell + 2, w - 40);
        int color = glow(screen.heat());
        int hover = indexAt(mouseX, mouseY);
        for (int i = 0; i < metal.length; i++) {
            if (!metal[i]) {
                continue;
            }
            int cx = gx + (i % COLS) * cell;
            int cy = gy + (i / COLS) * cell;
            int c = mix(color, SLAG, Math.min(1, slag[i] * 1.2F));
            g.fill(cx, cy, cx + cell, cy + cell, i == hover ? mix(c, 0xFFFFFFFF, 0.15F) : c);
            if (slag[i] > 0.15F) {
                int s = Math.max(1, Math.round(cell * 0.3F * slag[i]));
                g.fill(cx + cell / 2 - s, cy + cell / 2 - s, cx + cell / 2 + s, cy + cell / 2 + s, shade(SLAG, 0.6F));
            }
        }
        boolean hit = struckAt >= 0 && System.currentTimeMillis() - struckAt < 150;
        if (hit && struckIndex >= 0) {
            int cx = gx + (struckIndex % COLS) * cell + cell / 2;
            int cy = gy + (struckIndex / COLS) * cell + cell / 2;
            for (int k = 0; k < 6; k++) {
                int sx = cx + random.nextInt(cell * 3) - cell * 3 / 2;
                int sy = cy + random.nextInt(cell * 2) - cell;
                g.fill(sx, sy, sx + 1, sy + 1, SPARK);
            }
            hammer(g, cx, cy - 2, 0);
        } else if (hover >= 0) {
            hammer(g, mouseX, gy + (hover / COLS) * cell, swing.lift(now));
        }
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
            blow(indexAt(mouseX, mouseY), swing.release(System.currentTimeMillis()));
            return true;
        }
        return false;
    }
}
