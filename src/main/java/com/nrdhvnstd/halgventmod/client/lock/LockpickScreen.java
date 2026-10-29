package com.nrdhvnstd.halgventmod.client.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.lock.Lockpicking;
import com.nrdhvnstd.halgventmod.network.LockpickResultPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Взлом замка — мини-игра. Замок нарисован в разрезе: сверху корпус, снизу цилиндр, между ними линия среза. В каждой
 * шахте — нижний штифт (латунь, у всех разной длины), над ним верхний (сталь) и пружина. Замок открыт, когда у всех
 * шахт верхний штифт стоит над линией среза.
 * <pre>
 * Мышь           — отмычка ходит вдоль скважины (не мгновенно); ЛКМ — поднять штифт над кончиком.
 * Колесо, W / S  — натяжение воротка, 0…100 %.
 * Esc            — бросить без потерь.
 * </pre>
 * Под натяжением цилиндр зажимает только ОДИН штифт — чья очередь (порядок спрятан). Он идёт туго; подними его так,
 * чтобы верх нижнего штифта встал на линию среза, и отпусти ЛКМ — верхний штифт сядет на цилиндр, щёлк. Остальные
 * штифты ходят свободно и не встают. Натяжение должно попасть в спрятанный «вилку» замка:
 * слабее — зажатый штифт ведёт себя как свободный и не встаёт; сильнее — штифт заклинило, отмычка гнётся.
 * Поднял зажатый штифт выше линии — перетянул: замок встал колом, поможет только сбросить натяжение почти в ноль
 * (все вставшие штифты падают). Грибковые штифты (1–2 в замке) дают ложную установку: цилиндр отыгрывает назад,
 * штифт застревает; ослабь натяжение к нижнему краю «вилки» и дожми ещё немного — тогда встанет.
 * Отмычка устаёт от силы; полоса прочности кончилась — ломается. Зерно замка — с сервера (Lockpicking.start).
 */
public class LockpickScreen extends Screen {
    // Меняйте здесь ⚠️ (сложность подбирать в игре)
    private static final float WINDOW_SCALE = 60.0F;      // окно с сервера (доля хода) × это = пиксели допуска
    private static final float FREE_SPEED = 1.4F;         // подъём свободного штифта, пикселей за тик
    private static final float BIND_SPEED = 0.30F;        // подъём зажатого штифта при верном натяжении
    private static final float HEAVY_SPEED = 0.04F;       // при пережатом — почти стоит
    private static final float FALL_SPEED = 2.2F;         // пружина возвращает штифт
    private static final float PICK_SPEED = 3.0F;         // отмычка вдоль скважины, пикселей за тик
    private static final float TENSION_WHEEL = 0.04F;     // шаг натяжения колесом
    private static final float TENSION_KEYS = 0.012F;     // за тик удержания W / S
    private static final float TENSION_ZERO = 0.06F;      // ниже — натяжения считай нет
    private static final float SWEET_MIN = 0.25F;         // «вилка» натяжения лежит в этих пределах
    private static final float SWEET_RANGE = 0.45F;
    private static final float SWEET_WIDTH_PER_WINDOW = 4.0F; // ширина вилки = окно с сервера × это
    private static final float SPOOL_PUSH = 5.0F;         // на сколько пикселей дожать грибок после ложной установки
    private static final float SPOOL_EASE = 0.4F;         // грибок идёт только в нижних 40 % вилки
    private static final int RELEASE_GRACE = 10;          // тиков без натяжения до падения штифтов
    private static final float HEAVY_STRESS = 0.55F;      // за тик, если давят заклинивший штифт
    private static final float TOP_STRESS = 0.30F;        // за тик, если штифт упёрт в потолок шахты под натяжением
    private static final float OVERSET_STRESS = 22.0F;
    private static final float DROP_STRESS = 6.0F;
    private static final float RELAX = 0.08F;
    private static final float MAX_STRESS = 100.0F;

