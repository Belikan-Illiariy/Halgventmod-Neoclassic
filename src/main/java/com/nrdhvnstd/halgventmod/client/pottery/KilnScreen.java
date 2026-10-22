package com.nrdhvnstd.halgventmod.client.pottery;

import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.menu.KilnMenu;
import com.nrdhvnstd.halgventmod.pottery.KilnBlockEntity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Окно горна ({@link KilnMenu}). Жаром правят тягой: шесть ступеней, у каждой своя температура — щёлкните ступень,
 * горн потянется к ней, пока есть дрова.
 * <pre>
 *   [график жара за последние минуты: зоны закала, обжига, пережога; линия — к чему тянет]   [ступени тяги]
 *   [план: 1 закал → 2 обжиг → 3 остыть, у каждого шага полоса и что делать]                   [дрова: полоса, ячейка]
 *   [изделия 5×2]   [Разжечь / Затушить]   [этап обжига строкой]
 * </pre>
 */
public class KilnScreen extends StationScreen<KilnMenu> {
    // Раскладка
    private static final int GRAPH_X = 8, GRAPH_Y = 18, GRAPH_W = 152, GRAPH_H = 60;
    private static final int DRAFT_X = 168, DRAFT_Y = 18, DRAFT_W = 80, DRAFT_ROW = 10;
    private static final int PLAN_X = 8, PLAN_Y = 86, PLAN_W = 152;
    private static final float MAX_HEAT = 100;
    private static final int SAMPLE_MS = 1000;

    private final float[] history = new float[GRAPH_W];
    private int samples;
    private long lastSample;
    private Button fireButton;

    public KilnScreen(KilnMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, KilnMenu.WIDTH, KilnMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        fireButton = addRenderableWidget(Button.builder(Component.empty(), button -> click(lit()
                ? KilnMenu.BUTTON_EXTINGUISH : KilnMenu.BUTTON_IGNITE)).bounds(leftPos + 104, topPos + 124, 70, 16).build());
        containerTick();
    }

    private boolean lit() {
        return menu.get(KilnBlockEntity.D_LIT) != 0;
    }

    private int draft() {
        return menu.get(KilnBlockEntity.D_DRAFT);
    }

