package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.Locale;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Строгание. Заготовка видна сбоку: верх в буграх, карандашом проведена разметка — до неё надо выстрогать ровно.
 * Рубанок лежит подошвой на самых высоких местах под собой и снимает стружку на толщину ножа ниже них, поэтому
 * сначала срезает бугры, а ямы перекрывает. Зажмите ЛКМ и ведите по волокну (стрелка): против волокна — задиры,
 * быстро по сучку — его вырывает, быстро вообще — рубанок дробит. Колесо — толщина стружки; ниже разметки —
 * перестрогано. Всё до разметки — этап закончен, Enter — раньше.
 */
final class PlaneStep extends WorkStep {
    // Меняйте здесь
    private static final float[] DEPTHS = {0.2F, 0.5F, 1.0F, 1.6F};  // толщина стружки, единиц высоты
    private static final int SOLE = 9;                  // длина подошвы, столбцов
    private static final float FAST = 0.5F;             // пикселей в мс: быстрее — сучки рвёт, рубанок дробит
    private static final float TEAR_AGAINST = 0.4F;     // шанс задира на столбец против волокна
    private static final float TEAR_DEPTH = 1.2F;
    private static final float CHATTER = 0.5F;
    private static final float OVER = 0.4F;             // глубже разметки на столько — перестрогано
    private static final float FLAT = 0.3F;             // всё не выше этого над разметкой — готово
    private static final int SCALE = 3;                 // пикселей окна на единицу высоты

    private final int columns;
    private final float[] surface;
    private final boolean[] knot;
    private final boolean[] torn;
    private final int grain;            // куда идёт волокно: 1 — вправо, −1 — влево
    private int depthIndex = 2;
    private int tears;
    private float planeX = -1;          // где нож, в столбцах
    private int lastColumn = Integer.MIN_VALUE; // последний строганый столбец — не строгать его дважды за ход
    private boolean pressed;
    private boolean finished;
    private long lastMoveAt;
    private long lastShaving;
    // Поле, как его нарисовали в последний раз: нужно, чтобы перевести мышь в столбцы
    private int boardLeft;
    private int columnWidth = 6;

    PlaneStep(BenchWorkScreen screen, BenchProducts.Product product, BenchProducts.Step step, Random random) {
        super(screen, product, step, random);
        this.columns = Math.min(56, 40 + 8 * (product.planks() - 1));
        this.surface = new float[columns];
        this.knot = new boolean[columns];
        this.torn = new boolean[columns];
        this.grain = random.nextBoolean() ? 1 : -1;
        double f1 = 0.12 + random.nextDouble() * 0.1, p1 = random.nextDouble() * 6.3;
        double f2 = 0.3 + random.nextDouble() * 0.2, p2 = random.nextDouble() * 6.3;
        for (int i = 0; i < columns; i++) {
            surface[i] = (float) Math.max(1.0, 3.2 + 1.6 * Math.sin(i * f1 + p1) + 0.9 * Math.sin(i * f2 + p2)
                    + random.nextDouble() * 0.6);
        }
        for (int k = 0; k < product.knots(); k++) {
            int at = 3 + random.nextInt(Math.max(1, columns - 8));
            for (int i = at; i < Math.min(columns, at + 3); i++) {
                knot[i] = true;
                surface[i] += 0.6F; // сучок выпирает
            }
        }
    }

    private float depth() {
        return DEPTHS[depthIndex];
    }

    /** Где лежит подошва, если нож над столбцом column: на самом высоком месте под ней. */
    private float rest(int column) {
        float top = -100;
        for (int j = column - SOLE / 2; j <= column + SOLE / 2; j++) {
            if (j >= 0 && j < columns) {
                top = Math.max(top, surface[j]);
            }
        }
        return top;
    }

    private void shave(int column, int dir, float speed) {
        float level = rest(column) - depth();
        if (surface[column] <= level) {
            return;
        }
        surface[column] = level;
        boolean fast = speed > FAST;
        if (dir != grain && random.nextFloat() < TEAR_AGAINST) {
            tear(column, "plane_against");
        } else if (fast && knot[column]) {
            tear(column, "plane_knot");
        } else if (fast) {
            surface[column] += (random.nextFloat() - 0.5F) * CHATTER;
        }
        long now = System.currentTimeMillis();
        if (now - lastShaving > 90) {
            lastShaving = now;
            screen.sound(SoundEvents.BRUSH_GENERIC, 1.4F + random.nextFloat() * 0.3F - (fast ? 0.3F : 0));
        }
    }

    private void tear(int column, String why) {
        surface[column] -= TEAR_DEPTH;
        torn[column] = true;
        tears++;
        screen.show(why, BAD);
        screen.sound(SoundEvents.WOOD_BREAK, 1.6F);
    }