    // Чертёж замка
    private static final int WIDTH = 360;
    private static final int HEIGHT = 216;
    private static final int PADDING = 14;
    private static final int LOCK_TOP = 30;                // от верха окна: потолок шахт
    private static final int HOUSING = 56;                 // высота корпуса до линии среза
    private static final int PLUG = 44;                    // высота цилиндра
    private static final int KEYWAY = 30;                  // от линии среза до низа штифтов в покое
    private static final int SHAFT_WIDTH = 12;
    private static final int PIN_WIDTH = 8;
    private static final int DRIVER_LENGTH = 16;
    private static final int KEY_MIN = 8;
    private static final int KEY_STEPS = 6;                // длины нижних штифтов: KEY_MIN + шаг × 3
    private static final int MAX_LIFT = 34;
    private static final int GAUGE_WIDTH = 10;

    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_TEXT = 0xFFE8E0D0;
    private static final int COLOR_DIM = 0xFFA09888;
    private static final int COLOR_WARN = 0xFFE07050;
    private static final int COLOR_HOUSING = 0xFF3A3A40;
    private static final int COLOR_HOUSING_EDGE = 0xFF55555C;
    private static final int COLOR_PLUG = 0xFF8A6A30;
    private static final int COLOR_PLUG_EDGE = 0xFFB08A40;
    private static final int COLOR_SHAFT = 0xFF141214;
    private static final int COLOR_KEYWAY = 0xFF201A12;
    private static final int COLOR_KEY_PIN = 0xFFD8B050;
    private static final int COLOR_KEY_PIN_DARK = 0xFF9C7A30;
    private static final int COLOR_DRIVER = 0xFFB8BCC4;
    private static final int COLOR_DRIVER_DARK = 0xFF7A7E86;
    private static final int COLOR_SET = 0xFF70D070;
    private static final int COLOR_SPRING = 0xFF8A8E96;
    private static final int COLOR_SHEAR = 0x80FFE8A0;
    private static final int COLOR_PICK = 0xFFD4D4D4;
    private static final int COLOR_PICK_DARK = 0xFF6C6C70;

    private final int pins;
    private final float window;          // допуск, пикселей
    private final float sweetLow;
    private final float sweetHigh;
    private final int[] keyLength;
    private final boolean[] spool;
    private final List<Integer> order = new ArrayList<>(); // order.get(k) — какой штифт зажимается k-м
    private final float[] lift;
    private final boolean[] isSet;
    private final boolean[] falseSet;
    private int next;
    private int oversetPin = -1;
    private int released;
    private float tension;
    private float stress;
    private float pickX = -1;            // кончик отмычки, пиксели от левого края замка
    private int recoil;                  // тиков отдачи цилиндра после ложной установки
    private int ticks;
    private boolean pushing;
    private boolean finished;
    private double mouseX;

    private LockpickScreen(long seed, int pins, float serverWindow) {
        super(Component.translatable("gui.halgventmod.lockpick.title"));
        this.pins = pins;
        this.window = Math.max(1.0F, serverWindow * WINDOW_SCALE);
        this.keyLength = new int[pins];
        this.spool = new boolean[pins];
        this.lift = new float[pins];
        this.isSet = new boolean[pins];
        this.falseSet = new boolean[pins];
        Random random = new Random(seed);
        for (int i = 0; i < pins; i++) {
            keyLength[i] = KEY_MIN + random.nextInt(4) * KEY_STEPS;
            order.add(i);
        }
        Collections.shuffle(order, random);
        float width = serverWindow * SWEET_WIDTH_PER_WINDOW;
        sweetLow = SWEET_MIN + random.nextFloat() * (SWEET_RANGE - width);
        sweetHigh = sweetLow + width;
        int spools = pins >= 7 ? 2 : 1;
        for (int k = 0; k < spools; k++) {
            spool[order.get(1 + random.nextInt(pins - 1))] = true; // первый по очереди — никогда не грибок
        }
    }

    /** LockpickStartPayload. */
    public static void open(long seed, int pins, float window) {
        Minecraft.getInstance().setScreen(new LockpickScreen(seed, pins, window));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        mouseX = width / 2.0;
    }

    @Override
    public void mouseMoved(double x, double y) {
        mouseX = x;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        tension = Mth.clamp(tension + (float) scrollY * TENSION_WHEEL, 0.0F, 1.0F);
        return true;
    }

    // ===== Геометрия =====

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private int lockLeft() {
        return left() + PADDING;
    }

    private int lockWidth() {
        return WIDTH - 3 * PADDING - GAUGE_WIDTH;
    }

    // Середина шахты i, пиксели от левого края замка
    private float shaft(int i) {
        return (i + 0.5F) * lockWidth() / pins;
    }

    private int shear() {
        return top() + LOCK_TOP + HOUSING;
    }

    // Высота подъёма, при которой верх нижнего штифта стоит на линии среза
    private float setLift(int i) {
        return KEYWAY - keyLength[i];
    }

    // Над каким штифтом кончик отмычки; -1 — между шахтами
    private int pinUnderPick() {
        for (int i = 0; i < pins; i++) {
            if (Math.abs(pickX - shaft(i)) <= PIN_WIDTH / 2.0F + 1) {
                return i;
            }
        }
        return -1;
    }