    private float heat() {
        return menu.get(KilnBlockEntity.D_HEAT) / 10.0F;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        fireButton.setMessage(Component.translatable(lit() ? "gui.halgventmod.hearth.extinguish" : "gui.halgventmod.hearth.ignite"));
        fireButton.active = menu.get(KilnBlockEntity.D_BROKEN) == 0;
        long now = System.currentTimeMillis();
        if (now - lastSample >= SAMPLE_MS) {
            lastSample = now;
            System.arraycopy(history, 1, history, 0, history.length - 1);
            history[history.length - 1] = heat();
            samples = Math.min(history.length, samples + 1);
        }
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    protected void panels(GuiGraphics g) {
        section(g, 4, 4, 164, 82);
        plaque(g, Component.translatable("gui.halgventmod.kiln.graph"), 40, 6, 128);
        section(g, 166, 4, 252, 82);
        plaque(g, Component.translatable("gui.halgventmod.kiln.draft"), 170, 6, 248);
        section(g, 4, 84, 164, 112);
        section(g, 166, 84, 252, 124);
        section(g, 4, 114, 100, 165);
        section(g, 102, 142, 252, 166);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        graph(g);
        draftPanel(g, mouseX, mouseY);
        plan(g);
        fuel(g);
        int x = leftPos;
        int y = topPos;
        int count = menu.get(KilnBlockEntity.D_COUNT);
        small(g, Component.translatable("gui.halgventmod.kiln.inside", count, menu.get(KilnBlockEntity.D_CAPACITY)),
                x + 8, y + 116, TEXT_DIM);
        Component stage = KilnBlockEntity.stage(heat(), menu.get(KilnBlockEntity.D_FUEL), lit(), draft(),
                menu.get(KilnBlockEntity.D_BROKEN) != 0, menu.get(KilnBlockEntity.D_REPAIR), count,
                menu.get(KilnBlockEntity.D_CAPACITY), menu.get(KilnBlockEntity.D_USES), menu.get(KilnBlockEntity.D_ELAPSED),
                menu.get(KilnBlockEntity.D_SOAK));
        smallWrap(g, stage, x + 104, y + 143, 144, y + 166, TEXT);
    }

    private int heatY(float heat) {
        return topPos + GRAPH_Y + GRAPH_H - Math.round(GRAPH_H * Math.max(0, Math.min(1, heat / MAX_HEAT)));
    }

    /** График жара: зоны, история, линия цели и нынешний жар. */
    private void graph(GuiGraphics g) {
        int x0 = leftPos + GRAPH_X;
        int x1 = x0 + GRAPH_W;
        g.fill(x0 - 1, topPos + GRAPH_Y - 1, x1 + 1, topPos + GRAPH_Y + GRAPH_H + 1, FRAME);
        g.fill(x0, topPos + GRAPH_Y, x1, topPos + GRAPH_Y + GRAPH_H, TRACK);
        // Зоны: обжиг — зелёная, пережог — красная, потолок закала — белая черта
        g.fill(x0, heatY(KilnBlockEntity.ZONE_HIGH), x1, heatY(KilnBlockEntity.ZONE_LOW), 0x403E9A2E);
        g.fill(x0, heatY(MAX_HEAT), x1, heatY(KilnBlockEntity.OVER), 0x40C03028);
        g.fill(x0, heatY(KilnBlockEntity.TEMPER_MAX), x1, heatY(KilnBlockEntity.TEMPER_MAX) + 1, 0x60FFFFFF);
        small(g, Component.literal(Math.round(KilnBlockEntity.ZONE_LOW * 10) + "–" + Math.round(KilnBlockEntity.ZONE_HIGH * 10) + "°"),
                x0 + 2, heatY(KilnBlockEntity.ZONE_HIGH) + 1, 0xFF8FD16A);
        small(g, Component.literal(Math.round(KilnBlockEntity.TEMPER_MAX * 10) + "°"), x0 + 2,
                heatY(KilnBlockEntity.TEMPER_MAX) - 7, 0xC0FFFFFF);
        // Цель тяги
        if (lit()) {
            int ty = heatY(KilnBlockEntity.targetHeat(draft()));
            for (int px = x0; px < x1; px += 4) {
                g.fill(px, ty, px + 2, ty + 1, 0xFFFFD27F);
            }
        }
        // История: справа — сейчас
        for (int i = history.length - samples; i < history.length; i++) {
            int px = x0 + i;
            int py = heatY(history[i]);
            float h = history[i];
            int color = h > KilnBlockEntity.OVER ? BAD : h >= KilnBlockEntity.ZONE_LOW ? GOOD : WARN;
            g.fill(px, py - 1, px + 1, py + 1, color);
        }
        Component now = Component.literal(Math.round(heat() * 10) + "°");
        g.drawString(font, now, x1 - font.width(now) - 2, topPos + GRAPH_Y + 2, TEXT, true);
    }

    /** Ступени тяги: щёлкнуть — горн потянется к этой температуре. */
    private void draftPanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + DRAFT_X;
        for (int level = KilnBlockEntity.MAX_DRAFT; level >= 0; level--) {
            int row = KilnBlockEntity.MAX_DRAFT - level;
            int ry = topPos + DRAFT_Y + row * DRAFT_ROW;
            float target = KilnBlockEntity.targetHeat(level);
            boolean current = level == draft();
            boolean hover = mouseX >= x && mouseX < x + DRAFT_W && mouseY >= ry && mouseY < ry + DRAFT_ROW;
            int zone = target > KilnBlockEntity.OVER ? 0xFF8A2A1E : target >= KilnBlockEntity.ZONE_LOW ? 0xFF3E7A2E
                    : target <= KilnBlockEntity.TEMPER_MAX ? 0xFF5A6A8A : 0xFF6A5A48;
            g.fill(x, ry, x + DRAFT_W, ry + DRAFT_ROW - 1, current ? zone : hover ? 0x40FFFFFF : 0x20000000);
            g.fill(x, ry, x + 3, ry + DRAFT_ROW - 1, zone);
            Component label = Component.translatable("gui.halgventmod.kiln.draft_" + level, Math.round(target * 10));
            small(g, label, x + 6, ry + 2, current ? 0xFFFFFFFF : TEXT_DIM);
        }
    }

