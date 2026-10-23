package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;
import com.nrdhvnstd.halgventmod.carpentry.Carpentry;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.network.BenchWorkResultPayload;

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
 * Работа на верстаке плотника: этапы изделия по порядку (BenchProducts), у каждого своя мини-игра ({@link WorkStep}):
 * <ul>
 *   <li>{@link PlaneStep} — строгание рубанком до разметки;</li>
 *   <li>{@link ChiselStep} — выборка гнезда долотом, лёгкими и сильными ударами;</li>
 *   <li>{@link DrillStep} — сверление коловоротом: кругами мыши, в меру, к концу тише;</li>
 *   <li>{@link WhittleStep} — обстругивание ножом по профилю;</li>
 *   <li>{@link NailStep} — сборка молотком: бить, когда гвоздь стоит прямо, и в меру.</li>
 * </ul>
 * Закончен этап — его счёт (0–100) в копилку, итог на секунду и следующий. После последнего счёт всех этапов уходит
 * на сервер ({@link BenchWorkResultPayload}); Esc — бросить, материалы остаются на верстаке.
 */
public class BenchWorkScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int WIDTH = 280;
    private static final int HEIGHT = 214;
    private static final int FIELD_TOP = 34;
    private static final int FIELD_HEIGHT = 128;
    private static final int BANNER_MS = 1100;
    private static final int LABEL_MS = 1100;

    private final BlockPos pos;
    private final BenchProducts.Product product;
    private final List<WorkStep> steps = new ArrayList<>();
    private final List<Integer> scores = new ArrayList<>();
    private int current;
    private long openedAt = -1;
    private long bannerAt = -1;
    private int bannerScore;
    private boolean sent;
    private Component label = Component.empty();
    private int labelColor;
    private long labelAt = -1;

    public BenchWorkScreen(BlockPos pos, int product, int seed) {
        super(Component.translatable("gui.halgventmod.bench_work.title"));
        this.pos = pos;
        this.product = BenchProducts.get(product);
        Random random = new Random(seed);
        for (BenchProducts.Step step : this.product.steps()) {
            steps.add(switch (step.operation()) {
                case PLANE -> new PlaneStep(this, this.product, step, random);
                case CHISEL -> new ChiselStep(this, this.product, step, random);
                case DRILL -> new DrillStep(this, this.product, step, random);
                case WHITTLE -> new WhittleStep(this, this.product, step, random);
                case NAIL -> new NailStep(this, this.product, step, random);
            });
        }
    }

    public static void open(BlockPos pos, int product, int seed) {
        Minecraft.getInstance().setScreen(new BenchWorkScreen(pos, product, seed));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private WorkStep step() {
        return steps.get(Math.min(current, steps.size() - 1));
    }

    /** Работает ли сейчас этап (не итог между этапами и не конец). */
    private boolean working() {
        return bannerAt < 0 && current < steps.size();
    }

    // ---------------------------------------------------------------- для этапов

    void show(String key, int color, Object... args) {
        label = Component.translatable("gui.halgventmod.bench_work." + key, args);
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

    // ---------------------------------------------------------------- ход

    private void advance(long now) {
        if (bannerAt >= 0) {
            if (now - bannerAt > BANNER_MS) {
                bannerAt = -1;
                current++;
                if (current >= steps.size()) {
                    finish(now);
                }
            }
            return;
        }
        if (current < steps.size() && step().done()) {
            bannerScore = step().score();
            scores.add(bannerScore);
            bannerAt = now;
            sound(SoundEvents.PLAYER_LEVELUP, bannerScore >= 65 ? 1.6F : 1.1F);
        }
        if (current >= steps.size()) {
            finish(now);
        }
    }

    private void finish(long now) {
        if (sent) {
            return;
        }
        // Сервер ждёт самое меньшее столько тиков работы — не раньше
        if (now - openedAt < product.minTicks() * 50L + 300) {
            return;
        }
        sent = true;
        PacketDistributor.sendToServer(new BenchWorkResultPayload(pos, true, List.copyOf(scores)));
        super.onClose();
    }

    @Override
    public void onClose() {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new BenchWorkResultPayload(pos, false, List.of()));
        }
        super.onClose();
    }

    /** Каким выйдет изделие, если дальше всё пойдёт как сейчас. */
    private int expectedScore() {
        int total = 0;
        for (int score : scores) {
            total += score;
        }
        int count = scores.size();
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
        int x = left();
        int y = top();
        g.blitSprite(PANEL, x, y, WIDTH, HEIGHT);
        TextFit.drawCentered(g, font, Component.translatable("gui.halgventmod.bench_work.title_of",
                product.result().getHoverName()), x + WIDTH / 2, y + 9, WIDTH - 20, WorkStep.TEXT);

        // Этапы: кружки сверху, нынешний — светлый
        int shown = Math.min(current, steps.size() - 1);
        BenchProducts.Step step = product.steps().get(shown);
        TextFit.draw(g, font, Component.translatable("gui.halgventmod.bench_work.stage", shown + 1, steps.size(),
                Component.translatable(step.operation().langKey())), x + 14, y + 21, WIDTH - 28 - 12 * steps.size(),
                WorkStep.TEXT_DIM);
        for (int i = 0; i < steps.size(); i++) {
            int dx = x + WIDTH - 14 - (steps.size() - i) * 12;
            int color = i < scores.size() ? (scores.get(i) >= 65 ? WorkStep.GOOD : scores.get(i) >= 45 ? WorkStep.TEXT
                    : WorkStep.BAD) : i == current ? WorkStep.WARN : 0xFF4A3E30;
            g.fill(dx, y + 22, dx + 8, y + 30, 0xFF0E0A06);
            g.fill(dx + 1, y + 23, dx + 7, y + 29, color);
        }

        int fx = x + 10;
        int fy = y + FIELD_TOP;
        int fw = WIDTH - 20;
        if (bannerAt >= 0 || current >= steps.size()) {
            // Итог этапа или ожидание конца
            WorkStep done = steps.get(Math.min(current, steps.size() - 1));
            done.render(g, fx, fy, fw, FIELD_HEIGHT, -1, -1, now);
            g.fill(fx, fy + FIELD_HEIGHT / 2 - 14, fx + fw, fy + FIELD_HEIGHT / 2 + 14, 0xC0140E08);
            Component text = current >= steps.size() ? Component.translatable("gui.halgventmod.bench_work.finishing")
                    : Component.translatable("gui.halgventmod.bench_work.stage_done", bannerScore);
            TextFit.drawCentered(g, font, text, fx + fw / 2, fy + FIELD_HEIGHT / 2 - 4, fw - 20,
                    bannerScore >= 65 ? WorkStep.GOOD : bannerScore >= 45 ? WorkStep.TEXT : WorkStep.BAD);
        } else {
            step().render(g, fx, fy, fw, FIELD_HEIGHT, mouseX, mouseY, now);
            TextFit.draw(g, font, step().status(), x + 14, y + HEIGHT - 46, WIDTH - 28, WorkStep.TEXT);
            TextFit.draw(g, font, step().hint(), x + 14, y + HEIGHT - 35, WIDTH - 28, WorkStep.TEXT_DIM);
        }
        int quality = Math.clamp(Carpentry.BASE_QUALITY + Carpentry.workDelta(expectedScore()), 0, Carpentry.MAX_QUALITY);
        TextFit.draw(g, font, Component.translatable("gui.halgventmod.bench_work.quality",
                Component.translatable("gui.halgventmod.bench_work.grade_" + quality)), x + 14, y + HEIGHT - 22, WIDTH - 28,
                quality > Carpentry.BASE_QUALITY ? WorkStep.GOOD : quality < Carpentry.BASE_QUALITY ? WorkStep.BAD : WorkStep.TEXT_DIM);
        if (labelAt >= 0 && now - labelAt < LABEL_MS) {
            TextFit.drawCentered(g, font, label, x + WIDTH / 2, fy + 2, WIDTH - 40, labelColor);
        }
    }

    // ---------------------------------------------------------------- управление — этапу

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (working() && step().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (working() && step().mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (working()) {
            step().mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (working()) {
            return true; // движение этап уже получил через mouseMoved — Minecraft зовёт его и при зажатой кнопке
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (working() && step().mouseScrolled(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && working()) {
            step().finishEarly();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