    private void move(double mouseX) {
        long now = System.currentTimeMillis();
        float target = Mth.clamp((float) ((mouseX - boardLeft) / columnWidth), 0, columns - 0.001F);
        if (planeX < 0) {
            planeX = target;
            lastMoveAt = now;
            return;
        }
        float from = planeX;
        planeX = target;
        if (!pressed || from == target) {
            lastMoveAt = now;
            return;
        }
        float speed = (float) (Math.abs(target - from) * columnWidth / Math.max(1, now - lastMoveAt));
        lastMoveAt = now;
        int dir = target > from ? 1 : -1;
        for (int c = (int) from; dir > 0 ? c <= (int) target : c >= (int) target; c += dir) {
            if (c != lastColumn) {
                shave(c, dir, speed);
            }
        }
        lastColumn = (int) target;
    }

    private int overcut() {
        int n = 0;
        for (float h : surface) {
            if (h < -OVER) {
                n++;
            }
        }
        return n;
    }

    /** Неровность: средний отход от разметки. */
    private float roughness() {
        float sum = 0;
        for (float h : surface) {
            sum += Math.abs(h);
        }
        return sum / columns;
    }

    private boolean flat() {
        for (float h : surface) {
            if (h > FLAT) {
                return false;
            }
        }
        return true;
    }

    @Override
    boolean done() {
        return finished || flat() && !pressed;
    }

    @Override
    void finishEarly() {
        finished = true;
    }

    @Override
    int score() {
        return clampScore(100 - roughness() * 35 - tears * 9 - overcut() * 6);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.bench_work.plane_status",
                String.format(Locale.ROOT, "%.1f", roughness()), tears, overcut());
    }

    @Override
    Component hint() {
        return Component.translatable(grain > 0 ? "gui.halgventmod.bench_work.plane_hint_right"
                : "gui.halgventmod.bench_work.plane_hint_left",
                Component.translatable("gui.halgventmod.bench_work.depth_" + depthIndex));
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        columnWidth = Math.max(3, (w - 20) / columns);
        boardLeft = x + (w - columns * columnWidth) / 2;
        int bx = boardLeft;
        int line = y + h * 3 / 5;
        int bottom = line + 26;
        g.fill(bx - 12, bottom, bx + columns * columnWidth + 12, bottom + 8, BENCH);
        for (int i = 0; i < columns; i++) {
            int cx = bx + i * columnWidth;
            int top = line - Math.round(surface[i] * SCALE);
            int body = knot[i] ? KNOT : (i / 2) % 3 == 0 ? WOOD_SHADE : WOOD;
            g.fill(cx, top, cx + columnWidth, bottom, body);
            g.fill(cx, top, cx + columnWidth, top + 1, torn[i] ? 0xFFA0503A : WOOD_TOP);
            if (surface[i] < -OVER) {
                g.fill(cx, top, cx + columnWidth, line, 0x80E0604A);
            }
        }
        // Волокно: стрелки по боку заготовки
        for (int ax = bx + 12; ax < bx + columns * columnWidth - 12; ax += 40) {
            int ay = line + 14;
            g.fill(ax - 7, ay, ax + 7, ay + 1, WOOD_DARK);
            int tip = grain > 0 ? ax + 7 : ax - 7;
            g.fill(tip - grain * 2, ay - 2, tip - grain * 2 + 1, ay + 3, WOOD_DARK);
        }
        for (int lx = bx - 6; lx < bx + columns * columnWidth + 6; lx += 4) {
            g.fill(lx, line, lx + 2, line + 1, PENCIL);
        }
        // Рубанок: колодка на подошве, нож посередине, ручка сзади
        if (planeX >= 0) {
            int column = Math.min(columns - 1, (int) planeX);
            int soleY = line - Math.round(rest(column) * SCALE);
            int px = bx + Math.round(planeX * columnWidth);
            int half = SOLE * columnWidth / 2;
            g.fill(px - half, soleY - 10, px + half, soleY, pressed ? 0xFF8A5A30 : 0xFF4E3018);
            g.fill(px - half, soleY - 1, px + half, soleY, 0xFF4E3018);
            g.fill(px - 1, soleY - 14, px + 1, soleY + Math.max(1, Math.round(depth() * SCALE)), STEEL);
            int back = px - grain * (half - 3);
            g.fill(back - 2, soleY - 18, back + 2, soleY - 10, 0xFF4E3018);
            g.fill(px + grain * (half - 6) - 2, soleY - 14, px + grain * (half - 6) + 2, soleY - 10, 0xFF4E3018);
        }
        // Толщина стружки — справа
        gauge(g, x + w - 8, y + 8, 5, 40, (depthIndex + 0.5F) / DEPTHS.length, 0, 0.5F, TEXT);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            planeX = -1;
            lastColumn = Integer.MIN_VALUE;
            move(mouseX);
            pressed = true;
            return true;
        }
        return false;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = false;
            return true;
        }
        return false;
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        move(mouseX);
    }

    @Override
    boolean mouseScrolled(double scrollY) {
        int next = Mth.clamp(depthIndex + (scrollY > 0 ? 1 : -1), 0, DEPTHS.length - 1);
        if (next != depthIndex) {
            depthIndex = next;
            screen.sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F);
        }
        return true;
    }
}