    private boolean inSweet() {
        return tension >= sweetLow && tension <= sweetHigh;
    }

    // ===== Ход игры =====

    @Override
    public void tick() {
        if (finished) {
            return;
        }
        ticks++;
        long handle = minecraft.getWindow().getWindow();
        pushing = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_W)) {
            tension = Math.min(1.0F, tension + TENSION_KEYS);
        }
        if (InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_S)) {
            tension = Math.max(0.0F, tension - TENSION_KEYS);
        }
        if (recoil > 0) {
            recoil--;
        }

        // Отмычка тянется к мыши; с поднятым штифтом её не сдвинуть
        float target = Mth.clamp((float) (mouseX - lockLeft()), 0, lockWidth());
        if (pickX < 0) {
            pickX = target;
        }
        int under = pinUnderPick();
        if (!(pushing && under >= 0 && lift[under] > 0)) {
            pickX += Mth.clamp(target - pickX, -PICK_SPEED, PICK_SPEED);
            under = pinUnderPick();
        }

        // Натяжения нет — вставшие штифты и перетянутый падают
        boolean slack = tension < TENSION_ZERO;
        released = slack ? released + 1 : 0;
        if (released > RELEASE_GRACE && (next > 0 || oversetPin >= 0 || anyFalseSet())) {
            dropAll();
        }
        if (slack) {
            stress = Math.max(0.0F, stress - RELAX);
        }

        int binding = next < pins && !slack ? order.get(next) : -1;
        for (int i = 0; i < pins; i++) {
            if (isSet[i] || i == oversetPin) {
                continue;
            }
            boolean lifting = pushing && i == under && oversetPin < 0;
            if (i == binding) {
                bindingPin(i, lifting);
            } else if (lifting) {
                lift[i] = Math.min(MAX_LIFT, lift[i] + FREE_SPEED);
                if (lift[i] >= MAX_LIFT && !slack) {
                    stress += TOP_STRESS;
                }
            } else {
                lift[i] = Math.max(0.0F, lift[i] - FALL_SPEED);
            }
        }

        if (next >= pins) {
            play(SoundEvents.IRON_TRAPDOOR_OPEN, 0.8F, 1.6F);
            finish(Lockpicking.Outcome.SUCCESS);
        } else if (stress >= MAX_STRESS) {
            play(SoundEvents.ITEM_BREAK, 1.0F, 1.0F);
            finish(Lockpicking.Outcome.BROKEN);
        }
    }

    private boolean anyFalseSet() {
        for (boolean f : falseSet) {
            if (f) {
                return true;
            }
        }
        return false;
    }

    // Штифт, который зажал цилиндр
    private void bindingPin(int i, boolean lifting) {
        float goal = setLift(i) + (falseSet[i] ? SPOOL_PUSH : 0);
        boolean heavy = tension > sweetHigh;
        boolean light = tension < sweetLow;
        if (falseSet[i]) {
            // Грибок на линии: стоит сам, вверх идёт только при слабом краю вилки
            boolean eased = tension >= sweetLow && tension <= sweetLow + (sweetHigh - sweetLow) * SPOOL_EASE;
            if (lifting) {
                if (eased) {
                    lift[i] += BIND_SPEED;
                } else {
                    stress += HEAVY_STRESS;
                    creak();
                }
            } else if (eased && Math.abs(lift[i] - goal) <= window) {
                setPin(i); // грибок прошёл линию
                return;
            }
        } else if (lifting) {
            lift[i] = Math.min(MAX_LIFT, lift[i] + (heavy ? HEAVY_SPEED : light ? FREE_SPEED : BIND_SPEED));
            if (heavy) {
                stress += HEAVY_STRESS;
                creak();
            }
        } else if (!light && Math.abs(lift[i] - goal) <= window) {
            // Отпустили на линии — встаёт (грибок — ложно)
            if (spool[i] && !falseSet[i]) {
                falseSet[i] = true;
                lift[i] = setLift(i);
                recoil = 8;
                play(SoundEvents.LEVER_CLICK, 0.9F, 0.7F);
            } else {
                setPin(i);
            }
            return;
        } else {
            lift[i] = Math.max(0.0F, lift[i] - FALL_SPEED);
        }
        // Выше линии под натяжением — перетянут
        if (!light && lift[i] - goal > window) {
            oversetPin = i;
            stress += OVERSET_STRESS;
            play(SoundEvents.CHAIN_BREAK, 0.8F, 1.3F);
        } else if (lifting && !heavy && !light && Math.abs(lift[i] - goal) <= window * 2 && ticks % 4 == 0) {
            play(SoundEvents.LEVER_CLICK, 0.2F, 1.8F); // у самой линии — тихий шорох
        }
    }

    private void setPin(int i) {
        isSet[i] = true;
        falseSet[i] = false;
        next++;
        play(SoundEvents.LEVER_CLICK, 1.0F, 1.5F);
    }

    private void creak() {
        if (ticks % 6 == 0) {
            play(SoundEvents.CHAIN_STEP, 0.5F, 0.6F);
        }
    }

    // Натяжение сброшено: всё падает
    private void dropAll() {
        int fallen = next;
        for (int i = 0; i < pins; i++) {
            isSet[i] = false;
            falseSet[i] = false;
        }
        next = 0;
        oversetPin = -1;
        if (fallen > 0) {
            stress += DROP_STRESS;
            play(SoundEvents.CHAIN_BREAK, 0.6F, 0.8F);
        }
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void finish(Lockpicking.Outcome outcome) {
        if (finished) {
            return;
        }
        finished = true;
        PacketDistributor.sendToServer(new LockpickResultPayload(outcome, ticks));
        onClose();
    }

    @Override
    public void onClose() {
        if (!finished) {
            finished = true;
            PacketDistributor.sendToServer(new LockpickResultPayload(Lockpicking.Outcome.CANCEL, ticks));
        }
        super.onClose();
    }

    // ===== Рисование =====

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        ConquestUi.window(graphics, left(), top(), left() + WIDTH, top() + HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        graphics.drawString(font, title, x + PADDING, y + 8, COLOR_TITLE, true);
        Component count = Component.translatable("gui.halgventmod.lockpick.pins", next, pins);
        graphics.drawString(font, count, x + WIDTH - PADDING - font.width(count), y + 8, COLOR_TEXT, false);

        int lx = lockLeft();
        int lw = lockWidth();
        int shear = shear();
        int ceiling = y + LOCK_TOP;
        int plugBottom = shear + PLUG;
        int rest = shear + KEYWAY; // низ штифтов в покое
        // Цилиндр под натяжением чуть повёрнут (сдвиг), после ложной установки — отыгрывает назад
        int turn = tension >= TENSION_ZERO ? 1 + Math.round(tension * 2) : 0;
        if (recoil > 0) {
            turn -= recoil % 2 == 0 ? 2 : 1;
        }

        graphics.fill(lx - 4, ceiling - 4, lx + lw + 4, shear, COLOR_HOUSING);
        graphics.fill(lx - 4, ceiling - 4, lx + lw + 4, ceiling - 3, COLOR_HOUSING_EDGE);
        graphics.fill(lx - 4 + turn, shear, lx + lw + 4 + turn, plugBottom, COLOR_PLUG);
        graphics.fill(lx - 4 + turn, shear, lx + lw + 4 + turn, shear + 1, COLOR_PLUG_EDGE);
        graphics.fill(lx - 4 + turn, rest + 1, lx + lw + 4 + turn, plugBottom - 4, COLOR_KEYWAY); // скважина

        for (int i = 0; i < pins; i++) {
            int cx = lx + Math.round(shaft(i));
            graphics.fill(cx - SHAFT_WIDTH / 2, ceiling, cx + SHAFT_WIDTH / 2, shear, COLOR_SHAFT);
            graphics.fill(cx - SHAFT_WIDTH / 2 + turn, shear, cx + SHAFT_WIDTH / 2 + turn, rest + 1, COLOR_SHAFT);

            int keyBottom = rest - Math.round(lift[i]);
            int keyTop = keyBottom - keyLength[i];
            int driverBottom = isSet[i] ? shear : keyTop;
            int driverTop = driverBottom - DRIVER_LENGTH;
            // Пружина от потолка до верхнего штифта
            for (int sy = ceiling + 1; sy < driverTop - 1; sy += 3) {
                int sx = ((sy - ceiling) / 3) % 2 == 0 ? -2 : 1;
                graphics.fill(cx + sx - 1, sy, cx + sx + 2, sy + 1, COLOR_SPRING);
            }
            // Верхний штифт (у грибка — с талией)
            int driverColor = isSet[i] ? COLOR_SET : COLOR_DRIVER;
            graphics.fill(cx - PIN_WIDTH / 2, driverTop, cx + PIN_WIDTH / 2, driverBottom, driverColor);
            graphics.fill(cx + PIN_WIDTH / 2 - 2, driverTop, cx + PIN_WIDTH / 2, driverBottom, COLOR_DRIVER_DARK);
            if (spool[i] && (falseSet[i] || isSet[i])) {
                int waist = driverBottom - DRIVER_LENGTH / 2;
                graphics.fill(cx - PIN_WIDTH / 2, waist - 2, cx - PIN_WIDTH / 2 + 2, waist + 2, COLOR_SHAFT);
                graphics.fill(cx + PIN_WIDTH / 2 - 2, waist - 2, cx + PIN_WIDTH / 2, waist + 2, COLOR_SHAFT);
            }
            // Нижний штифт: острый носик снизу
            int kx = cx + (keyBottom > shear ? turn : 0);
            int body = isSet[i] ? rest - keyLength[i] : keyTop;
            int bottom = isSet[i] ? rest : keyBottom;
            graphics.fill(kx - PIN_WIDTH / 2, body, kx + PIN_WIDTH / 2, bottom - 2, COLOR_KEY_PIN);
            graphics.fill(kx + PIN_WIDTH / 2 - 2, body, kx + PIN_WIDTH / 2, bottom - 2, COLOR_KEY_PIN_DARK);
            graphics.fill(kx - PIN_WIDTH / 2 + 2, bottom - 2, kx + PIN_WIDTH / 2 - 2, bottom, COLOR_KEY_PIN_DARK);
            if (i == oversetPin) {
                graphics.fill(cx - SHAFT_WIDTH / 2, shear - 1, cx + SHAFT_WIDTH / 2, shear + 1, COLOR_WARN);
            }
        }
        graphics.fill(lx - 4, shear, lx + lw + 4, shear + 1, COLOR_SHEAR); // линия среза

        // Отмычка: стержень из скважины слева и крючок под штифтом
        int under = pinUnderPick();
        int tipLift = under >= 0 && pushing && !isSet[under] ? Math.round(Math.min(lift[under], MAX_LIFT)) : 0;
        int tipX = lx + Math.round(Math.max(0, pickX));
        int shaftY = rest + 8;
        graphics.fill(x + 2, shaftY, tipX - 1, shaftY + 3, COLOR_PICK);
        graphics.fill(x + 2, shaftY + 2, tipX - 1, shaftY + 3, COLOR_PICK_DARK);
        graphics.fill(tipX - 2, rest - tipLift + 1, tipX + 2, shaftY + 3, COLOR_PICK);
        graphics.fill(tipX + 1, rest - tipLift + 1, tipX + 2, shaftY + 3, COLOR_PICK_DARK);

        // Вороток натяжения справа: шкала 0…100 %
        int gx = x + WIDTH - PADDING - GAUGE_WIDTH;
        int gTop = ceiling;
        int gBottom = plugBottom;
        graphics.fill(gx, gTop, gx + GAUGE_WIDTH, gBottom, COLOR_SHAFT);
        int level = gBottom - Math.round(tension * (gBottom - gTop));
        int gaugeColor = oversetPin >= 0 ? COLOR_WARN : 0xFFC8A060;
        graphics.fill(gx + 1, level, gx + GAUGE_WIDTH - 1, gBottom - 1, gaugeColor);
        graphics.fill(gx - 2, level, gx + GAUGE_WIDTH + 2, level + 1, COLOR_TEXT);
        Component tensionText = Component.translatable("gui.halgventmod.lockpick.tension_on");
        graphics.drawString(font, Math.round(tension * 100) + "%", gx - 2, gBottom + 3, COLOR_TEXT, false);

        // Прочность отмычки и подсказки
        int barY = plugBottom + 16;
        float strength = Mth.clamp(1.0F - stress / MAX_STRESS, 0.0F, 1.0F);
        int barX = x + PADDING;
        int barWidth = lw;
        graphics.fill(barX, barY, barX + barWidth, barY + 6, COLOR_SHAFT);
        int barColor = strength > 0.5F ? COLOR_SET : strength > 0.25F ? 0xFFE8C040 : COLOR_WARN;
        graphics.fill(barX, barY, barX + Math.round(barWidth * strength), barY + 6, barColor);
        graphics.drawString(font, Component.translatable("gui.halgventmod.lockpick.durability"), barX, barY + 9, COLOR_DIM, false);
        Component state = oversetPin >= 0 ? Component.translatable("gui.halgventmod.lockpick.overset")
                : anyFalseSet() ? Component.translatable("gui.halgventmod.lockpick.false_set") : tensionText;
        int stateColor = oversetPin >= 0 ? COLOR_WARN : anyFalseSet() ? COLOR_TITLE : COLOR_DIM;
        graphics.drawString(font, state, barX + barWidth - font.width(state), barY + 9, stateColor, false);
        graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.lockpick.hint"), x + WIDTH / 2,
                y + HEIGHT - 12, COLOR_DIM);
    }
}
