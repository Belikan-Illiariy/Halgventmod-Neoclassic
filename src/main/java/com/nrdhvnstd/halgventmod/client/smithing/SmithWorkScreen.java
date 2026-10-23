package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.network.SmithResultPayload;
import com.nrdhvnstd.halgventmod.smithing.SmithArea;
import com.nrdhvnstd.halgventmod.smithing.Smithing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно кузнечества: этапы по порядку ({@link SmithStep}), между ними — итог этапа. Для горячей работы окно ведёт
 * жар заготовки: вне горна она остывает, пробел — сунуть в горн и вынуть; в горне ЛКМ — качнуть мехи. Предел
 * жара — от жара кузнечного горна (сведения с сервера), каменный уголь греет быстрее. Передержали на белом —
 * пережог (штраф этапу). Медь и драгметаллы куют холодными: вместо остывания — наклёп, в горне — отжиг.
 * <p>После последнего этапа итог (счёт этапов и прочее) уходит на сервер (SmithResultPayload), но не раньше, чем
 * сервер его примет; Esc — бросить.
 */
public class SmithWorkScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int WIDTH = 300;
    private static final int HEIGHT = 232;
    private static final int FIELD_TOP = 34;
    private static final int FIELD_HEIGHT = 144;
    private static final int GAUGE = 26;
    private static final int BANNER_MS = 1100;
    private static final int LABEL_MS = 1200;

    // Жар заготовки ⚠️
    private static final float COOL = 0.075F;        // доля жара в секунду вне горна
    private static final float HEAT_RATE = 0.22F;    // жар в секунду в горне (каменный уголь — ×1,3)
    private static final float PUMP = 0.08F;         // мехи в окне: столько сверху к пределу
    private static final float PUMP_MAX = 0.24F;
    private static final float PUMP_DECAY = 0.05F;   // в секунду
    private static final float BURN_AT = 0.97F;
    private static final float BURN_SECONDS = 1.5F;
    private static final float HARDEN_PER_STRIKE = 0.055F;
    private static final float ANNEAL_SECONDS = 1.6F;

    private final BlockPos pos;
    private final int kind;
    private final Component heading;
    private final long minMillis;
    private final List<SmithStep> steps = new ArrayList<>();
    private final List<Integer> results = new ArrayList<>();
    private final boolean copper;
    private final float forge;
    private final boolean coal;
    private final int baseQuality;
    private final boolean showQuality;
    private int current;
    private int done;
    private int doneAverage;
    private long openedAt = -1;
    private long lastFrame = -1;
    private long bannerAt = -1;
    private int bannerScore;
    private boolean sent;
    private Component label = Component.empty();
    private int labelColor;
    private long labelAt = -1;
    // Жар
    private float heat;
    private float pump;
    private boolean inForge;
    private float burnTimer;
    private float annealTimer;
    private float harden;
    private int reheats;
    // Молотобоец
    private boolean striker;
    private double mouseX;
    private double mouseY;

    SmithWorkScreen(BlockPos pos, int kind, Component heading, long minTicks, int info, int extra, boolean showQuality) {
        super(heading);
        this.pos = pos;
        this.kind = kind;
        this.heading = heading;
        this.minMillis = minTicks * 50L + 300;
        this.forge = (info & SmithArea.HEAT_MASK) / 100F;
        this.coal = (info & SmithArea.COAL_BIT) != 0;
        this.baseQuality = extra & 0xF;
        this.copper = (extra & (1 << 4)) != 0;
        this.showQuality = showQuality;
        this.heat = limit();
    }

    /** Сколько этапов изделия уже сделано раньше (работу бросали) и их средний счёт: их в окне нет. */
    void resume(int count, int average) {
        this.done = count;
        this.doneAverage = average;
    }

    void add(SmithStep step) {
        steps.add(step);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- для этапов

    /** Жар заготовки 0–1; медь — всегда «можно». */
    float heat() {
        return copper ? 0.7F : heat;
    }

    boolean copper() {
        return copper;
    }

    boolean inForge() {
        return inForge;
    }

    /** Жар горна 0–1 (сведения с сервера). */
    float forgeHeat() {
        return forge;
    }

    boolean coal() {
        return coal;
    }

    /** Чем закаливать (чан): 1 вода, 2 рассол, 3 жир. */
    int liquid(int info) {
        return (info >> SmithArea.LIQUID_SHIFT) & 3;
    }

    /** Предел жара в горне: от его жара и мехов. */
    float limit() {
        return Math.min(1.0F, 0.3F + 0.75F * forge + pump + (coal ? 0.04F : 0));
    }

    /**
     * Удар по горячему. Медь — наклёп (переклепали — трещина); железо — ниже minHeat трескается. Каждый удар
     * чуть остужает. false — сейчас бить нельзя (заготовка в горне).
     */
    boolean strike(SmithStep step, float minHeat) {
        if (inForge) {
            return false;
        }
        if (copper) {
            harden += HARDEN_PER_STRIKE;
            if (harden >= 1) {
                step.penalize(8);
                show("cracked_hard", SmithStep.BAD);
                sound(SoundEvents.ANVIL_LAND, 1.6F);
                harden = 0.85F;
            }
            return true;
        }
        if (heat < minHeat - 0.04F) {
            int crack = Math.round((minHeat - heat) * 60);
            step.penalize(Math.max(3, crack));
            show("cracked_cold", SmithStep.BAD);
            sound(SoundEvents.ANVIL_LAND, 1.8F);
        }
        heat = Math.max(0, heat - 0.008F);
        return true;
    }

    /** Отпустить на волю: этап сам ведёт жар (закалка, отпуск). */
    void setHeat(float value) {
        heat = Math.max(0, Math.min(1, value));
    }

    void show(String key, int color, Object... args) {
        label = Component.translatable("gui.halgventmod.smith_work." + key, args);
        labelColor = color;
        labelAt = System.currentTimeMillis();
    }

    void sound(SoundEvent event, float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
        }
    }

    net.minecraft.client.gui.Font font() {
        return font;
    }

    /** Удар молотобойца пришёл с сервера. */
    void strikerBlow(int accuracy) {
        if (accuracy == com.nrdhvnstd.halgventmod.smithing.Striker.JOINED) {
            striker = true;
            show("striker_joined", SmithStep.GOOD);
            return;
        }
        if (accuracy == com.nrdhvnstd.halgventmod.smithing.Striker.LEFT) {
            striker = false;
            show("striker_left", SmithStep.TEXT_DIM);
            return;
        }
        if (!working() || inForge) {
            return;
        }
        SmithStep step = step();
        if (step.helped()) {
            step.helperBlow(accuracy, mouseX, mouseY);
        } else {
            show("striker_idle", SmithStep.TEXT_DIM);
        }
    }

    // ---------------------------------------------------------------- ход

    private SmithStep step() {
        return steps.get(Math.min(current, steps.size() - 1));
    }

    private boolean working() {
        return bannerAt < 0 && current < steps.size();
    }

    private boolean hotWork() {
        return working() && step().hot();
    }

    private void advance(long now) {
        float dt = lastFrame < 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        if (bannerAt >= 0) {
            if (now - bannerAt > BANNER_MS) {
                bannerAt = -1;
                current++;
                if (current < steps.size() && step().hot() && !copper) {
                    heat = Math.max(heat, limit() * 0.95F); // новый этап — из горна
                }
            }
        } else if (current < steps.size()) {
            tickHeat(dt);
            step().tick(dt, now);
            if (step().done()) {
                SmithStep done = step();
                bannerScore = done.score();
                results.addAll(done.result());
                bannerAt = now;
                inForge = false;
                sound(SoundEvents.PLAYER_LEVELUP, bannerScore >= 65 ? 1.6F : 1.1F);
            }
        }
        if (current >= steps.size()) {
            finish(now);
        }
    }

    private void tickHeat(float dt) {
        pump = Math.max(0, pump - PUMP_DECAY * dt);
        if (!hotWork()) {
            return;
        }
        if (copper) {
            if (inForge) {
                annealTimer += dt;
                if (annealTimer >= ANNEAL_SECONDS) {
                    harden = 0;
                    inForge = false;
                    annealTimer = 0;
                    show("annealed", SmithStep.GOOD);
                    sound(SoundEvents.FIRE_EXTINGUISH, 1.4F);
                }
            }
            return;
        }
        if (inForge) {
            float rate = HEAT_RATE * (coal ? 1.3F : 1.0F) * (0.6F + forge);
            heat = Math.min(limit(), heat + rate * dt);
            if (heat > BURN_AT) {
                burnTimer += dt;
                if (burnTimer >= BURN_SECONDS) {
                    burnTimer = 0;
                    step().penalize(10);
                    show("burnt", SmithStep.BAD);
                    sound(SoundEvents.FIRE_EXTINGUISH, 0.8F);
                }
            } else {
                burnTimer = Math.max(0, burnTimer - dt);
            }
        } else {
            heat = Math.max(0, heat - COOL * heat * dt);
        }
    }

    private void finish(long now) {
        if (sent || now - openedAt < minMillis) {
            return;
        }
        sent = true;
        PacketDistributor.sendToServer(new SmithResultPayload(pos, kind, true, List.copyOf(results)));
        super.onClose();
    }

    @Override
    public void onClose() {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new SmithResultPayload(pos, kind, false, List.copyOf(results))); // сделанное — сохранить
        }
        super.onClose();
    }

    private int expectedScore() {
        int total = done * doneAverage;
        int count = done;
        for (int i = 0; i < Math.min(current, steps.size()); i++) {
            total += steps.get(i).score();
            count++;
        }
        if (working()) {
            total += step().score();
            count++;
        }
        return count == 0 ? 50 : Math.round(total / (float) count);
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = System.currentTimeMillis();
        if (openedAt < 0) {
            openedAt = now;
        }
        advance(now);
        if (sent) {
            return;
        }
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        g.blitSprite(PANEL, x, y, WIDTH, HEIGHT);
        TextFit.drawCentered(g, font, heading, x + WIDTH / 2, y + 9, WIDTH - 20, SmithStep.TEXT);

        int shown = Math.min(current, steps.size() - 1);
        TextFit.draw(g, font, Component.translatable("gui.halgventmod.smith_work.stage", done + shown + 1, done + steps.size(),
                stepName(shown)), x + 14, y + 21, WIDTH - 28 - 12 * (done + steps.size()), SmithStep.TEXT_DIM);
        for (int i = 0; i < done; i++) {
            int dx = x + WIDTH - 14 - (done + steps.size() - i) * 12;
            g.fill(dx, y + 22, dx + 8, y + 30, 0xFF0E0A06);
            g.fill(dx + 1, y + 23, dx + 7, y + 29, 0xFF6E8A5A);
        }
        for (int i = 0; i < steps.size(); i++) {
            int dx = x + WIDTH - 14 - (steps.size() - i) * 12;
            int color = i < current ? (steps.get(i).score() >= 65 ? SmithStep.GOOD : steps.get(i).score() >= 45
                    ? SmithStep.TEXT : SmithStep.BAD) : i == current ? SmithStep.WARN : 0xFF4A3E30;
            g.fill(dx, y + 22, dx + 8, y + 30, 0xFF0E0A06);
            g.fill(dx + 1, y + 23, dx + 7, y + 29, color);
        }

        boolean gauge = hotWork() || bannerAt >= 0 && current < steps.size() && step().hot();
        int fx = x + 10;
        int fy = y + FIELD_TOP;
        int fw = WIDTH - 20 - (gauge ? GAUGE : 0);
        g.fill(fx - 1, fy - 1, fx + fw + 1, fy + FIELD_HEIGHT + 1, 0xFF0E0A06);
        g.fill(fx, fy, fx + fw, fy + FIELD_HEIGHT, 0xFF1C1612);
        g.enableScissor(fx, fy, fx + fw, fy + FIELD_HEIGHT);
        if (bannerAt >= 0 || current >= steps.size()) {
            SmithStep done = step();
            done.render(g, fx, fy, fw, FIELD_HEIGHT, -1, -1, now);
            g.disableScissor();
            g.fill(fx, fy + FIELD_HEIGHT / 2 - 14, fx + fw, fy + FIELD_HEIGHT / 2 + 14, 0xC0140E08);
            Component text = current >= steps.size() ? Component.translatable("gui.halgventmod.smith_work.finishing")
                    : Component.translatable("gui.halgventmod.smith_work.stage_done", bannerScore);
            TextFit.drawCentered(g, font, text, fx + fw / 2, fy + FIELD_HEIGHT / 2 - 4, fw - 20,
                    bannerScore >= 65 ? SmithStep.GOOD : bannerScore >= 45 ? SmithStep.TEXT : SmithStep.BAD);
        } else {
            step().render(g, fx, fy, fw, FIELD_HEIGHT, inForge ? -1 : mouseX, inForge ? -1 : mouseY, now);
            if (inForge) {
                g.fill(fx, fy, fx + fw, fy + FIELD_HEIGHT, 0x80200800);
                TextFit.drawCentered(g, font, Component.translatable(copper ? "gui.halgventmod.smith_work.annealing"
                        : "gui.halgventmod.smith_work.in_forge"), fx + fw / 2, fy + FIELD_HEIGHT / 2 - 4, fw - 20,
                        SmithStep.WARN);
            }
            g.disableScissor();
            TextFit.draw(g, font, step().status(), x + 14, y + HEIGHT - 50, WIDTH - 28, SmithStep.TEXT);
            Component hint = step().hint();
            if (step().hot()) {
                hint = Component.translatable("gui.halgventmod.smith_work.forge_hint", hint);
            }
            TextFit.draw(g, font, hint, x + 14, y + HEIGHT - 39, WIDTH - 28, SmithStep.TEXT_DIM);
        }
        if (gauge) {
            renderHeat(g, fx + fw + 8, fy, FIELD_HEIGHT);
        }
        if (showQuality) {
            int quality = Math.clamp(baseQuality + Smithing.workDelta(expectedScore()), 0, Smithing.MAX_QUALITY);
            TextFit.draw(g, font, Component.translatable("gui.halgventmod.smith_work.quality",
                    Component.translatable("gui.halgventmod.bench_work.grade_" + quality)), x + 14, y + HEIGHT - 26,
                    WIDTH / 2 - 14, quality > baseQuality ? SmithStep.GOOD : quality < baseQuality ? SmithStep.BAD
                            : SmithStep.TEXT_DIM);
        }
        if (striker) {
            TextFit.draw(g, font, Component.translatable("gui.halgventmod.smith_work.striker"), x + WIDTH / 2,
                    y + HEIGHT - 26, WIDTH / 2 - 14, SmithStep.GOOD);
        }
        if (labelAt >= 0 && now - labelAt < LABEL_MS) {
            TextFit.drawCentered(g, font, label, x + WIDTH / 2, fy + 3, WIDTH - 40, labelColor);
        }
    }

    private Component stepName(int index) {
        return steps.get(index).name();
    }

    /** Шкала жара (или наклёпа у меди) справа от поля. */
    private void renderHeat(GuiGraphics g, int x, int y, int h) {
        int w = 10;
        if (copper) {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0E0A06);
            g.fill(x, y, x + w, y + h, SmithStep.TRACK);
            int top = y + h - Math.round(h * Math.min(1, harden));
            g.fill(x, top, x + w, y + h, SmithStep.mix(0xFFB86A3A, SmithStep.BAD, harden));
            g.fill(x - 3, y + Math.round(h * 0.15F), x + w + 3, y + Math.round(h * 0.15F) + 1, SmithStep.BAD);
            return;
        }
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0E0A06);
        for (int i = 0; i < h; i++) {
            float v = 1 - i / (float) h;
            g.fill(x, y + i, x + w, y + i + 1, SmithStep.shade(SmithStep.glow(v), 0.55F));
        }
        float need = step().minHeat();
        int ny = y + h - Math.round(h * need);
        g.fill(x - 3, ny, x + w + 3, ny + 1, SmithStep.MARK);
        int ly = y + h - Math.round(h * limit());
        g.fill(x, ly, x + w, ly + 1, 0x80FFFFFF);
        int hy = y + h - Math.round(h * heat);
        g.fill(x - 4, hy - 2, x + w + 4, hy + 2, SmithStep.glow(heat));
        g.renderOutline(x - 4, hy - 2, w + 8, 4, 0xFF0E0A06);
    }

    // ---------------------------------------------------------------- управление

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hotWork() && inForge && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !copper) {
            pump = Math.min(PUMP_MAX, pump + PUMP);
            sound(SoundEvents.FIRECHARGE_USE, 0.5F + pump);
            return true;
        }
        if (working() && !inForge && step().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (working() && !inForge && step().mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (working() && !inForge) {
            step().mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (working()) {
            return true; // движение этап уже получил через mouseMoved
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (working() && !inForge && step().mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE && hotWork()) {
            inForge = !inForge;
            annealTimer = 0;
            if (inForge) {
                reheats++;
                sound(SoundEvents.FIRE_AMBIENT, 1.0F);
            } else {
                sound(SoundEvents.CHAIN_PLACE, 0.8F);
            }
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && working() && !inForge) {
            step().finishEarly();
            return true;
        }
        if (working() && !inForge && step().keyPressed(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
