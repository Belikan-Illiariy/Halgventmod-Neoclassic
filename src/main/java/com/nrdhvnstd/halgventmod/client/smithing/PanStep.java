package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Промывка лотком. В лотке песок (лёгкий, серый), чёрный шлих (тяжёлый, тёмный), крупинки золота (тяжелее всего),
 * камни и комья глины.
 * <ul>
 *   <li>Пробел — зачерпнуть воды: без воды песок не расслаивается и не смывается, вода убывает при работе;</li>
 *   <li>водите мышью по кругу — вода закручивается: лёгкое уходит к краю, тяжёлое оседает к середине; слишком быстро —
 *       плещет через край вместе с золотом (полоса вверху);</li>
 *   <li>ЛКМ (держать) — наклонить и слить то, что у нижнего края;</li>
 *   <li>ПКМ — выкинуть камень (мешает расслаиваться), размять ком глины (в нём песок и золото — три нажатия),
 *       взять самородок (бывает изредка — сверху ещё золотого песка).</li>
 * </ul>
 * С ртутью (желоб) золото липнет к ней и почти не уходит. Готово, когда песка почти не осталось и комья размяты
 * (или Enter).
 */
final class PanStep extends SmithStep {
    private static final float RIM = 0.82F;
    private static final float SWIRL_LOW = 0.45F;    // ⚠️ скорость кружения: зелёная зона
    private static final float SWIRL_HIGH = 1.25F;
    private static final float SLOSH = 1.6F;         // выше — плещет через край
    private static final int SAND = 0, BLACK = 1, GOLD = 2;
    private static final int SAND_COLOR = 0xFFAAA290;
    private static final int BLACK_COLOR = 0xFF2C2C34;
    private static final int GOLD_COLOR = 0xFFF0C840;
    private static final int STONE = 0xFF7C7C82;
    private static final int CLAY = 0xFF8A6A48;

    private final List<float[]> grains = new ArrayList<>();   // r, angle, вид
    private final List<float[]> stones = new ArrayList<>();   // r, angle
    private final List<float[]> clods = new ArrayList<>();    // r, angle, нажатий, золота внутри, песка внутри
    private final boolean mercury;
    private float[] nugget;                                    // r, angle, в коме (1) или на виду (0); null — нет
    private boolean nuggetTaken;
    private final float sandTotal;
    private final int goldTotal;
    private float swirl;
    private float water = 1;
    private boolean tilting;
    private boolean finished;
    private float lostCooldown;
    private double lastAngle = Double.NaN;
    private int cx;
    private int cy;
    private int inner = 40;

    PanStep(SmithWorkScreen screen, Random random, int amount, boolean mercury, boolean nugget) {
        super(screen, random);
        this.mercury = mercury;
        int sandCount = 60 + amount * 10;
        int blackCount = 10 + amount * 3;
        int goldCount = 6 + amount * 2 + (mercury ? 4 : 0);
        int clodCount = 1 + random.nextInt(2) + amount / 2;
        int stoneCount = 2 + random.nextInt(3);
        for (int i = 0; i < sandCount; i++) {
            grains.add(spot(SAND, 0.8F));
        }
        for (int i = 0; i < blackCount; i++) {
            grains.add(spot(BLACK, 0.8F));
        }
        // Часть золота — в комьях глины
        int inClods = Math.min(goldCount / 2, clodCount * 2);
        for (int i = 0; i < goldCount - inClods; i++) {
            grains.add(spot(GOLD, 0.8F));
        }
        for (int i = 0; i < clodCount; i++) {
            float[] at = spot(0, 0.65F);
            clods.add(new float[] {at[0], at[1], 0, inClods / clodCount + (i < inClods % clodCount ? 1 : 0), 6});
        }
        for (int i = 0; i < stoneCount; i++) {
            float[] at = spot(0, 0.7F);
            stones.add(new float[] {at[0], at[1]});
        }
        if (nugget) {
            float[] at = spot(0, 0.6F);
            this.nugget = new float[] {at[0], at[1], clods.isEmpty() ? 0 : 1};
            if (!clods.isEmpty()) {
                this.nugget[0] = clods.get(0)[0];
                this.nugget[1] = clods.get(0)[1];
            }
        }
        this.sandTotal = sandCount + blackCount * 0.5F + clodCount * 6;
        this.goldTotal = goldCount;
    }

    private float[] spot(int kind, float spread) {
        return new float[] {(float) Math.sqrt(random.nextFloat()) * spread, random.nextFloat() * 6.28F, kind};
    }

    @Override
    boolean done() {
        return finished || clods.isEmpty() && count(SAND) <= sandTotal * 0.06F;
    }

    @Override
    void finishEarly() {
        finished = true;
    }

    private int count(int kind) {
        int n = 0;
        for (float[] gr : grains) {
            if (gr[2] == kind) {
                n++;
            }
        }
        return n;
    }

    /** Золото, что осталось в лотке (в неразмятом коме — не в счёт: его выбросят с комом). */
    private int kept() {
        return Math.round(100F * count(GOLD) / goldTotal);
    }

