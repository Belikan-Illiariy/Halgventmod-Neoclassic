package com.nrdhvnstd.halgventmod.client.leather;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.leather.ScrapeJob;
import com.nrdhvnstd.halgventmod.network.ScrapeResultPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Мини-игра скобления на колоде. Шкура сверху, хребет посередине, волокно идёт от хребта к краям.
 * Зажмите ЛКМ и ведите инструмент: материал снимается там, где прошло лезвие. К хребту лезвие идёт вхолостую,
 * поперёк волокна снимает плохо и давит сильнее. Чем быстрее мазок, тем выше нажим; на пределе нажима и на тонких
 * (светлых) местах при высоком нажиме — порез. В пергаменте слишком тонкое место рвётся: прореха.
 * Итог уходит на сервер ({@link ScrapeResultPayload}). Esc — бросить: шкура на колоде не меняется.
 */
public class ScrapeScreen extends Screen {
    // Меняйте здесь ⚠️
    private static final float GOOD_SPEED = 170.0F;     // пикселей окна в секунду: мазок с полной силой
    private static final float MIN_SPEED_EFFECT = 0.2F; // сила совсем медленного мазка
    private static final float MAX_SPEED_EFFECT = 1.2F;
    private static final float ACROSS_EFFECT = 0.15F;   // сила мазка поперёк волокна
    private static final float PRESSURE_GAIN = 0.55F;   // в секунду при хорошей скорости
    private static final float PRESSURE_DECAY = 0.45F;  // в секунду
    private static final float ACROSS_PRESSURE = 1.5F;  // прибавка к нажиму поперёк волокна
    private static final float BARE_PRESSURE = 1.8F;    // множитель нажима по уже чистому месту
    private static final float THIN_LIMIT = 0.55F;      // такой нажим режет тонкое место
    private static final float AFTER_CUT_PRESSURE = 0.35F;
    private static final int CUT_PAUSE_MS = 800;        // следующий порез не раньше
    private static final int BLADE_RADIUS = 3;          // клеток от середины лезвия
    private static final float FLESH_PASS = 1.0F;       // сколько снимает один хороший проход
    private static final float HAIR_PASS = 0.7F;
    private static final float PARCHMENT_PASS = 0.3F;
    private static final float FLESH_GOAL = 0.97F;      // доля клеток, после которой работа закончена
    private static final float HAIR_GOAL = 0.9F;
    private static final float PARCHMENT_GOAL = 0.92F;
    private static final float PARCHMENT_DONE = 0.4F;   // толщина готового пергамента
    private static final float PARCHMENT_TEAR = 0.05F;  // тоньше — прореха
    private static final int FINISH_DELAY_MS = 700;

    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int WIDTH = 250;
    private static final int HEIGHT = 200;
    private static final int COLS = 48;
    private static final int ROWS = 32;
    private static final int CELL = 4;
    private static final int FIELD_X = 14;
    private static final int FIELD_Y = 26;
    private static final int GAUGE_X = 216;

    private final BlockPos pos;
    private final ScrapeJob job;
    private final ItemStack tool;
    private final boolean[] onHide = new boolean[COLS * ROWS];
    private final boolean[] thin = new boolean[COLS * ROWS];
    private final boolean[] torn = new boolean[COLS * ROWS];
    private final boolean[] workable = new boolean[COLS * ROWS];
    private final float[] amount = new float[COLS * ROWS];
    private final float[] noise = new float[COLS * ROWS];
    private final List<int[]> scars = new ArrayList<>();
    private final List<Fleck> flecks = new ArrayList<>();
    private final RandomSource flecksRandom = RandomSource.create();
    private int workableCells;
    private float progress;
    private float pressure;
    private float speed;
    private int cuts;
    private int tears;
    private boolean down;
    private boolean sent;
    private int lastX = Integer.MIN_VALUE;
    private int lastY;
    private long lastFrame = -1;
    private long cutReadyAt;
    private long flashUntil;
    private long nextSoundAt;
    private long finishedAt = -1;

    private static final class Fleck {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        int color;
    }

