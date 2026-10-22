package com.nrdhvnstd.halgventmod.client.pottery;

import java.util.BitSet;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.network.WheelResultPayload;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.pottery.PotteryWheelBlockEntity;
import com.nrdhvnstd.halgventmod.pottery.Vessel;
import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.VesselGeometry;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Гончарный круг: форма по вокселям.
 * <pre>
 *  [формы]   [разрез сосуда 16×16]   [сосуд объёмом, вращается]
 *            [маховик] Глина · Обрезки · Сходство · Качество
 *            [Детали]  [Смять]  [Готово]
 * </pre>
 * <b>Тело</b> лепится, пока круг крутится: в разрезе каждая клетка — кольцо вокселей вокруг оси. ЛКМ — срезать
 * (глина в обрезки), ПКМ — налепить из обрезков (неровно), Shift + ЛКМ — вытянуть: клетка переезжает на верх
 * своей стенки. Пробел подталкивает маховик, он сам замедляется. В зелёной зоне — чисто; медленно — глина
 * мнётся; быстро — нависающее обваливается; совсем быстро — ком срывает с круга. Клетка держится, если под ней
 * (на слой ниже) есть глина не дальше одного кольца.
 * <p><b>Детали</b> (ручки, носики) — на остановленном круге: срез сосуда по глубине (колесо мыши), каждый воксель
 * по одному. Деталь должна касаться тела или другой детали, ручка — в двух местах.
 * <p>«Готово» — форма уходит на сервер ({@link WheelResultPayload}); он проверяет её заново. Esc — встать из-за
 * круга, глина остаётся на нём.
 */
public class WheelScreen extends Screen {
    // Меняйте здесь
    private static final float DECAY = 0.12F;        // маховик теряет в секунду
    private static final float KICK = 0.18F;         // толчок ногой
    private static final float SPINNING = 0.05F;     // ниже — круг стоит
    private static final float LOW = 0.35F;          // зелёная зона
    private static final float HIGH = 0.75F;
    private static final float WRENCH = 0.95F;       // ком срывает
    private static final float SMUDGE_CHANCE = 0.6F; // на медленном круге касание мнёт соседнюю клетку
    private static final float VIEW_TILT = 35.0F;    // сосуд в окне — сверху под этим углом

    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final ResourceLocation CELL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/cell");
    private static final int PAD = 8;
    private static final int FORM_W = 92;
    private static final int FORM_H = 22;
    private static final int GRID_CELL = 9;
    private static final int GRID = VesselGeometry.SIZE * GRID_CELL;   // 144
    private static final int VIEW = 132;
    private static final int TOP = 24;
    private static final int GRID_X = PAD + FORM_W + PAD;
    private static final int VIEW_X = GRID_X + GRID + PAD;
    private static final int WIDTH = VIEW_X + VIEW + PAD;
    private static final int HEIGHT = TOP + GRID + 72;

    private static final int TEXT = 0xFFE9DCB4;
    private static final int TEXT_DIM = 0xFFAA9C80;
    private static final int GOOD = 0xFF8FD16A;
    private static final int BAD = 0xFFFF6A5A;
    private static final int BACK = 0xFF2A2420;
    private static final int CLAY = 0xFFB09A80;
    private static final int CLAY_DARK = 0xFF8C7660;
    private static final int DETAIL = 0xFFC8A070;
    private static final int GHOST = 0xFFFFD27F;

    private final BlockPos pos;
    private final int lumps;
    private final int clayQuality;
    private final Random random = new Random();

    private VesselForm form;
    private int budget;
    private int[] profile = new int[VesselGeometry.LAYERS];
    private final BitSet details = new BitSet(VesselGeometry.VOXELS);
    private int scraps;
    private float speed;
    private int smudges;
    private int collapses;
    private int added;
    private boolean detailMode;
    private int sliceZ = VesselGeometry.SIZE / 2;
    private float angle;
    private float dragAngle;
    private long lastFrame = -1;
    private Component message = Component.empty();
    private long messageUntil;
    private int lastCell = -1;
    private double match;
    private String problem;
    private boolean sent;
    private Button modeButton;
    private Button doneButton;

