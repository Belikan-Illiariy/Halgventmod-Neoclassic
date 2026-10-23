package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;
import com.nrdhvnstd.halgventmod.carpentry.WorkShape;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Выборка долотом. Заготовка сверху, карандашом обведено гнездо (клетки). Каждую клетку гнезда надо вынуть
 * на две глубины. ЛКМ — лёгкий удар киянкой (одна глубина), ПКМ — сильный (сразу две), но:
 * <ul>
 *   <li>сильный удар колет древесину вдоль волокна: соседняя по волокну (слева и справа) клетка за краем гнезда
 *       скалывается — край гнезда испорчен;</li>
 *   <li>сильный удар по тонкому дну (клетка уже вынута на одну глубину) пробивает его;</li>
 *   <li>удар мимо гнезда — задир на лице заготовки.</li>
 * </ul>
 * Середину выгоднее выбирать сильными, края — лёгкими. Удары не чаще замаха киянки.
 */
final class ChiselStep extends WorkStep {
    // Меняйте здесь
    private static final int SWING_MS = 260;            // замах киянки
    private static final int DEPTH = 2;                 // глубин на клетку гнезда

    private final WorkShape shape;
    private final int[] depth = new int[WorkShape.COLS * WorkShape.ROWS];
    private final boolean[] chipped = new boolean[WorkShape.COLS * WorkShape.ROWS];
    private final boolean[] gouged = new boolean[WorkShape.COLS * WorkShape.ROWS];
    private final boolean[] punched = new boolean[WorkShape.COLS * WorkShape.ROWS];
    private final int total;
    private int chips;
    private int gouges;
    private int punches;
    private long lastStrike;
    private boolean strong;     // как ударили последний раз — для отрисовки киянки
    // Поле, как его нарисовали в последний раз
    private int gridX;
    private int gridY;
    private int cell = 12;
    private int hoverCol = -1;
    private int hoverRow = -1;

    ChiselStep(BenchWorkScreen screen, BenchProducts.Product product, BenchProducts.Step step, Random random) {
        super(screen, product, step, random);
        this.shape = step.shape();
        for (int col = 0; col < WorkShape.COLS; col++) {
            for (int row = 0; row < WorkShape.ROWS; row++) {
                if (shape.hollow(col, row)) {
                    depth[index(col, row)] = DEPTH;
                }
            }
        }
        this.total = shape.hollowCells() * DEPTH;
    }

    private static int index(int col, int row) {
        return row * WorkShape.COLS + col;
    }

    private boolean inGrid(int col, int row) {
        return col >= 0 && row >= 0 && col < WorkShape.COLS && row < WorkShape.ROWS;
    }

    private int left() {
        int n = 0;
        for (int d : depth) {
            n += d;
        }
        return n;
    }

    private void strike(int col, int row, boolean hard) {
        long now = System.currentTimeMillis();
        if (!inGrid(col, row) || now - lastStrike < SWING_MS) {
            return;
        }
        lastStrike = now;
        strong = hard;
        int i = index(col, row);
        if (!shape.hollow(col, row)) {
            if (!gouged[i] && !chipped[i]) {
                gouged[i] = true;
                gouges++;
            }
            screen.show("chisel_gouge", BAD);
            screen.sound(SoundEvents.WOOD_HIT, 0.7F);
            return;
        }
        if (depth[i] == 0) {
            screen.sound(SoundEvents.WOOD_HIT, 1.8F);
            return;
        }
        if (hard) {
            if (depth[i] == 1 && !punched[i]) {
                punched[i] = true;
                punches++;
                screen.show("chisel_punch", BAD);
            }
            // Вдоль волокна (по X) колет соседей за краем гнезда
            for (int side = -1; side <= 1; side += 2) {
                int c = col + side;
                if (inGrid(c, row) && !shape.hollow(c, row) && !chipped[index(c, row)]) {
                    chipped[index(c, row)] = true;
                    chips++;
                    screen.show("chisel_chip", BAD);
                    screen.sound(SoundEvents.WOOD_BREAK, 1.5F);
                }
            }
            depth[i] = 0;
            screen.sound(SoundEvents.WOOD_HIT, 0.8F);
        } else {
            depth[i]--;
            screen.sound(SoundEvents.WOOD_HIT, 1.25F + random.nextFloat() * 0.2F);
        }
    }

    @Override
    boolean done() {
        return left() == 0;
    }

