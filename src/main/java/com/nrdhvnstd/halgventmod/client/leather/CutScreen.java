package com.nrdhvnstd.halgventmod.client.leather;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.leather.LeatherPatterns;
import com.nrdhvnstd.halgventmod.network.CutResultPayload;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Мини-игра раскроя на шитейном столе. На коже или ткани мелом намечен контур выкройки (CutShape). Зажмите ЛКМ
 * на мигающей метке и ведите нож (ножницы) по линии до конца круга. Сход с линии или рывок — срыв: нож встаёт,
 * вернитесь к месту, где остановились. Отпускать можно — разрез продолжится с того же места.
 * Итог (срывы и средняя неровность) уходит на сервер ({@link CutResultPayload}). Esc — бросить: ничего не раскроено.
 */
public class CutScreen extends Screen {
    // Меняйте здесь
    private static final float TOLERANCE = 6.0F;        // пикселей от линии — ещё на линии
    private static final float MAX_SPEED = 520.0F;      // пикселей в секунду — быстрее — рывок
    private static final int WINDOW = 10;                // на сколько точек вперёд можно «перескочить»
    private static final float STEP = 3.0F;              // шаг точек контура, пикселей
    private static final int FINISH_DELAY_MS = 600;

    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int WIDTH = 260;
    private static final int HEIGHT = 180;
    private static final int AREA_W = 200;               // поле контура 2×1
    private static final int AREA_H = 100;
    private static final int TEXT = 0xFFE9DCB4;
    private static final int TEXT_DIM = 0xFFAA9C80;
    private static final int CHALK = 0xFFF2EEE0;
    private static final int SLIP = 0xFFE0604A;

    private final BlockPos pos;
    private final LeatherPatterns.Pattern pattern;
    private final boolean cloth;
    private final ItemStack tool;
    private final int base;
    private final int dark;
    private final int light;
    private final float[] px;   // контур в координатах поля, от левого верхнего угла поля
    private final float[] py;
    private int progress;       // до какой точки разрезано
    private boolean holding;
    private boolean off;        // сорвался — ждём возвращения на линию
    private int slips;
    private double deviation;
    private int samples;
    private float lastX = Float.NaN;
    private float lastY;
    private long lastMove;
    private long slipFlashUntil;
    private long finishedAt = -1;
    private boolean sent;

