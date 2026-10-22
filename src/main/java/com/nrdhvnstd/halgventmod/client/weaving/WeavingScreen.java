package com.nrdhvnstd.halgventmod.client.weaving;

import java.util.Random;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.network.WeavePassPayload;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.weaving.Fiber;
import com.nrdhvnstd.halgventmod.weaving.LoomBlockEntity;
import com.nrdhvnstd.halgventmod.weaving.Weaving;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Мини-игра «Зев и прибой» у ткацкого станка. Каждая прокидка утка — два такта:
 * <ol>
 *   <li><b>Бросок.</b> Галево качается, зев (просвет между слоями основы) то открывается, то смыкается.
 *       ЛКМ или Пробел — бросить челнок. Если зев сомкнётся, пока челнок в пути, — он цепляет основу:
 *       изъян, ряд не лёг, бросайте снова.</li>
 *   <li><b>Прибой.</b> Зажмите — бёрдо набирает силу, отпустите в зелёной зоне. Слабо — ряд рыхлый (в ткани
 *       просвет), сильно — уток перетянут и кромка сужается. И то и другое — изъян.</li>
 * </ol>
 * Ровные прокидки подряд разгоняют галево. Каждая прокидка уходит на сервер ({@link WeavePassPayload}); что уже
 * соткано, окно читает из блок-сущности станка. Esc — отойти: соткано остаётся на станке.
 */
public class WeavingScreen extends Screen {
    // Меняйте здесь
    private static final int FLY_MS = 420;              // полёт челнока через зев
    private static final int CHARGE_MS = 800;           // бёрдо от нуля до полной силы
    private static final float ZONE_LOW = 0.55F;        // зелёная зона прибоя
    private static final float ZONE_HIGH = 0.80F;
    private static final float SNAG_OPEN = 0.3F;        // зев уже этого — челнок цепляет основу
    private static final int PERIOD_MS = 1350;          // качание галева
    private static final int PERIOD_PER_COMBO_MS = 45;  // быстрее с каждой ровной прокидкой подряд
    private static final int MIN_PERIOD_MS = 760;
    private static final int LABEL_MS = 1100;

    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int WIDTH = 280;
    private static final int HEIGHT = 214;
    private static final int THREADS = 30;
    private static final int ROW = 4;
    private static final int TEXT = 0xFFE9DCB4;
    private static final int TEXT_DIM = 0xFFAA9C80;
    private static final int WOOD = 0xFF6E4626;
    private static final int WOOD_DARK = 0xFF3E2614;
    private static final int GAP = 0xFF1A120C;
    private static final int CLAY = 0xFF9A4E2E;
    private static final int GOOD = 0xFF8FD16A;
    private static final int BAD = 0xFFE0604A;

    private enum State { AIM, FLY, BEAT, CHARGE, WAIT }

    private final BlockPos pos;
    private final ItemStack shuttle = new ItemStack(ModItems.SHUTTLE.get());
    private final Random random = new Random();
    private State state = State.AIM;
    private long lastFrame = -1;
    private double phase;           // галево: 0 — зев сомкнут
    private int period = PERIOD_MS;
    private int side;               // где челнок: 0 — слева, 1 — справа
    private long flyStart;
    private long chargeStart;
    private float power;
    private int combo;
    private int sentAtPasses;
    private long waitSince;
    private Component label = Component.empty();
    private int labelColor;
    private long labelAt = -1;
    private long doneAt = -1;