    private int washed() {
        float left = count(SAND) + count(BLACK) * 0.5F;
        for (float[] clod : clods) {
            left += clod[4];
        }
        return Math.round(100F * Math.max(0, sandTotal - left) / sandTotal);
    }

    @Override
    int rawScore() {
        return clampScore(kept() * 0.6F + washed() * 0.4F);
    }

    @Override
    List<Integer> result() {
        return List.of(clampScore(kept() - penalty), washed(), nuggetTaken ? 1 : 0);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.pan_status", washed(), kept(), Math.round(water * 100));
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.pan_hint");
    }

    @Override
    void tick(float dt, long now) {
        swirl = Math.max(0, swirl - dt * 1.4F);
        lostCooldown = Math.max(0, lostCooldown - dt);
        float wet = Math.min(1, water / 0.3F);           // мало воды — песок стоит
        float s = Math.min(SWIRL_HIGH, swirl) * wet * (stones.isEmpty() ? 1 : 0.5F);
        for (float[] gr : grains) {
            float drift = switch ((int) gr[2]) {
                case SAND -> 0.14F;
                case BLACK -> 0.015F;
                default -> mercury ? -0.14F : -0.07F;
            };
            gr[0] = Math.max(0, Math.min(0.98F, gr[0] + (drift * s - (gr[2] == GOLD ? 0.01F : 0)) * dt
                    + (random.nextFloat() - 0.5F) * 0.015F * wet));
            gr[1] += swirl * wet * (gr[2] == SAND ? 2.6F : 1.8F) * dt;
        }
        for (float[] stone : stones) {
            stone[1] += swirl * wet * 1.2F * dt;
        }
        for (float[] clod : clods) {
            clod[1] += swirl * wet * 1.0F * dt;
        }
        if (nugget != null && !nuggetTaken && nugget[2] == 0) {
            nugget[1] += swirl * wet * 1.0F * dt;
        }
        water = Math.max(0, water - dt * (0.03F * swirl + (tilting ? 0.3F : 0)));
        // Слишком быстро — плещет через край что попало
        if (swirl > SLOSH && water > 0.2F) {
            grains.removeIf(gr -> gr[0] > 0.75F && random.nextFloat() < dt * (gr[2] == GOLD && mercury ? 0.5F : 2.0F)
                    && lost(gr));
            water = Math.max(0, water - dt * 0.2F);
        }
        if (tilting && water > 0.05F) {
            grains.removeIf(gr -> {
                double down = Math.abs(Math.sin(gr[1]) - 1); // 0 — у нижнего края
                float chance = gr[2] == GOLD && mercury ? 0.04F : 0.15F;
                return gr[0] > RIM && down < 0.6 && random.nextFloat() < chance && lost(gr);
            });
            // Неразмятый ком у края — уходит целиком (с золотом внутри)
            clods.removeIf(clod -> clod[0] > 0.7F && Math.abs(Math.sin(clod[1]) - 1) < 0.4 && random.nextFloat() < dt * 0.6F
                    && clodLost(clod));
        }
    }

    /** Ушло за край: золото — жаль. Возвращает true, чтобы убрать из лотка. */
    private boolean lost(float[] grain) {
        if (grain[2] == GOLD && lostCooldown <= 0) {
            screen.show("pan_lost", BAD);
            lostCooldown = 0.8F;
        }
        return true;
    }

    private boolean clodLost(float[] clod) {
        if (clod[3] > 0) {
            screen.show("pan_clod_lost", BAD);
        }
        if (nugget != null && nugget[2] == 1 && clods.indexOf(clod) == 0) {
            nugget = null;
        }
        return true;
    }

    private int px(float r, float angle) {
        return cx + (int) Math.round(Math.cos(angle) * r * inner);
    }

