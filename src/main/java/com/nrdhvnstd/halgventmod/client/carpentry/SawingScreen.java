package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.carpentry.Carpentry;
import com.nrdhvnstd.halgventmod.carpentry.SawhorseBlockEntity;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.network.SawCutPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Мини-игра «Пропил» у пильных козел. Бревно видно сверху, вдоль него — {@link Carpentry#CUTS} карандашные линии:
 * по ним бревно распускают на доски. Бревно не прямое — линии чуть выгнуты. Пропил идёт от себя (снизу вверх):
 * <ul>
 *   <li><b>Толчок.</b> Пила сама ходит взад-вперёд. ЛКМ или Пробел — нажать на пилу, когда она идёт от вас
 *       (полоска толчка зелёная); точно в середине толчка — глубже всего. Нажали на обратном ходу — пила вязнет,
 *       и, если так раз за разом, её зажимает. Ровные толчки подряд разгоняют темп.</li>
 *   <li><b>Ведение.</b> Мышь — куда смотрит пила: пропил поворачивает к ней понемногу, с каждым толчком. Волокно
 *       уводит пилу в свою сторону, трещина в бревне тянет к себе. Вышли за зелёную полосу у линии — пропил кривой,
 *       доска уйдёт в горбыль.</li>
 *   <li><b>Сучки</b> (тёмные пятна): в сучке пила идёт вдвое медленнее, а неточный толчок дёргает её вбок.</li>
 * </ul>
 * Твёрдое дерево (дуб, акация) пилится дольше мягкого. Каждый пропил уходит на сервер ({@link SawCutPayload});
 * сколько уже распилено, окно читает из блок-сущности. Esc — отойти: сделанные пропилы остаются на козлах.
 */
public class SawingScreen extends Screen {
    // Меняйте здесь
    private static final int PERIOD_MS = 1050;         // ход пилы туда-обратно
    private static final float PERIOD_PER_COMBO = 0.06F; // быстрее с каждым точным толчком подряд
    private static final int MIN_PERIOD_MS = 600;
    private static final float ADVANCE = 0.11F;        // доля пропила за точный толчок (мягкое дерево — больше)
    private static final float ACCURATE = 0.7F;        // точность толчка не меньше — «точный», темп растёт
    private static final float KNOT_SLOW = 0.5F;       // в сучке пила идёт медленнее
    private static final float KNOT_ACCURACY = 0.6F;   // в сучке толчок неточнее этого — пилу дёргает вбок
    private static final float KNOT_KICK = 3.0F;
    private static final float BIND_WRONG = 0.4F;      // нажали на обратном ходу
    private static final float BIND_COOL = 0.15F;      // остывает за точный толчок
    private static final int STUCK_MS = 900;           // зажатая пила стоит
    private static final float TURN = 0.35F;           // насколько пропил поворачивает к мыши на пиксель пропила
    private static final float GRAIN_PULL = 0.12F;     // увод волокном на пиксель пропила
    private static final float CRACK_PULL = 0.3F;      // трещина тянет на пиксель пропила
    private static final int CRACK_REACH = 12;
    private static final float TOLERANCE = 2.5F;       // средний отход от линии — не больше: пропил ровный
    private static final float MAX_DRIFT = 6.0F;       // а дальше этого нельзя ни разу
    private static final int LABEL_MS = 1100;

    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int WIDTH = 280;
    private static final int HEIGHT = 214;
    private static final int LOG_W = 126;
    private static final int LOG_TOP = 28;
    private static final int LOG_LEN = 130;
    private static final int TEXT = 0xFFE9DCB4;
    private static final int TEXT_DIM = 0xFFAA9C80;
    private static final int BARK = 0xFF4A3220;
    private static final int BARK_DARK = 0xFF2E1E12;
    private static final int WOOD = 0xFFD9B98A;
    private static final int GRAIN = 0xFFC4A070;
    private static final int KNOT = 0xFF6A4424;
    private static final int CRACK = 0xFF3A2414;
    private static final int KERF = 0xFF1A120C;
    private static final int PENCIL = 0xFF5A5A6E;
    private static final int BAND = 0x303E7A2E;
    private static final int STEEL = 0xFFB4BCC4;
    private static final int STEEL_DARK = 0xFF5E666E;
    private static final int FRAME_WOOD = 0xFF6E4626;
    private static final int TRACK = 0xFF2A2018;
    private static final int GOOD = 0xFF8FD16A;
    private static final int WARN = 0xFFE8B04A;
    private static final int BAD = 0xFFE0604A;

    private record Knot(float x, float t, float rx, float ry) {}

    private enum State { SAW, WAIT }

    private final BlockPos pos;
    private final Random random = new Random();
    // Бревно этого захода: изгиб линий, сучки, трещина, кора, волокно
    private final float bow;
    private final float[] wiggle = new float[Carpentry.CUTS];
    private final float[] wigglePhase = new float[Carpentry.CUTS];
    private final List<Knot> knots = new ArrayList<>();
    private final float crackX;         // смещение трещины от середины бревна, пиксели; NaN — трещины нет
    private final float crackFrom;
    private final float crackTo;
    private final int[] barkLeft = new int[LOG_LEN];
    private final int[] barkRight = new int[LOG_LEN];
    private final float[] grainLines = new float[9];
    private float grain;                // куда уводит волокно на этом пропиле: −1…1
    private float softness = 1.0F;

    private State state = State.SAW;
    private final Map<Integer, List<float[]>> paths = new HashMap<>(); // пропилы этого захода: (доля, x от середины)
    private final List<float[]> path = new ArrayList<>();
    private float progress;             // 0 — у края бревна, 1 — пропил прошёл
    private float kerfX;                // где пропил, от середины бревна
    private double phase;               // ход пилы
    private long lastFrame = -1;
    private boolean pushed;             // в этот толчок уже нажимали
    private int combo;
    private float bind;
    private int jams;
    private long stuckUntil;
    private float lastAccuracy = -1;
    private double aimX;                // мышь, от середины бревна
    private int sentAtCuts = -1;
    private long waitSince;
    private Component label = Component.empty();
    private int labelColor;
    private long labelAt = -1;
    private long doneAt = -1;

    public SawingScreen(BlockPos pos) {
        super(Component.translatable("gui.halgventmod.sawing.title"));
        this.pos = pos;
        this.bow = (random.nextFloat() * 2 - 1) * 8;
        for (int i = 0; i < Carpentry.CUTS; i++) {
            wiggle[i] = 0.6F + random.nextFloat() * 1.2F;
            wigglePhase[i] = random.nextFloat() * 6.28F;
        }
        int knotCount = 2 + random.nextInt(3);
        for (int i = 0; i < knotCount; i++) {
            knots.add(new Knot((random.nextFloat() - 0.5F) * (LOG_W - 30), 0.12F + random.nextFloat() * 0.76F,
                    4 + random.nextFloat() * 4, 3 + random.nextFloat() * 3));
        }
        if (random.nextFloat() < 0.6F) {
            int line = random.nextInt(Carpentry.CUTS);
            crackX = lineBase(line) + (random.nextBoolean() ? 1 : -1) * (6 + random.nextFloat() * 4);
            crackFrom = random.nextFloat() * 0.5F;
            crackTo = crackFrom + 0.25F + random.nextFloat() * 0.25F;
        } else {
            crackX = Float.NaN;
            crackFrom = crackTo = 0;
        }
        for (int row = 0; row < LOG_LEN; row++) {
            barkLeft[row] = 4 + (int) Math.round(2 * Math.sin(row * 0.31) + random.nextFloat() * 1.5);
            barkRight[row] = 4 + (int) Math.round(2 * Math.sin(row * 0.27 + 2) + random.nextFloat() * 1.5);
        }
        for (int i = 0; i < grainLines.length; i++) {
            grainLines[i] = (i + 0.5F) / grainLines.length * (LOG_W - 16) - (LOG_W - 16) / 2.0F + random.nextFloat() * 4;
        }
        newCut();
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SawingScreen(pos));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    private SawhorseBlockEntity sawhorse() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof SawhorseBlockEntity sawhorse ? sawhorse : null;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private int centerX() {
        return left() + 92;
    }

    private int logBottom() {
        return top() + LOG_TOP + LOG_LEN;
    }

    /** Прямая разметка линии cut, от середины бревна. */
    private static float lineBase(int cut) {
        float spacing = (float) LOG_W / (Carpentry.CUTS + 1);
        return (cut - (Carpentry.CUTS - 1) / 2.0F) * spacing;
    }

    /** Где линия cut на доле t пропила (0 — ближний край): бревно выгнуто, карандаш чуть гуляет. */
    private float lineX(int cut, float t) {
        return lineBase(cut) + bow * (float) Math.sin(Math.PI * t)
                + wiggle[cut] * (float) Math.sin(t * Math.PI * 3 + wigglePhase[cut]);
    }

    @Nullable
    private Knot knotAt(float x, float t) {
        for (Knot knot : knots) {
            float dx = (x - knot.x()) / knot.rx();
            float dy = (t - knot.t()) * LOG_LEN / knot.ry();
            if (dx * dx + dy * dy <= 1) {
                return knot;
            }
        }
        return null;
    }

    private int currentCut(SawhorseBlockEntity sawhorse) {
        return state == State.WAIT ? sentAtCuts + 1 : sawhorse.cuts();
    }

    private void newCut() {
        progress = 0;
        path.clear();
        bind = 0;
        jams = 0;
        combo = 0;
        grain = random.nextFloat() * 2 - 1;
    }

    // ---------------------------------------------------------------- ход игры

    private int period() {
        return Math.max(MIN_PERIOD_MS, Math.round(PERIOD_MS / (1 + PERIOD_PER_COMBO * combo)));
    }

    /** Пила идёт от себя: толчок. */
    private boolean pushing() {
        return Math.sin(phase) > 0;
    }

    private void tick(long now, SawhorseBlockEntity sawhorse) {
        if (lastFrame < 0) {
            lastFrame = now;
            softness = Carpentry.softness(sawhorse.log());
            kerfX = lineX(sawhorse.cuts(), 0);
        }
        long dt = Math.min(100, now - lastFrame);
        lastFrame = now;
        if (now >= stuckUntil) {
            boolean was = pushing();
            phase += dt * Math.PI * 2 / period();
            if (was != pushing()) {
                if (was && !pushed && state == State.SAW && progress > 0) {
                    combo = 0; // пропустили толчок — темп сбился
                }
                pushed = false;
                screen(SoundEvents.WOOD_HIT, pushing() ? 1.9F : 1.6F, 0.25F);
            }
        }
        if (state == State.WAIT && (sawhorse.cuts() != sentAtCuts || now - waitSince > 900)) {
            state = State.SAW;
            newCut();
            kerfX = lineX(sawhorse.cuts(), 0);
        }
    }

    private void push(SawhorseBlockEntity sawhorse) {
        long now = System.currentTimeMillis();
        if (state != State.SAW || now < stuckUntil || sawhorse.cuts() >= Carpentry.CUTS) {
            return;
        }
        if (!pushing()) {
            bind += BIND_WRONG;
            combo = 0;
            if (bind >= 1.0F) {
                jams++;
                bind = 0;
                stuckUntil = now + STUCK_MS;
                kerfX += (random.nextBoolean() ? 1 : -1) * 1.5F;
                show("jam", BAD);
                sound(SoundEvents.CHAIN_HIT, 0.6F);
            } else {
                show("wrong", WARN);
                sound(SoundEvents.WOOD_HIT, 0.7F);
            }
            return;
        }
        if (pushed) {
            return;
        }
        pushed = true;
        float accuracy = (float) Math.sin(phase);
        lastAccuracy = accuracy;
        Knot knot = knotAt(kerfX, progress);
        float advance = ADVANCE * softness * (0.35F + 0.65F * accuracy) * (knot != null ? KNOT_SLOW : 1.0F);
        if (knot != null && accuracy < KNOT_ACCURACY) {
            kerfX += (random.nextBoolean() ? 1 : -1) * KNOT_KICK;
            bind += 0.3F;
            combo = 0;
            show("knot", BAD);
            sound(SoundEvents.WOOD_BREAK, 1.5F);
        } else if (accuracy >= ACCURATE) {
            combo++;
            bind = Math.max(0, bind - BIND_COOL);
            if (combo > 1 && combo % 4 == 0) {
                show("tempo", GOOD, combo);
            }
        } else {
            combo = 0;
        }
        // Пропил поворачивает к мыши понемногу, волокно и трещина уводят
        float dy = Math.min(advance, 1 - progress) * LOG_LEN;
        float turn = (float) Mth.clamp(aimX - kerfX, -TURN * dy, TURN * dy);
        kerfX += turn + grain * GRAIN_PULL * dy;
        if (!Float.isNaN(crackX) && progress >= crackFrom && progress <= crackTo && Math.abs(kerfX - crackX) < CRACK_REACH) {
            kerfX += Math.signum(crackX - kerfX) * Math.min(Math.abs(crackX - kerfX), CRACK_PULL * dy);
            show("crack", WARN);
        }
        progress = Math.min(1.0F, progress + advance);
        path.add(new float[] {progress, kerfX});
        sound(SoundEvents.WOOD_HIT, 1.2F + 0.3F * accuracy);
        if (progress >= 1.0F) {
            finishCut(sawhorse);
        }
    }

    private void finishCut(SawhorseBlockEntity sawhorse) {
        int cut = sawhorse.cuts();
        float sum = 0;
        float max = 0;
        for (float[] point : path) {
            float off = Math.abs(point[1] - lineX(cut, point[0]));
            sum += off;
            max = Math.max(max, off);
        }
        float mean = path.isEmpty() ? 0 : sum / path.size();
        boolean crooked = mean > TOLERANCE || max > MAX_DRIFT;
        paths.put(cut, new ArrayList<>(path));
        PacketDistributor.sendToServer(new SawCutPayload(pos, crooked ? Carpentry.CUT_CROOKED : Carpentry.CUT_EVEN, jams));
        show(crooked ? "crooked" : "even", crooked ? BAD : GOOD);
        sound(SoundEvents.WOOD_BREAK, crooked ? 0.8F : 1.1F);
        sentAtCuts = cut;
        waitSince = System.currentTimeMillis();
        state = State.WAIT;
    }

    private void show(String key, int color, Object... args) {
        label = Component.translatable("gui.halgventmod.sawing." + key, args);
        labelColor = color;
        labelAt = System.currentTimeMillis();
    }

    private void sound(SoundEvent event, float pitch) {
        screen(event, pitch, 1.0F);
    }

    private void screen(SoundEvent event, float pitch, float volume) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
        }
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        SawhorseBlockEntity sawhorse = sawhorse();
        long now = System.currentTimeMillis();
        if (sawhorse == null || sawhorse.log().isEmpty()) {
            // Последний пропил: бревно снято с козел — показать итог и закрыть
            if (sentAtCuts == Carpentry.CUTS - 1 && doneAt < 0) {
                doneAt = now;
            }
            if (doneAt < 0 || now - doneAt > 1200) {
                onClose();
                return;
            }
        } else {
            tick(now, sawhorse);
        }
        int cx = centerX();
        aimX = Mth.clamp(mouseX - cx, -LOG_W / 2.0, LOG_W / 2.0);

        int x = left();
        int y = top();
        g.blitSprite(PANEL, x, y, WIDTH, HEIGHT);
        Component name = sawhorse == null || sawhorse.log().isEmpty() ? Component.empty() : sawhorse.log().getHoverName();
        TextFit.drawCentered(g, font, Component.translatable("gui.halgventmod.sawing.title_of", name), x + WIDTH / 2, y + 9,
                WIDTH - 20, TEXT);

        drawLog(g, cx, y + LOG_TOP);
        int current = sawhorse == null ? Carpentry.CUTS : currentCut(sawhorse);
        for (int cut = 0; cut < Carpentry.CUTS; cut++) {
            drawLine(g, cx, cut, cut == current && state == State.SAW);
            List<float[]> done = cut == current && state == State.SAW ? path : paths.get(cut);
            if (done != null) {
                drawKerf(g, cx, cut, done);
            } else if (cut < current) {
                for (int row = 0; row < LOG_LEN; row++) {
                    int px = cx + Math.round(lineX(cut, row / (float) LOG_LEN));
                    g.fill(px, logBottom() - row - 1, px + 2, logBottom() - row, KERF); // распилено раньше
                }
            }
        }
        if (current < Carpentry.CUTS && state == State.SAW && sawhorse != null) {
            drawSaw(g, cx, now);
        }
        drawGauges(g, x, y, now);

        int infoY = y + HEIGHT - 30;
        int cuts = sawhorse == null ? Carpentry.CUTS : sawhorse.cuts();
        int crooked = sawhorse == null ? 0 : sawhorse.crooked();
        TextFit.draw(g, font, Component.translatable("gui.halgventmod.sawing.progress", Math.min(cuts + 1, Carpentry.CUTS),
                Carpentry.CUTS, crooked, jams), x + 14, infoY, WIDTH - 28, TEXT);
        Component hint = doneAt >= 0 ? Component.translatable("gui.halgventmod.sawing.done")
                : Component.translatable("gui.halgventmod.sawing.hint");
        TextFit.draw(g, font, hint, x + 14, infoY + 12, WIDTH - 28, doneAt >= 0 ? GOOD : TEXT_DIM);
        if (labelAt >= 0 && now - labelAt < LABEL_MS) {
            TextFit.drawCentered(g, font, label, cx, y + LOG_TOP + 4, LOG_W + 20, labelColor);
        }
    }

    /** Бревно сверху: кора по краям, волокна вдоль, сучки, трещина. */
    private void drawLog(GuiGraphics g, int cx, int top) {
        int half = LOG_W / 2;
        g.fill(cx - half, top, cx + half, top + LOG_LEN, WOOD);
        for (float gx : grainLines) {
            for (int row = 0; row < LOG_LEN; row += 2) {
                int px = cx + Math.round(gx + 1.5F * (float) Math.sin(row * 0.05 + gx));
                g.fill(px, top + row, px + 1, top + row + 1, GRAIN);
            }
        }
        for (Knot knot : knots) {
            int kx = cx + Math.round(knot.x());
            int ky = top + LOG_LEN - Math.round(knot.t() * LOG_LEN);
            int rx = Math.round(knot.rx());
            int ry = Math.round(knot.ry());
            for (int dy = -ry; dy <= ry; dy++) {
                int w = Math.round(rx * (float) Math.sqrt(Math.max(0, 1 - dy * dy / (float) (ry * ry))));
                g.fill(kx - w, ky + dy, kx + w + 1, ky + dy + 1, KNOT);
            }
            g.fill(kx - 1, ky - 1, kx + 1, ky + 1, BARK_DARK);
        }
        if (!Float.isNaN(crackX)) {
            for (int row = Math.round(crackFrom * LOG_LEN); row < Math.round(crackTo * LOG_LEN); row++) {
                int px = cx + Math.round(crackX + 1.2F * (float) Math.sin(row * 0.4));
                g.fill(px, top + LOG_LEN - row - 1, px + 1, top + LOG_LEN - row, CRACK);
            }
        }
        for (int row = 0; row < LOG_LEN; row++) {
            g.fill(cx - half, top + row, cx - half + barkLeft[row], top + row + 1, row % 5 == 0 ? BARK_DARK : BARK);
            g.fill(cx + half - barkRight[row], top + row, cx + half, top + row + 1, row % 5 == 2 ? BARK_DARK : BARK);
        }
    }

    /** Карандашная линия пунктиром; у той, по которой пилят, — зелёная полоса допуска. */
    private void drawLine(GuiGraphics g, int cx, int cut, boolean active) {
        int bottom = logBottom();
        for (int row = 0; row < LOG_LEN; row++) {
            float lx = cx + lineX(cut, row / (float) LOG_LEN);
            if (active) {
                g.fill(Math.round(lx - TOLERANCE), bottom - row - 1, Math.round(lx + TOLERANCE) + 1, bottom - row, BAND);
            }
            if (row % 4 < 2) {
                g.fill(Math.round(lx), bottom - row - 1, Math.round(lx) + 1, bottom - row, PENCIL);
            }
        }
    }

    /** Пропил: тёмная щель по точкам (доля, x), от ближнего края. */
    private void drawKerf(GuiGraphics g, int cx, int cut, List<float[]> points) {
        int bottom = logBottom();
        float prevT = 0;
        float prevX = lineX(cut, 0);
        for (float[] point : points) {
            int r0 = Math.round(prevT * LOG_LEN);
            int r1 = Math.max(r0 + 1, Math.round(point[0] * LOG_LEN));
            for (int row = r0; row < r1; row++) {
                float t = (row - r0) / (float) Math.max(1, r1 - r0);
                int px = cx + Math.round(prevX + (point[1] - prevX) * t);
                g.fill(px, bottom - row - 1, px + 2, bottom - row, KERF);
            }
            prevT = point[0];
            prevX = point[1];
        }
    }

    /** Пила: полотно из пропила к себе, повернуто туда, куда смотрит мышь, ходит взад-вперёд. */
    private void drawSaw(GuiGraphics g, int cx, long now) {
        int front = logBottom() - Math.round(progress * LOG_LEN);
        float stroke = (float) -Math.cos(phase) * 8;      // −8 — к себе, +8 — от себя
        float fromX = cx + kerfX;
        float toX = (float) (cx + aimX);
        // Полотно: носок впереди пропила, ручка у ближнего края — не ниже бревна, чтобы не залезть на подписи
        int start = Math.round(front - 40 - stroke);
        int length = 56;
        boolean stuck = now < stuckUntil;
        for (int s = 0; s < length; s++) {
            float t = s / (float) length;
            int py = start + s;
            int px = Math.round(fromX + (toX - fromX) * t * 0.6F);
            g.fill(px - 1, py, px + 1, py + 1, stuck ? BAD : STEEL);
            if (s % 3 == 0) {
                g.fill(px + 1, py, px + 2, py + 1, STEEL_DARK); // зубья
            }
        }
        int hx = Math.round(fromX + (toX - fromX) * 0.6F);
        int hy = start + length;
        g.fill(hx - 7, hy, hx + 7, hy + 4, FRAME_WOOD);
        g.fill(hx - 2, hy + 4, hx + 2, hy + 12, FRAME_WOOD);
        // Куда целит мышь: штрих у ближнего края бревна
        g.fill(Math.round(toX), logBottom() + 2, Math.round(toX) + 1, logBottom() + 6, TEXT);
    }

    /** Справа: ход пилы (толчок — зелёное), точность последнего толчка, нагрев. */
    private void drawGauges(GuiGraphics g, int x, int y, long now) {
        int gx = x + WIDTH - 72;
        int gy = y + 34;
        int gh = 110;
        // Ход пилы: метка ходит вверх (толчок) и вниз (обратный ход)
        g.fill(gx - 1, gy - 1, gx + 13, gy + gh + 1, KERF);
        g.fill(gx, gy, gx + 12, gy + gh, TRACK);
        boolean push = pushing();
        g.fill(gx, gy, gx + 12, gy + gh, push ? 0xFF2E5A22 : 0xFF3A2218);
        int my = gy + gh / 2 + Math.round((float) Math.cos(phase) * (gh / 2 - 3));
        g.fill(gx - 3, my - 2, gx + 15, my + 2, now < stuckUntil ? BAD : push ? GOOD : TEXT_DIM);
        TextFit.drawCentered(g, font, Component.translatable(push ? "gui.halgventmod.sawing.push"
                : "gui.halgventmod.sawing.pull"), gx + 6, gy + gh + 4, 44, push ? GOOD : TEXT_DIM);
        // Нагрев
        int hx = gx + 28;
        g.fill(hx - 1, gy - 1, hx + 11, gy + gh + 1, KERF);
        g.fill(hx, gy, hx + 10, gy + gh, TRACK);
        boolean stuck = now < stuckUntil;
        int heat = stuck ? gh : Math.round(gh * Math.min(1.0F, bind));
        g.fill(hx, gy + gh - heat, hx + 10, gy + gh, stuck || bind > 0.66F ? BAD : bind > 0.33F ? WARN : GOOD);
        TextFit.drawCentered(g, font, Component.translatable("gui.halgventmod.sawing.heat"), hx + 5, gy + gh + 4, 40, TEXT_DIM);
        // Темп и точность
        TextFit.drawCentered(g, font, Component.translatable("gui.halgventmod.sawing.pace",
                String.format(java.util.Locale.ROOT, "%.1f", PERIOD_MS / (float) period())), gx + 20, gy - 12, 60, TEXT_DIM);
    }

    // ---------------------------------------------------------------- управление: ЛКМ или Пробел — толчок

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            SawhorseBlockEntity sawhorse = sawhorse();
            if (sawhorse != null) {
                push(sawhorse);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            SawhorseBlockEntity sawhorse = sawhorse();
            if (sawhorse != null) {
                push(sawhorse);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