    public WheelScreen(BlockPos pos, int lumps, int quality) {
        super(Component.translatable("gui.halgventmod.wheel.title"));
        this.pos = pos;
        this.lumps = lumps;
        this.clayQuality = quality;
        VesselForm first = VesselForm.CUP;
        for (VesselForm f : VesselForm.values()) {
            if (f.lumps() <= lumps) {
                first = f;
                break;
            }
        }
        choose(first);
    }

    public static void open(BlockPos pos, int lumps, int quality) {
        Minecraft.getInstance().setScreen(new WheelScreen(pos, lumps, quality));
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

    @Override
    protected void init() {
        int y = top() + TOP + GRID + 37;
        int x = left() + GRID_X;
        int w = (WIDTH - GRID_X - PAD - 8) / 3;
        modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> toggleMode())
                .bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.wheel.reset"), b -> {
            choose(form);
            say("gui.halgventmod.wheel.reset_done");
        }).bounds(x + w + 4, y, w, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.wheel.done"), b -> finish())
                .bounds(x + 2 * (w + 4), y, w, 20).build());
        refresh();
    }

    private Component modeLabel() {
        return Component.translatable(detailMode ? "gui.halgventmod.wheel.mode_body" : "gui.halgventmod.wheel.mode_details");
    }

    // ---------------------------------------------------------------- состояние

    /** Выбрать форму: на круг — свежий ком с глиной под неё. */
    private void choose(VesselForm chosen) {
        form = chosen;
        budget = VesselGeometry.budget(form);
        profile = VesselGeometry.lump(budget);
        details.clear();
        scraps = budget - VesselGeometry.bodyVolume(profile);
        smudges = collapses = added = 0;
        detailMode = false;
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
        refresh();
    }

    private void refresh() {
        int[] d = details.stream().toArray();
        match = VesselGeometry.match(form, profile, d);
        problem = VesselGeometry.problem(form, profile, d, budget);
        if (doneButton != null) {
            doneButton.active = form.lumps() <= lumps && problem == null && match >= VesselGeometry.MIN_MATCH;
        }
    }

    private int uneven() {
        return smudges + collapses + added / 2;
    }

    private int forecast() {
        int bonus = match >= PotteryWheelBlockEntity.MATCH_FINE ? 1 : match >= PotteryWheelBlockEntity.MATCH_GOOD ? 0
                : match >= PotteryWheelBlockEntity.MATCH_FAIR ? -1 : -2;
        int penalty = Math.min(PotteryWheelBlockEntity.MAX_UNEVEN_PENALTY, uneven() / PotteryWheelBlockEntity.UNEVEN_PER_LEVEL);
        return Mth.clamp(clayQuality + bonus - penalty, 0, 5);
    }

    private void say(String key, Object... args) {
        message = Component.translatable(key, args);
        messageUntil = System.currentTimeMillis() + 2500;
    }

    private void play(net.minecraft.sounds.SoundEvent sound, float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6F));
        }
    }

    private void toggleMode() {
        detailMode = !detailMode;
        if (detailMode) {
            speed = 0; // детали лепят на остановленном круге
        }
        modeButton.setMessage(modeLabel());
    }

    // ---------------------------------------------------------------- лепка тела

    private boolean spinning() {
        return speed > SPINNING;
    }

    private void editBody(int r, int y, int button, boolean shift) {
        if (!spinning()) {
            say("gui.halgventmod.wheel.spin_first");
            return;
        }
        boolean changed;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shift) {
            changed = pull(r, y);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            changed = cut(r, y);
        } else {
            changed = add(r, y);
        }
        if (!changed) {
            return;
        }
        afterTouch(r, y);
        refresh();
    }

    private boolean cut(int r, int y) {
        if (!VesselGeometry.cell(profile, r, y)) {
            return false;
        }
        VesselGeometry.setCell(profile, r, y, false);
        scraps += VesselGeometry.RING_VOXELS[r];
        play(SoundEvents.MUD_BREAK, 1.4F);
        return true;
    }

    private boolean add(int r, int y) {
        if (VesselGeometry.cell(profile, r, y)) {
            return false;
        }
        if (scraps < VesselGeometry.RING_VOXELS[r]) {
            say("gui.halgventmod.wheel.no_scraps");
            return false;
        }
        if (!VesselGeometry.supported(profile, r, y, 1)) {
            say("gui.halgventmod.wheel.no_support");
            return false;
        }
        VesselGeometry.setCell(profile, r, y, true);
        scraps -= VesselGeometry.RING_VOXELS[r];
        added++;
        play(SoundEvents.MUD_PLACE, 1.2F);
        return true;
    }

    /** Вытянуть: клетка переезжает на верх своей стенки (то же кольцо) — объём тот же, неровности нет. */
    private boolean pull(int r, int y) {
        if (!VesselGeometry.cell(profile, r, y)) {
            return false;
        }
        VesselGeometry.setCell(profile, r, y, false);
        int top = -1;
        for (int yy = VesselGeometry.LAYERS - 1; yy >= 0; yy--) {
            if (VesselGeometry.cell(profile, r, yy)) {
                top = yy;
                break;
            }
        }
        int target = top + 1;
        if (target >= VesselGeometry.LAYERS || target == y || !VesselGeometry.supported(profile, r, target, 1)) {
            VesselGeometry.setCell(profile, r, y, true);
            say("gui.halgventmod.wheel.cant_pull");
            return false;
        }
        VesselGeometry.setCell(profile, r, target, true);
        play(SoundEvents.MUD_STEP, 1.5F);
        return true;
    }

    /** Что делает с глиной скорость круга после касания. */
    private void afterTouch(int r, int y) {
        if (speed < LOW && random.nextFloat() < SMUDGE_CHANCE) {
            int[][] around = {{r - 1, y}, {r + 1, y}, {r, y - 1}, {r, y + 1}};
            int[] pick = around[random.nextInt(around.length)];
            if (pick[1] > 0 && VesselGeometry.cell(profile, pick[0], pick[1])) {
                VesselGeometry.setCell(profile, pick[0], pick[1], false);
                scraps += VesselGeometry.RING_VOXELS[pick[0]];
            }
            smudges++;
            say("gui.halgventmod.wheel.smudged");
        }
        if (speed > WRENCH) {
            int[] fresh = VesselGeometry.lump(budget);
            profile = fresh;
            details.clear();
            scraps = budget - VesselGeometry.bodyVolume(profile);
            collapses += 3;
            say("gui.halgventmod.wheel.wrenched");
            play(SoundEvents.MUD_BRICKS_BREAK, 0.7F);
            return;
        }
        int fallen = VesselGeometry.collapse(profile, speed > HIGH ? 0 : 1);
        if (fallen > 0) {
            scraps += fallen;
            collapses++;
            say(speed > HIGH ? "gui.halgventmod.wheel.flung" : "gui.halgventmod.wheel.collapsed");
            play(SoundEvents.MUD_BRICKS_BREAK, 1.0F);
        }
        dropLooseDetails();
    }

    private void dropLooseDetails() {
        int before = details.cardinality();
        int[] kept = VesselGeometry.attachedDetails(profile, details.stream().toArray());
        details.clear();
        for (int i : kept) {
            details.set(i);
        }
        scraps += before - details.cardinality();
    }

    // ---------------------------------------------------------------- детали

    private void editDetail(int x, int y, int button) {
        int i = VesselGeometry.index(x, y, sliceZ);
        if (VesselGeometry.body(profile, x, y, sliceZ)) {
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!details.get(i)) {
                return;
            }
            details.clear(i);
            scraps++;
            dropLooseDetails();
            play(SoundEvents.MUD_BREAK, 1.6F);
        } else {
            if (details.get(i)) {
                return;
            }
            if (scraps < 1) {
                say("gui.halgventmod.wheel.no_scraps");
                return;
            }
            boolean holds = VesselGeometry.touchesBody(profile, i);
            for (int n : VesselGeometry.neighbours(i)) {
                holds |= n >= 0 && details.get(n);
            }
            if (!holds) {
                say("gui.halgventmod.wheel.detail_loose");
                return;
            }
            details.set(i);
            scraps--;
            play(SoundEvents.MUD_PLACE, 1.6F);
        }
        refresh();
    }

    // ---------------------------------------------------------------- ввод

    /** Клетка сетки под мышью: {столбец, слой} или null. */
    private int[] cellAt(double mouseX, double mouseY) {
        int gx = (int) Math.floor((mouseX - left() - GRID_X) / GRID_CELL);
        int gy = (int) Math.floor((mouseY - top() - TOP) / GRID_CELL);
        if (gx < 0 || gx >= VesselGeometry.SIZE || gy < 0 || gy >= VesselGeometry.SIZE) {
            return null;
        }
        return new int[] {gx, VesselGeometry.SIZE - 1 - gy};
    }

    /** Столбец разреза → кольцо: разрез через ось, кольца расходятся от середины в обе стороны. */
    private static int ringOf(int column) {
        return column < VesselGeometry.RINGS ? VesselGeometry.RINGS - 1 - column : column - VesselGeometry.RINGS;
    }

    private void touch(double mouseX, double mouseY, int button) {
        int[] cell = cellAt(mouseX, mouseY);
        if (cell == null) {
            return;
        }
        int key = cell[0] * 64 + cell[1];
        if (key == lastCell) {
            return; // протяжка: в одну клетку — один раз
        }
        lastCell = key;
        if (detailMode) {
            editDetail(cell[0], cell[1], button);
        } else {
            editBody(ringOf(cell[0]), cell[1], button, hasShiftDown());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int formIndex = formAt(mouseX, mouseY);
        if (formIndex >= 0 && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            VesselForm chosen = VesselForm.values()[formIndex];
            if (chosen.lumps() > lumps) {
                say("gui.halgventmod.wheel.need_lumps", chosen.lumps());
            } else if (chosen != form) {
                choose(chosen);
            }
            return true;
        }
        if (cellAt(mouseX, mouseY) != null && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            lastCell = -1;
            touch(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (cellAt(mouseX, mouseY) != null) {
            touch(mouseX, mouseY, button);
            return true;
        }
        if (inView(mouseX, mouseY)) {
            dragAngle += (float) dragX * 2.0F;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        lastCell = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detailMode && scrollY != 0) {
            sliceZ = Mth.clamp(sliceZ + (scrollY > 0 ? 1 : -1), 0, VesselGeometry.SIZE - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            if (detailMode) {
                say("gui.halgventmod.wheel.stopped_for_details");
            } else {
                speed = Math.min(1.0F, speed + KICK);
                play(SoundEvents.WOOD_HIT, 0.7F + speed * 0.6F);
            }
            return true;
        }
        if (detailMode && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            sliceZ = Mth.clamp(sliceZ + (keyCode == GLFW.GLFW_KEY_UP ? 1 : -1), 0, VesselGeometry.SIZE - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int formAt(double mouseX, double mouseY) {
        double fx = mouseX - left() - PAD;
        double fy = mouseY - top() - TOP;
        if (fx < 0 || fx >= FORM_W || fy < 0) {
            return -1;
        }
        int i = (int) fy / FORM_H;
        return i < VesselForm.values().length ? i : -1;
    }

    private boolean inView(double mouseX, double mouseY) {
        double vx = mouseX - left() - VIEW_X;
        double vy = mouseY - top() - TOP;
        return vx >= 0 && vx < VIEW && vy >= 0 && vy < VIEW;
    }

    private void finish() {
        if (sent || problem != null || match < VesselGeometry.MIN_MATCH) {
            return;
        }
        sent = true;
        PacketDistributor.sendToServer(new WheelResultPayload(pos, form.ordinal(), profile.clone(),
                details.stream().toArray(), uneven()));
        onClose();
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blitSprite(PANEL, left(), top(), WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tick(System.currentTimeMillis());
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        g.drawCenteredString(font, title, x + WIDTH / 2, y + 8, TEXT);
        renderForms(g, x, y, mouseX, mouseY);
        if (detailMode) {
            renderSlice(g, x + GRID_X, y + TOP);
        } else {
            renderSection(g, x + GRID_X, y + TOP);
        }
        renderView(g, x + VIEW_X, y + TOP);
        renderSpeed(g, x + GRID_X, y + TOP + GRID + 4);
        renderStats(g, x, y);
    }

    private void tick(long now) {
        if (lastFrame < 0) {
            lastFrame = now;
        }
        float seconds = Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        speed = Math.max(0, speed - DECAY * seconds);
        angle += speed * 540.0F * seconds;
    }

    private void renderForms(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        VesselForm[] forms = VesselForm.values();
        for (int i = 0; i < forms.length; i++) {
            VesselForm f = forms[i];
            int fx = x + PAD;
            int fy = y + TOP + i * FORM_H;
            g.blitSprite(CELL, fx, fy, FORM_W, FORM_H - 2);
            boolean enough = f.lumps() <= lumps;
            if (f == form) {
                g.renderOutline(fx, fy, FORM_W, FORM_H - 2, GHOST);
            } else if (formAt(mouseX, mouseY) == i) {
                g.fill(fx + 1, fy + 1, fx + FORM_W - 1, fy + FORM_H - 3, 0x30FFFFFF);
            }
            ItemStack icon = new ItemStack(Pottery.wareItem(f));
            icon.set(ModComponents.VESSEL.get(), Vessel.template(f));
            g.renderItem(icon, fx + 2, fy + 2);
            g.drawString(font, Component.translatable(f.langKey()), fx + 21, fy + 2, enough ? TEXT : TEXT_DIM, false);
            g.drawString(font, Component.translatable("gui.halgventmod.wheel.lumps", f.lumps()), fx + 21, fy + 11,
                    enough ? TEXT_DIM : BAD, false);
        }
        g.drawString(font, Component.translatable("gui.halgventmod.wheel.on_wheel", lumps), x + PAD,
                y + TOP + forms.length * FORM_H + 4, TEXT_DIM, false);
    }

    /** Разрез через ось: глина, образец (рамкой) и опора. */
    private void renderSection(GuiGraphics g, int gx, int gy) {
        g.fill(gx - 1, gy - 1, gx + GRID + 1, gy + GRID + 1, 0xFF000000);
        g.fill(gx, gy, gx + GRID, gy + GRID, BACK);
        int[] template = VesselGeometry.template(form).profile();
        for (int col = 0; col < VesselGeometry.SIZE; col++) {
            int r = ringOf(col);
            for (int y = 0; y < VesselGeometry.LAYERS; y++) {
                int cx = gx + col * GRID_CELL;
                int cy = gy + (VesselGeometry.LAYERS - 1 - y) * GRID_CELL;
                if (VesselGeometry.cell(profile, r, y)) {
                    g.fill(cx, cy, cx + GRID_CELL, cy + GRID_CELL, CLAY_DARK);
                    g.fill(cx, cy, cx + GRID_CELL - 1, cy + GRID_CELL - 1, CLAY);
                }
                if (VesselGeometry.cell(template, r, y)) {
                    g.renderOutline(cx + 1, cy + 1, GRID_CELL - 2, GRID_CELL - 2, GHOST);
                }
            }
        }
        // Ось круга
        g.fill(gx + GRID / 2, gy, gx + GRID / 2 + 1, gy + GRID, 0x60FFFFFF);
        g.drawString(font, Component.translatable("gui.halgventmod.wheel.section"), gx, gy - 10, TEXT_DIM, false);
    }

    /** Срез по глубине для деталей: тело серым, детали глиной, образец деталей — рамкой. */
    private void renderSlice(GuiGraphics g, int gx, int gy) {
        g.fill(gx - 1, gy - 1, gx + GRID + 1, gy + GRID + 1, 0xFF000000);
        g.fill(gx, gy, gx + GRID, gy + GRID, BACK);
        BitSet ghost = new BitSet(VesselGeometry.VOXELS);
        for (int i : VesselGeometry.template(form).details()) {
            ghost.set(i);
        }
        for (int x = 0; x < VesselGeometry.SIZE; x++) {
            for (int y = 0; y < VesselGeometry.SIZE; y++) {
                int cx = gx + x * GRID_CELL;
                int cy = gy + (VesselGeometry.SIZE - 1 - y) * GRID_CELL;
                int i = VesselGeometry.index(x, y, sliceZ);
                if (VesselGeometry.body(profile, x, y, sliceZ)) {
                    g.fill(cx, cy, cx + GRID_CELL - 1, cy + GRID_CELL - 1, 0xFF6A5E52);
                } else if (details.get(i)) {
                    g.fill(cx, cy, cx + GRID_CELL, cy + GRID_CELL, CLAY_DARK);
                    g.fill(cx, cy, cx + GRID_CELL - 1, cy + GRID_CELL - 1, DETAIL);
                }
                if (ghost.get(i)) {
                    g.renderOutline(cx + 1, cy + 1, GRID_CELL - 2, GRID_CELL - 2, GHOST);
                }
            }
        }
        g.drawString(font, Component.translatable("gui.halgventmod.wheel.slice", sliceZ + 1, VesselGeometry.SIZE),
                gx, gy - 10, TEXT_DIM, false);
    }

    /** Сосуд объёмом: крутится вместе с кругом; на остановленном — поворачивается мышью. */
    private void renderView(GuiGraphics g, int vx, int vy) {
        g.fill(vx - 1, vy - 1, vx + VIEW + 1, vy + VIEW + 1, 0xFF000000);
        g.fill(vx, vy, vx + VIEW, vy + VIEW, 0xFF1C1814);
        BitSet voxels = VesselGeometry.voxels(profile, details.stream().toArray());
        int[] faces = VoxelMesh.build(voxels);
        float scale = 6.0F;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(vx + VIEW / 2.0F, vy + VIEW / 2.0F, 200);
        pose.scale(scale, -scale, scale);
        // Ось Y перевёрнута (окно считает вниз): положительный наклон — взгляд сверху
        pose.mulPose(Axis.XP.rotationDegrees(VIEW_TILT));
        pose.mulPose(Axis.YP.rotationDegrees(angle + dragAngle));
        pose.translate(-VesselGeometry.SIZE / 2.0F, -VesselGeometry.SIZE / 2.0F, -VesselGeometry.SIZE / 2.0F);
        VoxelRender.renderGui(faces, 0xC8B49C, 255, pose.last(), g.bufferSource().getBuffer(RenderType.gui()));
        g.flush();
        pose.popPose();
    }

    private void renderSpeed(GuiGraphics g, int x, int y) {
        int w = GRID;
        g.fill(x - 1, y - 1, x + w + 1, y + 7, 0xFF000000);
        g.fill(x, y, x + w, y + 6, 0xFF3A2E26);
        g.fill(x + (int) (w * LOW), y, x + (int) (w * HIGH), y + 6, 0xFF3F6A2E);
        g.fill(x + (int) (w * HIGH), y, x + (int) (w * WRENCH), y + 6, 0xFF7A5A20);
        g.fill(x + (int) (w * WRENCH), y, x + w, y + 6, 0xFF7A2A20);
        int px = x + (int) (w * speed);
        g.fill(px - 1, y - 2, px + 1, y + 8, 0xFFFFFFFF);
        g.drawString(font, Component.translatable(detailMode ? "gui.halgventmod.wheel.stopped"
                : "gui.halgventmod.wheel.speed"), x + w + 6, y - 1, TEXT_DIM, false);
    }

    private void renderStats(GuiGraphics g, int x, int y) {
        int sx = x + GRID_X;
        int sy = y + TOP + GRID + 14;
        g.drawString(font, Component.translatable("gui.halgventmod.wheel.stats",
                budget - scraps, budget, scraps, Math.round(match * 100), uneven()), sx, sy, TEXT, false);
        Component status;
        int color;
        if (problem != null) {
            status = Component.translatable("gui.halgventmod.wheel.problem." + problem);
            color = BAD;
        } else if (match < VesselGeometry.MIN_MATCH) {
            status = Component.translatable("gui.halgventmod.wheel.unlike", Math.round(VesselGeometry.MIN_MATCH * 100));
            color = BAD;
        } else {
            GearQuality.Grade grade = GearQuality.Grade.values()[forecast()];
            status = Component.translatable("gui.halgventmod.wheel.forecast",
                    Component.translatable(ModTraits.langKey(grade.id(), Gender.M)));
            color = GOOD;
        }
        g.drawString(font, status, sx, sy + 11, color, false);
        if (System.currentTimeMillis() < messageUntil) {
            g.drawCenteredString(font, message, x + VIEW_X + VIEW / 2, y + TOP + VIEW + 6, 0xFFFFD27F);
        }
        Component hint = Component.translatable(detailMode ? "gui.halgventmod.wheel.hint_details" : "gui.halgventmod.wheel.hint_body");
        g.drawString(font, hint, x + PAD, y + HEIGHT - 11, TEXT_DIM, false);
    }
}