    @Override
    int score() {
        return clampScore(100 - chips * 10 - gouges * 8 - punches * 6);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.bench_work.chisel_status", total - left(), total, chips, punches + gouges);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.bench_work.chisel_hint");
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        cell = Math.min((w - 20) / WorkShape.COLS, (h - 18) / WorkShape.ROWS);
        gridX = x + (w - cell * WorkShape.COLS) / 2;
        gridY = y + 12 + (h - 12 - cell * WorkShape.ROWS) / 2;
        int gw = cell * WorkShape.COLS;
        int gh = cell * WorkShape.ROWS;
        g.fill(gridX - 3, gridY - 3, gridX + gw + 3, gridY + gh + 3, WOOD_DARK);
        hoverCol = mouseX < 0 ? -1 : Math.floorDiv(mouseX - gridX, cell);
        hoverRow = mouseY < 0 ? -1 : Math.floorDiv(mouseY - gridY, cell);
        for (int col = 0; col < WorkShape.COLS; col++) {
            for (int row = 0; row < WorkShape.ROWS; row++) {
                int i = index(col, row);
                int cx = gridX + col * cell;
                int cy = gridY + row * cell;
                int color;
                if (!shape.hollow(col, row)) {
                    color = row % 3 == 1 ? WOOD_SHADE : WOOD;  // волокно — полосами вдоль
                } else {
                    color = depth[i] == DEPTH ? WOOD : depth[i] == 1 ? WOOD_DARK : HOLLOW;
                }
                g.fill(cx, cy, cx + cell, cy + cell, color);
                if (shape.hollow(col, row) && depth[i] == 1) {
                    g.fill(cx + 2, cy + cell / 2, cx + cell - 2, cy + cell / 2 + 1, 0xFF6A4A28); // след долота
                }
                if (punched[i]) {
                    g.fill(cx + cell / 2 - 1, cy + cell / 2 - 1, cx + cell / 2 + 2, cy + cell / 2 + 2, 0xFF000000);
                }
                if (chipped[i]) {
                    g.fill(cx + 1, cy + 2, cx + cell - 1, cy + cell - 2, 0xFFE8C894);
                    g.fill(cx + 2, cy + cell / 2, cx + cell - 2, cy + cell / 2 + 1, BAD);
                }
                if (gouged[i]) {
                    g.fill(cx + 2, cy + 2, cx + cell - 2, cy + 3, 0xFF5A3A1A);
                    g.fill(cx + 3, cy + cell - 4, cx + cell - 3, cy + cell - 3, 0xFF5A3A1A);
                }
            }
        }
        // Разметка: карандаш по краю гнезда
        for (int col = 0; col < WorkShape.COLS; col++) {
            for (int row = 0; row < WorkShape.ROWS; row++) {
                if (!shape.hollow(col, row)) {
                    continue;
                }
                int cx = gridX + col * cell;
                int cy = gridY + row * cell;
                if (!inGrid(col - 1, row) || !shape.hollow(col - 1, row)) {
                    g.fill(cx, cy, cx + 1, cy + cell, PENCIL);
                }
                if (!inGrid(col + 1, row) || !shape.hollow(col + 1, row)) {
                    g.fill(cx + cell - 1, cy, cx + cell, cy + cell, PENCIL);
                }
                if (!inGrid(col, row - 1) || !shape.hollow(col, row - 1)) {
                    g.fill(cx, cy, cx + cell, cy + 1, PENCIL);
                }
                if (!inGrid(col, row + 1) || !shape.hollow(col, row + 1)) {
                    g.fill(cx, cy + cell - 1, cx + cell, cy + cell, PENCIL);
                }
            }
        }
        // Волокно — стрелка над заготовкой
        g.fill(gridX + gw / 2 - 20, gridY - 8, gridX + gw / 2 + 20, gridY - 7, TEXT_DIM);
        g.fill(gridX + gw / 2 + 17, gridY - 10, gridX + gw / 2 + 18, gridY - 5, TEXT_DIM);
        g.fill(gridX + gw / 2 - 18, gridY - 10, gridX + gw / 2 - 17, gridY - 5, TEXT_DIM);
        // Долото над клеткой под мышью; сразу после удара — опущено
        if (inGrid(hoverCol, hoverRow)) {
            int cx = gridX + hoverCol * cell;
            int cy = gridY + hoverRow * cell;
            g.renderOutline(cx - 1, cy - 1, cell + 2, cell + 2, shape.hollow(hoverCol, hoverRow) ? TEXT : BAD);
            boolean down = now - lastStrike < SWING_MS / 2;
            int lift = down ? 0 : 6;
            g.fill(cx + cell / 2 - 1, cy - 10 - lift, cx + cell / 2 + 1, cy + cell / 2 - lift, STEEL);
            g.fill(cx + cell / 2 - 2, cy - 18 - lift, cx + cell / 2 + 2, cy - 10 - lift, HANDLE);
            if (down) {
                int head = strong ? 6 : 4;
                g.fill(cx + cell / 2 - head, cy - 24, cx + cell / 2 + head, cy - 18, 0xFF8A5A30);
            }
        }
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        int col = (int) Math.floor((mouseX - gridX) / cell);
        int row = (int) Math.floor((mouseY - gridY) / cell);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            strike(col, row, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            return true;
        }
        return false;
    }
}