    public WeavingScreen(BlockPos pos) {
        super(Component.translatable("gui.halgventmod.weaving.title"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new WeavingScreen(pos));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    private LoomBlockEntity loom() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(pos) instanceof LoomBlockEntity loom ? loom : null;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    // Основа: от x0 до x1
    private int warpLeft() {
        return left() + 34;
    }

    private int warpRight() {
        return left() + WIDTH - 64;
    }

    private int clothTop() {
        return top() + 30;
    }

    private float openness() {
        return (float) (0.5 - 0.5 * Math.cos(phase));
    }

    /** Нечем ткать: на эту прокидку нужен новый моток утка, а в инвентаре его нет. */
    private boolean noWeft(LoomBlockEntity loom) {
        return minecraft.player != null && !minecraft.player.getAbilities().instabuild && loom.needsWeft()
                && loom.warp() != null && minecraft.player.getInventory().countItem(loom.warp().yarn()) <= 0;
    }

    // ---------------------------------------------------------------- ход игры

    private void tick(long now, LoomBlockEntity loom) {
        if (lastFrame < 0) {
            lastFrame = now;
        }
        long dt = Math.min(100, now - lastFrame);
        lastFrame = now;
        phase += dt * Math.PI * 2 / period;

        if (state == State.FLY) {
            float t = (now - flyStart) / (float) FLY_MS;
            if (t > 0.08F && t < 0.92F && openness() < SNAG_OPEN) {
                // Зев сомкнулся на челноке: зацепил основу, челнок возвращается
                send(Weaving.PASS_SNAG);
                show("snag", BAD);
                combo = 0;
                state = State.AIM;
                sound(SoundEvents.WOOL_BREAK, 0.7F);
            } else if (t >= 1.0F) {
                side = 1 - side;
                state = State.BEAT;
                sound(SoundEvents.WOOD_HIT, 1.4F);
            }
        } else if (state == State.CHARGE) {
            power = (now - chargeStart) / (float) CHARGE_MS;
            if (power >= 1.15F) {
                beat(loom); // передержали — бёрдо само бьёт со всей силы
            }
        } else if (state == State.WAIT && (loom.passes() != sentAtPasses || now - waitSince > 900)) {
            state = State.AIM;
            period = Math.max(MIN_PERIOD_MS, PERIOD_MS - PERIOD_PER_COMBO_MS * combo) + random.nextInt(240);
        }
    }

    private void throwShuttle(LoomBlockEntity loom) {
        if (state != State.AIM || noWeft(loom) || loom.done()) {
            return;
        }
        state = State.FLY;
        flyStart = System.currentTimeMillis();
        sound(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.3F);
    }

    private void startCharge() {
        if (state == State.BEAT) {
            state = State.CHARGE;
            chargeStart = System.currentTimeMillis();
            power = 0;
        }
    }

    private void beat(LoomBlockEntity loom) {
        if (state != State.CHARGE) {
            return;
        }
        int result = power < ZONE_LOW ? Weaving.PASS_LOOSE : power > ZONE_HIGH ? Weaving.PASS_TIGHT : Weaving.PASS_EVEN;
        if (result == Weaving.PASS_EVEN) {
            combo++;
            show(combo > 1 ? "even_combo" : "even", GOOD);
            sound(SoundEvents.WOOD_PLACE, 1.0F);
        } else {
            combo = 0;
            show(result == Weaving.PASS_LOOSE ? "loose" : "tight", BAD);
            sound(SoundEvents.WOOD_PLACE, result == Weaving.PASS_LOOSE ? 1.6F : 0.6F);
        }
        sentAtPasses = loom.passes();
        waitSince = System.currentTimeMillis();
        send(result);
        state = State.WAIT;
    }

    private void send(int result) {
        PacketDistributor.sendToServer(new WeavePassPayload(pos, result));
    }

    private void show(String key, int color) {
        label = Component.translatable("gui.halgventmod.weaving." + key, combo);
        labelColor = color;
        labelAt = System.currentTimeMillis();
    }

    private void sound(net.minecraft.sounds.SoundEvent event, float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
        }
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        LoomBlockEntity loom = loom();
        if (loom == null || loom.warp() == null) {
            onClose();
            return;
        }
        long now = System.currentTimeMillis();
        tick(now, loom);
        Fiber fiber = loom.warp();
        int light = fiber == Fiber.LINEN ? 0xFFD8CCAA : 0xFF9C8E7C;
        int dark = fiber == Fiber.LINEN ? 0xFFB0A282 : 0xFF726656;

        int x = left();
        int y = top();
        g.blitSprite(PANEL, x, y, WIDTH, HEIGHT);
        TextFit.drawCentered(g, font, Component.translatable("gui.halgventmod.weaving.title_of",
                new ItemStack(fiber.cloth()).getHoverName()), x + WIDTH / 2, y + 9, WIDTH - 20, TEXT);

        int x0 = warpLeft();
        int x1 = warpRight();
        int bottom = y + HEIGHT - 52;
        int fell = clothTop() + loom.passes() * ROW;
        int shedY = fell + 16;
        int open = Math.round(openness() * 14);

        // Нити основы: от навоя до грузил
        float spacing = (x1 - x0) / (float) (THREADS - 1);
        for (int i = 0; i < THREADS; i++) {
            int tx = Math.round(x0 + i * spacing);
            g.fill(tx, clothTop(), tx + 1, bottom, i % 2 == 0 ? light : dark);
        }
        // Грузила: по одному на пучок нитей, чуть качаются
        for (int i = 0; i < 8; i++) {
            int wx = Math.round(x0 + (i + 0.5F) * (x1 - x0) / 8F + Mth.sin((float) (phase * 0.5 + i)) * 1.5F);
            int wy = bottom + (i % 2) * 5;
            g.fill(wx - 4, wy, wx + 4, wy + 7, CLAY);
            g.fill(wx - 1, wy + 2, wx + 1, wy + 4, GAP);
        }

        // Соткано: ряды утка сверху вниз; рыхлые — с просветами, перетянутые сужают кромку ниже
        int inset = 0;
        for (int row = 0; row < loom.passes(); row++) {
            if (loom.tightRow(row)) {
                inset += 2;
            }
            int ry = clothTop() + row * ROW;
            int rx0 = x0 - 2 + inset;
            int rx1 = x1 + 3 - inset;
            boolean loose = loom.looseRow(row);
            for (int px = rx0; px < rx1; px += 2) {
                int color = ((px / 2 + row) % 2 == 0) ? light : dark;
                if (loose && (px / 2) % 3 == 0) {
                    continue; // просвет
                }
                g.fill(px, ry, px + 2, ry + ROW - (loose ? 1 : 0), color);
            }
            if (loose || loom.tightRow(row)) {
                g.fill(rx1 + 2, ry + 1, rx1 + 4, ry + ROW - 1, BAD);
            }
        }

        // Навой и галево
        g.fill(x0 - 10, clothTop() - 7, x1 + 11, clothTop() - 1, WOOD);
        g.fill(x0 - 10, clothTop() - 1, x1 + 11, clothTop(), WOOD_DARK);
        boolean weaving = !loom.done();
        if (weaving) {
            // Зев: тёмный просвет между слоями основы, над ним — галево, поднимается вместе с зевом
            g.fill(x0 - 2, shedY - open / 2, x1 + 3, shedY + (open + 1) / 2, GAP);
            for (int i = 1; i < THREADS; i += 2) {
                int tx = Math.round(x0 + i * spacing);
                g.fill(tx, shedY - open / 2 - 3, tx + 1, shedY - open / 2, light);
            }
            int rodY = shedY - open / 2 - 6;
            g.fill(x0 - 12, rodY, x1 + 13, rodY + 3, WOOD);
            g.fill(x0 - 12, rodY + 3, x1 + 13, rodY + 4, WOOD_DARK);

            // Бёрдо: во время прибоя поднимается к опушке
            if (state == State.BEAT || state == State.CHARGE) {
                int by = shedY + 10 - Math.round(Math.min(1.0F, power) * (shedY + 10 - fell - 1));
                g.fill(x0 - 6, by, x1 + 7, by + 2, WOOD);
            }

            // Челнок
            float t = state == State.FLY ? Math.min(1.0F, (System.currentTimeMillis() - flyStart) / (float) FLY_MS) : 0;
            int from = side == 0 ? x0 - 18 : x1 + 18;
            int to = side == 0 ? x1 + 18 : x0 - 18;
            int sx = Math.round(from + (to - from) * t);
            g.renderItem(shuttle, sx - 8, shedY - 8);
        }

        // Сила прибоя: столбик с зелёной зоной
        int gx = x + WIDTH - 40;
        int gy0 = clothTop();
        int gh = 90;
        g.fill(gx - 1, gy0 - 1, gx + 11, gy0 + gh + 1, WOOD_DARK);
        g.fill(gx, gy0, gx + 10, gy0 + gh, 0xFF2A2018);
        g.fill(gx, gy0 + Math.round(gh * (1 - ZONE_HIGH)), gx + 10, gy0 + Math.round(gh * (1 - ZONE_LOW)), 0xFF3E7A2E);
        if (state == State.CHARGE || state == State.BEAT) {
            int py = gy0 + Math.round(gh * (1 - Math.min(1.0F, power)));
            g.fill(gx - 3, py - 1, gx + 13, py + 1, TEXT);
        }

        // Подписи
        int infoY = y + HEIGHT - 38;
        TextFit.draw(g, font, Component.translatable("gui.halgventmod.weaving.progress", loom.passes(), Weaving.PASSES,
                loom.flaws()), x + 14, infoY, WIDTH - 28, TEXT);
        Component hint;
        int hintColor = TEXT_DIM;
        if (loom.done()) {
            hint = Component.translatable("gui.halgventmod.weaving.done");
            hintColor = GOOD;
            if (doneAt < 0) {
                doneAt = now;
                sound(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0F);
            }
        } else if (noWeft(loom) && state == State.AIM) {
            hint = Component.translatable("gui.halgventmod.weaving.no_weft", new ItemStack(fiber.yarn()).getHoverName());
            hintColor = BAD;
        } else {
            hint = Component.translatable(state == State.BEAT || state == State.CHARGE
                    ? "gui.halgventmod.weaving.hint_beat" : "gui.halgventmod.weaving.hint_throw");
        }
        TextFit.draw(g, font, hint, x + 14, infoY + 12, WIDTH - 28, hintColor);
        if (labelAt >= 0 && now - labelAt < LABEL_MS) {
            TextFit.drawCentered(g, font, label, (x0 + x1) / 2, bottom + 14, x1 - x0 + 20, labelColor);
        }
        if (doneAt >= 0 && now - doneAt > 1200) {
            onClose();
        }
    }

    // ---------------------------------------------------------------- управление: ЛКМ или Пробел

    private void press() {
        LoomBlockEntity loom = loom();
        if (loom == null) {
            return;
        }
        if (state == State.AIM) {
            throwShuttle(loom);
        } else if (state == State.BEAT) {
            startCharge();
        }
    }

    private void release() {
        LoomBlockEntity loom = loom();
        if (loom != null && state == State.CHARGE) {
            beat(loom);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            press();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            release();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            press();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            release();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
}