    private int py(float r, float angle) {
        return cy + (int) Math.round(Math.sin(angle) * r * inner);
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        int radius = Math.min(w - 50, h) / 2 - 8;
        cx = x + (w - 30) / 2;
        cy = y + h / 2 + 4 + (tilting ? 4 : 0);
        inner = (int) (radius * 0.93F);
        // Лоток: деревянный край, внутри — вода поверх песка
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.sqrt(radius * radius - dy * dy);
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, dy > radius * 0.75F && tilting ? 0xFF3A6A8A : 0xFF6E4A2A);
        }
        int bottom = mix(0xFF8A7A5E, 0xFF3E6A7A, Math.min(1, water * 1.2F));
        for (int dy = -inner; dy <= inner; dy++) {
            int half = (int) Math.sqrt(inner * inner - dy * dy);
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, bottom);
        }
        // Слив через нижний край
        if (tilting && water > 0.05F) {
            g.fill(cx - 10, cy + radius, cx + 10, y + h, 0x803E7A9A);
        }
        for (float[] gr : grains) {
            int color = gr[2] == SAND ? SAND_COLOR : gr[2] == BLACK ? BLACK_COLOR : mercury ? 0xFFE8D890 : GOLD_COLOR;
            int gx = px(gr[0], gr[1]);
            int gy = py(gr[0], gr[1]);
            g.fill(gx, gy, gx + 2, gy + 2, color);
        }
        for (float[] stone : stones) {
            int sx = px(stone[0], stone[1]);
            int sy = py(stone[0], stone[1]);
            g.fill(sx - 4, sy - 3, sx + 4, sy + 3, STONE);
            g.fill(sx - 3, sy - 3, sx + 2, sy - 2, 0xFFA0A0A6);
        }
        for (float[] clod : clods) {
            int kx = px(clod[0], clod[1]);
            int ky = py(clod[0], clod[1]);
            g.fill(kx - 5, ky - 4, kx + 5, ky + 4, CLAY);
            g.fill(kx - 4, ky - 4, kx + 3, ky - 3, 0xFFA08060);
            for (int i = 0; i < (int) clod[2]; i++) {
                g.fill(kx - 3 + i * 3, ky - 2, kx - 2 + i * 3, ky + 3, 0xFF5A3E26); // трещины
            }
        }
        if (nugget != null && !nuggetTaken && nugget[2] == 0) {
            int nx = px(nugget[0], nugget[1]);
            int ny = py(nugget[0], nugget[1]);
            g.fill(nx - 2, ny - 2, nx + 3, ny + 2, 0xFFFFD54A);
            if ((now / 300) % 2 == 0) {
                g.fill(nx + 2, ny - 4, nx + 3, ny - 3, SPARK);
            }
        }
        // Полосы: кружение (вверху) и вода (справа)
        bar(g, x + 14, y + 8, w - 60, 4, Math.min(1, swirl / 2), SWIRL_LOW / 2, SWIRL_HIGH / 2,
                swirl > SLOSH ? BAD : TEXT);
        gauge(g, x + w - 18, y + 14, 6, h - 30, water, 0.3F, 1.0F, water < 0.15F ? BAD : WATER);
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        double angle = Math.atan2(mouseY - cy, mouseX - cx);
        double distance = Math.hypot(mouseX - cx, mouseY - cy);
        if (!Double.isNaN(lastAngle) && distance > inner * 0.3) {
            double d = angle - lastAngle;
            while (d > Math.PI) {
                d -= Math.PI * 2;
            }
            while (d < -Math.PI) {
                d += Math.PI * 2;
            }
            swirl = Math.min(2.2F, swirl + (float) Math.abs(d) * 0.6F);
        }
        lastAngle = angle;
    }

    @Override
    boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            if (water < 0.95F) {
                water = 1;
                screen.sound(SoundEvents.BUCKET_FILL, 1.2F);
            }
            return true;
        }
        return false;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            tilting = true;
            screen.sound(SoundEvents.GENERIC_SPLASH, 1.4F);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        // Самородок на виду — взять
        if (nugget != null && !nuggetTaken && nugget[2] == 0
                && Math.hypot(mouseX - px(nugget[0], nugget[1]), mouseY - py(nugget[0], nugget[1])) <= 5) {
            nuggetTaken = true;
            screen.show("pan_nugget", GOOD);
            screen.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F);
            return true;
        }
        for (int i = 0; i < stones.size(); i++) {
            float[] stone = stones.get(i);
            if (Math.hypot(mouseX - px(stone[0], stone[1]), mouseY - py(stone[0], stone[1])) <= 6) {
                stones.remove(i);
                screen.sound(SoundEvents.STONE_HIT, 1.3F);
                return true;
            }
        }
        for (int i = 0; i < clods.size(); i++) {
            float[] clod = clods.get(i);
            if (Math.hypot(mouseX - px(clod[0], clod[1]), mouseY - py(clod[0], clod[1])) <= 7) {
                clod[2]++;
                screen.sound(SoundEvents.MUD_STEP, 1.0F + clod[2] * 0.15F);
                if (clod[2] >= 3) {
                    breakClod(i);
                }
                return true;
            }
        }
        return true;
    }

    /** Ком размят: песок и золото — в лоток, самородок — на виду. */
    private void breakClod(int index) {
        float[] clod = clods.remove(index);
        for (int i = 0; i < (int) clod[4]; i++) {
            grains.add(new float[] {clod[0] + (random.nextFloat() - 0.5F) * 0.1F, clod[1] + (random.nextFloat() - 0.5F) * 0.3F, SAND});
        }
        for (int i = 0; i < (int) clod[3]; i++) {
            grains.add(new float[] {clod[0] + (random.nextFloat() - 0.5F) * 0.08F, clod[1] + (random.nextFloat() - 0.5F) * 0.2F, GOLD});
        }
        if (nugget != null && nugget[2] == 1 && index == 0) {
            nugget[0] = clod[0];
            nugget[1] = clod[1];
            nugget[2] = 0;
        }
        screen.show("pan_clod", GOOD);
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            tilting = false;
            return true;
        }
        return false;
    }
}