    private int draftAt(double mouseX, double mouseY) {
        int x = leftPos + DRAFT_X;
        int y = topPos + DRAFT_Y;
        if (mouseX < x || mouseX >= x + DRAFT_W || mouseY < y || mouseY >= y + (KilnBlockEntity.MAX_DRAFT + 1) * DRAFT_ROW) {
            return -1;
        }
        return KilnBlockEntity.MAX_DRAFT - (int) ((mouseY - y) / DRAFT_ROW);
    }

    /** План обжига: закал → обжиг → остыть; нынешний шаг светлее, у каждого полоса и подсказка. */
    private void plan(GuiGraphics g) {
        int count = menu.get(KilnBlockEntity.D_COUNT);
        int elapsed = menu.get(KilnBlockEntity.D_ELAPSED);
        int soak = menu.get(KilnBlockEntity.D_SOAK);
        float heat = heat();
        int step = count == 0 ? -1 : elapsed < KilnBlockEntity.TEMPER_TICKS ? 0 : soak < KilnBlockEntity.SOAK_NEEDED ? 1 : 2;
        int width = (PLAN_W - 8) / 3;
        for (int i = 0; i < 3; i++) {
            int x = leftPos + PLAN_X + i * (width + 4);
            int y = topPos + PLAN_Y;
            boolean active = i == step;
            boolean done = step > i;
            g.fill(x, y, x + width, y + 24, active ? 0x40FFFFFF : 0x20000000);
            small(g, Component.translatable("gui.halgventmod.kiln.step_" + i), x + 2, y + 2,
                    active ? TEXT : done ? GOOD : TEXT_DIM);
            float share = switch (i) {
                case 0 -> elapsed / (float) KilnBlockEntity.TEMPER_TICKS;
                case 1 -> soak / (float) KilnBlockEntity.SOAK_NEEDED;
                default -> step == 2 ? 1 - Math.max(0, heat - KilnBlockEntity.HOT_OPEN) / (MAX_HEAT - KilnBlockEntity.HOT_OPEN) : 0;
            };
            bar(g, x + 2, y + 10, width - 4, 3, share, done ? GOOD : 0xFFC88A4A);
            if (active) {
                Component hint;
                int color = TEXT;
                if (i == 0) {
                    boolean hot = heat > KilnBlockEntity.TEMPER_MAX || menu.get(KilnBlockEntity.D_EARLY) > 0;
                    hint = Component.translatable(hot ? "gui.halgventmod.kiln.st_hot" : "gui.halgventmod.kiln.st_going");
                    color = hot ? BAD : TEXT;
                } else if (i == 1) {
                    String key = heat > KilnBlockEntity.OVER ? "st_over" : heat < KilnBlockEntity.ZONE_LOW ? "st_low" : "st_zone";
                    hint = Component.translatable("gui.halgventmod.kiln." + key);
                    color = heat > KilnBlockEntity.OVER || heat < KilnBlockEntity.ZONE_LOW ? WARN : GOOD;
                } else {
                    boolean cool = heat <= KilnBlockEntity.HOT_OPEN;
                    hint = Component.translatable(cool ? "gui.halgventmod.kiln.st_take" : "gui.halgventmod.kiln.st_cooling");
                    color = cool ? GOOD : TEXT;
                }
                small(g, hint, x + 2, y + 16, color);
            }
        }
    }

    /** Дрова: запас полосой, на сколько хватит на этой тяге, ячейка. */
    private void fuel(GuiGraphics g) {
        int x = leftPos + DRAFT_X;
        int y = topPos + 86;
        int fuel = menu.get(KilnBlockEntity.D_FUEL);
        small(g, Component.translatable("gui.halgventmod.kiln.fuel"), x, y, TEXT_DIM);
        bar(g, x, y + 8, 56, 4, fuel / (float) KilnBlockEntity.MAX_FUEL, 0xFFE08A2A);
        double perTick = KilnBlockEntity.BURN_BASE + KilnBlockEntity.BURN_PER_DRAFT * draft();
        small(g, Component.translatable("gui.halgventmod.kiln.fuel_left", time((int) Math.round(fuel / perTick))), x, y + 30,
                lit() ? TEXT : TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int level = draftAt(mouseX, mouseY);
        if (level >= 0 && button == 0) {
            click(KilnMenu.BUTTON_DRAFT + level);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