    public CutScreen(BlockPos pos, LeatherPatterns.Pattern pattern) {
        super(Component.translatable("gui.halgventmod.cut.title", pattern.result().getHoverName()));
        this.pos = pos;
        this.pattern = pattern;
        this.cloth = pattern.material() == LeatherPatterns.Material.CLOTH;
        this.tool = new ItemStack(cloth ? Items.SHEARS : ModItems.KNIFE.get());
        boolean wool = cloth && pattern.leather().get(0).item().get() == ModItems.WOOLEN_CLOTH.get();
        this.base = !cloth ? 0xFF8A5A33 : wool ? 0xFF7A6C5C : 0xFFC8BEA2;
        this.dark = !cloth ? 0xFF5A3720 : wool ? 0xFF54483C : 0xFF9C9278;
        this.light = !cloth ? 0xFFA06C40 : wool ? 0xFF94866F : 0xFFDDD5BC;

        // Контур: углы скруглены, точки — через равный шаг
        double[] c = pattern.shape().contour();
        int n = c.length / 2;
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            float ax = (float) (c[2 * (i % n)] * AREA_H);
            float ay = (float) (c[2 * (i % n) + 1] * AREA_H);
            if (i == 0) {
                points.add(new float[] {ax, ay});
                continue;
            }
            float[] last = points.get(points.size() - 1);
            float dx = ax - last[0];
            float dy = ay - last[1];
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            int steps = Math.max(1, (int) (length / STEP));
            for (int s = 1; s <= steps; s++) {
                points.add(new float[] {last[0] + dx * s / steps, last[1] + dy * s / steps});
            }
        }
        px = new float[points.size()];
        py = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            px[i] = points.get(i)[0];
            py[i] = points.get(i)[1];
        }
    }

    public static void open(BlockPos pos, int pattern) {
        if (pattern >= 0 && pattern < LeatherPatterns.all().size()) {
            Minecraft.getInstance().setScreen(new CutScreen(pos, LeatherPatterns.get(pattern)));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int areaX() {
        return (width - AREA_W) / 2;
    }

    private int areaY() {
        return (height - HEIGHT) / 2 + 26;
    }

    // ---------------------------------------------------------------- ход игры

    /** Где мышь относительно разреза: продвинуть, засчитать отход или срыв. */
    private void follow(float mx, float my, long now) {
        float x = mx - areaX();
        float y = my - areaY();
        // Рывок: мышь неслась быстрее, чем режет нож
        if (!Float.isNaN(lastX) && now > lastMove) {
            float dx = x - lastX;
            float dy = y - lastY;
            float speed = (float) Math.sqrt(dx * dx + dy * dy) * 1000.0F / (now - lastMove);
            if (speed > MAX_SPEED && !off && progress > 0) {
                slip();
            }
        }
        lastX = x;
        lastY = y;
        lastMove = now;

        int end = Math.min(px.length - 1, progress + WINDOW);
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = progress; i <= end; i++) {
            float dx = px[i] - x;
            float dy = py[i] - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        if (bestDistance <= TOLERANCE) {
            off = false;
            if (best > progress) {
                for (int i = progress; i < best; i++) {
                    deviation += bestDistance;
                    samples++;
                }
                if (cloth && best / 6 != progress / 6) {
                    sound(SoundEvents.SHEEP_SHEAR, 1.4F + 0.2F * (float) Math.random());
                } else if (!cloth && best / 8 != progress / 8) {
                    sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.6F);
                }
                progress = best;
            }
            if (progress >= px.length - 1) {
                finishedAt = now;
                sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F);
            }
        } else if (!off && progress > 0) {
            slip();
        }
    }

    private void slip() {
        off = true;
        slips++;
        slipFlashUntil = System.currentTimeMillis() + 250;
        sound(SoundEvents.WOOL_BREAK, 0.8F);
    }

    private int wobble() {
        return samples == 0 ? 0 : (int) Math.round(deviation / samples * 10);
    }

    private void sound(SoundEvent event, float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
        }
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = System.currentTimeMillis();
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        int ax = areaX();
        int ay = areaY();
        g.blitSprite(PANEL, x, y, WIDTH, HEIGHT);
        TextFit.drawCentered(g, font, title, width / 2, y + 9, WIDTH - 20, TEXT);

        // Кусок кожи или ткани с крапом
        g.fill(ax - 6, ay - 6, ax + AREA_W + 6, ay + AREA_H + 6, dark);
        g.fill(ax - 4, ay - 4, ax + AREA_W + 4, ay + AREA_H + 4, base);
        for (int i = 0; i < 70; i++) {
            int sx = ax - 2 + (i * 37) % (AREA_W + 2);
            int sy = ay - 2 + (i * 23) % (AREA_H + 2);
            g.fill(sx, sy, sx + 2, sy + 1, light);
        }

        // Мел: пунктир по всему контуру; разрезанное — тёмная прорезь
        for (int i = 0; i < px.length; i++) {
            int cx = Math.round(ax + px[i]);
            int cy = Math.round(ay + py[i]);
            if (i <= progress) {
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF1A100A);
            } else if (i % 2 == 0) {
                g.fill(cx, cy, cx + 1, cy + 1, CHALK);
            }
        }
        // Метка: где продолжать
        if (finishedAt < 0 && (!holding || off) && (now / 250) % 2 == 0) {
            int cx = Math.round(ax + px[progress]);
            int cy = Math.round(ay + py[progress]);
            g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFFFD27F);
        }
        if (now < slipFlashUntil) {
            g.fill(ax - 6, ay - 6, ax + AREA_W + 6, ay - 4, SLIP);
            g.fill(ax - 6, ay + AREA_H + 4, ax + AREA_W + 6, ay + AREA_H + 6, SLIP);
            g.drawCenteredString(font, Component.translatable("gui.halgventmod.cut.slip"), width / 2, ay + AREA_H / 2 - 4, SLIP);
        }

        // Инструмент у курсора
        if (finishedAt < 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.renderItem(tool, mouseX - 3, mouseY - 13);
            g.pose().popPose();
        }

        int infoY = ay + AREA_H + 12;
        int percent = Math.round(100.0F * progress / (px.length - 1));
        Component line = finishedAt >= 0 ? Component.translatable("gui.halgventmod.cut.done")
                : Component.translatable("gui.halgventmod.cut.progress", percent, slips, String.format("%.1f", wobble() / 10.0));
        TextFit.draw(g, font, line, x + 14, infoY, WIDTH - 28, TEXT);
        TextFit.draw(g, font, Component.translatable(progress == 0 ? "gui.halgventmod.cut.hint_start" : "gui.halgventmod.cut.hint"),
                x + 14, infoY + 12, WIDTH - 28, TEXT_DIM);

        if (finishedAt >= 0 && now - finishedAt > FINISH_DELAY_MS) {
            send();
            onClose();
        }
    }

    private void send() {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new CutResultPayload(pos, slips, wobble()));
        }
    }

    // ---------------------------------------------------------------- мышь

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && finishedAt < 0) {
            // Начать (или продолжить) можно только с места, где остановились
            float dx = (float) mouseX - areaX() - px[progress];
            float dy = (float) mouseY - areaY() - py[progress];
            if (Math.sqrt(dx * dx + dy * dy) <= TOLERANCE * 1.5F) {
                holding = true;
                off = false;
                lastX = Float.NaN;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && holding && finishedAt < 0) {
            follow((float) mouseX, (float) mouseY, System.currentTimeMillis());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            holding = false;
            lastX = Float.NaN;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