    public ScrapeScreen(BlockPos pos, ScrapeJob job, ItemStack tool, int size, int seed) {
        super(Component.translatable("gui.halgventmod.scrape.title." + job.id()));
        this.pos = pos;
        this.job = job;
        this.tool = tool;
        build(RandomSource.create(seed), size);
    }

    public static void open(BlockPos pos, ScrapeJob job, ItemStack tool, int size, int seed) {
        Minecraft.getInstance().setScreen(new ScrapeScreen(pos, job, tool, size, seed));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- шкура

    private void build(RandomSource random, int size) {
        float phase = random.nextFloat() * Mth.TWO_PI;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float nx = (c + 0.5F - COLS / 2.0F) / (COLS / 2.0F);
                float ny = (r + 0.5F - ROWS / 2.0F) / (ROWS / 2.0F);
                float edge = 0.88F + 0.1F * Mth.sin((float) Mth.atan2(ny, nx) * 5.0F + phase);
                int cell = c + r * COLS;
                onHide[cell] = nx * nx + ny * ny <= edge * edge;
                noise[cell] = random.nextFloat();
            }
        }
        for (int i = 0; i < 3; i++) {
            blob(random, 3 + random.nextInt(2), (cell, falloff) -> thin[cell] = true);
        }
        switch (job) {
            case FLESH -> {
                int patches = Mth.clamp(4 + size / 2, 4, 8);
                for (int i = 0; i < patches; i++) {
                    float layers = 1 + random.nextInt(3);
                    blob(random, 4 + random.nextInt(3), (cell, falloff) ->
                            amount[cell] = Math.max(amount[cell], Math.max(0.6F, layers * falloff)));
                }
            }
            case HAIR -> {
                for (int cell = 0; cell < amount.length; cell++) {
                    amount[cell] = onHide[cell] ? 0.8F + 0.4F * noise[cell] : 0.0F;
                }
            }
            case PARCHMENT -> {
                for (int cell = 0; cell < amount.length; cell++) {
                    amount[cell] = onHide[cell] ? (thin[cell] ? 0.75F : 1.0F) + 0.1F * noise[cell] : 0.0F;
                }
                for (int i = 0; i < 5; i++) {
                    blob(random, 4 + random.nextInt(3), (cell, falloff) -> amount[cell] += 0.5F * falloff);
                }
            }
        }
        for (int cell = 0; cell < amount.length; cell++) {
            workable[cell] = onHide[cell] && (job != ScrapeJob.FLESH || amount[cell] > 0.0F);
            if (workable[cell]) {
                workableCells++;
            }
        }
    }

    private interface BlobCell {
        void accept(int cell, float falloff);
    }

    /** Пятно на шкуре: falloff — 1 в середине, к краю меньше. */
    private void blob(RandomSource random, int radius, BlobCell action) {
        int centerX;
        int centerY;
        int tries = 0;
        do {
            centerX = random.nextInt(COLS);
            centerY = random.nextInt(ROWS);
        } while (!onHide[centerX + centerY * COLS] && ++tries < 50);
        for (int r = Math.max(0, centerY - radius); r <= Math.min(ROWS - 1, centerY + radius); r++) {
            for (int c = Math.max(0, centerX - radius); c <= Math.min(COLS - 1, centerX + radius); c++) {
                float distance = Mth.sqrt((c - centerX) * (c - centerX) + (r - centerY) * (r - centerY));
                if (distance <= radius && onHide[c + r * COLS]) {
                    action.accept(c + r * COLS, 1.0F - 0.5F * distance / radius);
                }
            }
        }
    }

    // ---------------------------------------------------------------- работа

    private void advance(long now, int mouseX, int mouseY) {
        if (lastFrame < 0) {
            lastFrame = now;
        }
        float seconds = Mth.clamp((now - lastFrame) / 1000.0F, 0.001F, 0.1F);
        lastFrame = now;
        if (lastX == Integer.MIN_VALUE) {
            lastX = mouseX;
            lastY = mouseY;
        }
        int dx = mouseX - lastX;
        int dy = mouseY - lastY;
        int fromX = lastX;
        int fromY = lastY;
        lastX = mouseX;
        lastY = mouseY;
        float distance = Mth.sqrt(dx * dx + dy * dy);
        speed = Mth.lerp(0.35F, speed, distance / seconds);
        updateFlecks(seconds);
        if (finishedAt >= 0) {
            return;
        }
        float gain = 0.0F;
        if (down && distance > 0.0F) {
            float middle = (fromX + mouseX) / 2.0F - left() - FIELD_X - COLS * CELL / 2.0F;
            float outward = Math.abs(middle) < CELL ? Math.abs(dx) / distance : Math.signum(middle) * dx / distance;
            if (outward >= -0.3F) { // к хребту лезвие идёт вхолостую
                float along = Math.max(0.0F, outward);
                float ratio = speed / GOOD_SPEED;
                float effect = (ACROSS_EFFECT + (1.0F - ACROSS_EFFECT) * along)
                        * Mth.clamp(ratio, MIN_SPEED_EFFECT, MAX_SPEED_EFFECT);
                boolean[] touched = stroke(fromX, fromY, dx, dy, distance, effect, now);
                if (touched[0]) {
                    gain = ratio * ratio * PRESSURE_GAIN * (1.0F + (1.0F - along) * ACROSS_PRESSURE);
                    if (!touched[1] && job != ScrapeJob.PARCHMENT) {
                        gain *= BARE_PRESSURE;
                    }
                    if (touched[2] && pressure >= THIN_LIMIT) {
                        cut(now, mouseX, mouseY);
                    }
                }
            }
        }
        pressure = Mth.clamp(pressure + (gain - PRESSURE_DECAY) * seconds, 0.0F, 1.0F);
        if (pressure >= 1.0F) {
            cut(now, mouseX, mouseY);
        }
        updateProgress(now);
    }

    /** Провести лезвие вдоль отрезка. @return {на шкуре ли, снято ли что-то, задето ли тонкое место} */
    private boolean[] stroke(int fromX, int fromY, int dx, int dy, float distance, float effect, long now) {
        boolean[] touched = new boolean[3];
        float pass = switch (job) {
            case FLESH -> FLESH_PASS;
            case HAIR -> HAIR_PASS;
            case PARCHMENT -> PARCHMENT_PASS;
        };
        int steps = Math.max(1, Mth.ceil(distance / 2.0F));
        float perStep = pass * effect * (distance / steps) / CELL;
        for (int s = 1; s <= steps; s++) {
            int c = Math.floorDiv(fromX + dx * s / steps - left() - FIELD_X, CELL);
            int middleRow = Math.floorDiv(fromY + dy * s / steps - top() - FIELD_Y, CELL);
            if (c < 0 || c >= COLS) {
                continue;
            }
            for (int r = Math.max(0, middleRow - BLADE_RADIUS); r <= Math.min(ROWS - 1, middleRow + BLADE_RADIUS); r++) {
                scrapeCell(c, r, perStep, touched, now);
            }
        }
        return touched;
    }

    private void scrapeCell(int c, int r, float removed, boolean[] touched, long now) {
        int cell = c + r * COLS;
        if (!onHide[cell] || torn[cell]) {
            return;
        }
        touched[0] = true;
        touched[2] |= thin[cell];
        if (amount[cell] <= 0.0F) {
            return;
        }
        touched[1] = true;
        amount[cell] = Math.max(0.0F, amount[cell] - removed);
        spawnFleck(c, r);
        if (now >= nextSoundAt && minecraft != null) {
            nextSoundAt = now + 130;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(job == ScrapeJob.FLESH
                    ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.WOOL_HIT, 0.9F + flecksRandom.nextFloat() * 0.3F, 0.5F));
        }
        if (job == ScrapeJob.PARCHMENT && amount[cell] <= PARCHMENT_TEAR) {
            tear(c, r, now);
        }
    }

    private void tear(int c, int r, long now) {
        tears++;
        flashUntil = now + 300;
        for (int row = Math.max(0, r - 1); row <= Math.min(ROWS - 1, r + 1); row++) {
            for (int col = Math.max(0, c - 1); col <= Math.min(COLS - 1, c + 1); col++) {
                if (onHide[col + row * COLS]) {
                    torn[col + row * COLS] = true;
                }
            }
        }
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_BREAK, 0.7F));
        }
    }

    private void cut(long now, int mouseX, int mouseY) {
        if (now < cutReadyAt) {
            return;
        }
        cutReadyAt = now + CUT_PAUSE_MS;
        cuts++;
        pressure = AFTER_CUT_PRESSURE;
        flashUntil = now + 300;
        scars.add(new int[] {mouseX - left(), mouseY - top()});
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SHEEP_SHEAR, 0.6F));
        }
    }

    private void updateProgress(long now) {
        int ready = 0;
        for (int cell = 0; cell < amount.length; cell++) {
            if (workable[cell] && (torn[cell] || amount[cell] <= (job == ScrapeJob.PARCHMENT ? PARCHMENT_DONE : 0.0F))) {
                ready++;
            }
        }
        progress = workableCells == 0 ? 1.0F : (float) ready / workableCells;
        float goal = switch (job) {
            case FLESH -> FLESH_GOAL;
            case HAIR -> HAIR_GOAL;
            case PARCHMENT -> PARCHMENT_GOAL;
        };
        if (progress >= goal) {
            finishedAt = now;
            down = false;
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2F));
            }
        }
    }

    private void spawnFleck(int c, int r) {
        if (flecks.size() >= 60 || flecksRandom.nextFloat() > 0.15F) {
            return;
        }
        Fleck fleck = new Fleck();
        fleck.x = FIELD_X + c * CELL + 2;
        fleck.y = FIELD_Y + r * CELL + 2;
        fleck.vx = (flecksRandom.nextFloat() - 0.5F) * 60.0F;
        fleck.vy = -20.0F - flecksRandom.nextFloat() * 40.0F;
        fleck.life = 0.5F;
        fleck.color = switch (job) {
            case FLESH -> 0xFFA0504A;
            case HAIR -> 0xFF5A4630;
            case PARCHMENT -> 0xFFE8DCC0;
        };
        flecks.add(fleck);
    }

    private void updateFlecks(float seconds) {
        flecks.removeIf(fleck -> {
            fleck.life -= seconds;
            fleck.x += fleck.vx * seconds;
            fleck.y += fleck.vy * seconds;
            fleck.vy += 180.0F * seconds;
            return fleck.life <= 0.0F;
        });
    }

    // ---------------------------------------------------------------- отрисовка

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = System.currentTimeMillis();
        advance(now, mouseX, mouseY);
        int x = left();
        int y = top();
        g.blitSprite(PANEL, x, y, WIDTH, HEIGHT);
        TextFit.drawCentered(g, font, title, width / 2, y + 10, WIDTH - 20, 0xFFE9DCB4);

        int fieldX = x + FIELD_X;
        int fieldY = y + FIELD_Y;
        g.fill(fieldX - 2, fieldY - 2, fieldX + COLS * CELL + 2, fieldY + ROWS * CELL + 2,
                now < flashUntil ? 0xFFD03030 : 0xFF2A1A0E);
        g.fill(fieldX, fieldY, fieldX + COLS * CELL, fieldY + ROWS * CELL, 0xFF4A3220);
        g.drawManaged(() -> {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int cell = c + r * COLS;
                    if (onHide[cell]) {
                        int px = fieldX + c * CELL;
                        int py = fieldY + r * CELL;
                        g.fill(px, py, px + CELL, py + CELL, color(cell));
                    }
                }
            }
        });
        drawGrain(g, fieldX, fieldY);
        for (int[] scar : scars) {
            g.fill(x + scar[0] - 4, y + scar[1], x + scar[0] + 4, y + scar[1] + 1, 0xFF7A1010);
        }
        for (Fleck fleck : flecks) {
            g.fill(x + (int) fleck.x, y + (int) fleck.y, x + (int) fleck.x + 2, y + (int) fleck.y + 2, fleck.color);
        }
        drawGauge(g, x + GAUGE_X, fieldY);
        if (finishedAt < 0) {
            g.renderItem(tool, mouseX - 8, mouseY - 8);
        }

        Component status = finishedAt >= 0
                ? Component.translatable("gui.halgventmod.scrape.done")
                : Component.translatable("gui.halgventmod.scrape.progress", Math.round(progress * 100.0F), cuts, tears);
        TextFit.draw(g, font, status, x + 14, y + 162, WIDTH - 28, 0xFFE9DCB4);
        TextFit.draw(g, font, Component.translatable("gui.halgventmod.scrape.hint." + job.id()), x + 14, y + 174, WIDTH - 28, 0xFFAA9C80);
        TextFit.draw(g, font, Component.translatable("gui.halgventmod.scrape.hint"), x + 14, y + 185, WIDTH - 28, 0xFFAA9C80);

        if (finishedAt >= 0 && now - finishedAt > FINISH_DELAY_MS) {
            send(true);
            onClose();
        }
    }

    private int color(int cell) {
        if (torn[cell]) {
            return 0xFF1A100A;
        }
        float shade = noise[cell] * 0.06F;
        float value = amount[cell];
        int base = thin[cell] ? 0xFFEAD9BC : 0xFFD0B48C;
        int color = switch (job) {
            case FLESH -> value <= 0.0F ? base : value < 1.0F
                    ? blend(base, 0xFFB56A5A, 0.4F + 0.6F * value)
                    : blend(0xFFB56A5A, 0xFF6E2A2A, Mth.clamp((value - 1.0F) / 2.0F, 0.0F, 1.0F));
            case HAIR -> value <= 0.0F ? base : blend(base, 0xFF4E3C28, 0.45F + 0.55F * Mth.clamp(value, 0.0F, 1.0F));
            case PARCHMENT -> value > PARCHMENT_DONE
                    ? blend(0xFFEFE3C4, 0xFF8A6A42, Mth.clamp((value - PARCHMENT_DONE) / 1.0F, 0.0F, 1.0F))
                    : blend(0xFFAFC0C8, 0xFFEFE3C4, Mth.clamp((value - PARCHMENT_TEAR) / (PARCHMENT_DONE - PARCHMENT_TEAR), 0.0F, 1.0F));
        };
        return blend(color, 0xFF000000, shade);
    }

    /** Хребет и стрелки волокна: куда вести инструмент. */
    private void drawGrain(GuiGraphics g, int fieldX, int fieldY) {
        int spine = fieldX + COLS * CELL / 2;
        for (int i = 0; i < ROWS * CELL; i += 8) {
            g.fill(spine, fieldY + i, spine + 1, fieldY + i + 4, 0x80301C0C);
        }
        for (int row = 1; row <= 3; row++) {
            int ay = fieldY + ROWS * CELL * row / 4;
            arrow(g, spine + 34, ay, 1);
            arrow(g, spine - 34, ay, -1);
        }
    }

    private void arrow(GuiGraphics g, int x, int y, int direction) {
        for (int i = 0; i < 3; i++) {
            int px = x + direction * i * 2;
            g.fill(px, y - 2 + i, px + 2, y + 3 - i, 0x70301C0C);
        }
    }

    private void drawGauge(GuiGraphics g, int x, int fieldY) {
        int height = ROWS * CELL;
        g.fill(x - 1, fieldY - 1, x + 13, fieldY + height + 1, 0xFF2A1A0E);
        g.fill(x, fieldY, x + 12, fieldY + height, 0xFF3A2A1C);
        int filled = (int) (height * pressure);
        int color = pressure >= 0.8F ? 0xFFD03030 : pressure >= THIN_LIMIT ? 0xFFD8A030 : 0xFF6FA040;
        g.fill(x, fieldY + height - filled, x + 12, fieldY + height, color);
        int tick = fieldY + height - (int) (height * THIN_LIMIT);
        g.fill(x - 2, tick, x + 14, tick + 1, 0xFFEAD9BC);
    }

    private static int blend(int from, int to, float amount) {
        float t = Mth.clamp(amount, 0.0F, 1.0F);
        int red = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int green = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int blue = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    // ---------------------------------------------------------------- ввод и итог

    private void send(boolean finished) {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new ScrapeResultPayload(pos, finished, cuts, tears));
        }
    }

    @Override
    public void onClose() {
        send(false);
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            down = finishedAt < 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            down = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
