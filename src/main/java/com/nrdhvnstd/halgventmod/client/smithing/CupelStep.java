package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.smithing.CupelBlockEntity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Купель сверху: в чаше — расплавленный свинец, на нём всплывает жёлтый глёт. Пробел — качнуть мехи: жар держите
 * в полосе — тогда свинец выгорает. Глёт сгоняйте к краю чаши (держите ЛКМ и ведите мышью) — там его впитывает
 * костяная зола; не согнанный глёт закрывает свинец от воздуха (выгорает медленнее), а простоявший — запекается
 * коркой и уносит с собой серебро. Перегрели — серебро брызжет. Свинец выгорел — серебро вспыхивает каплей, готово.
 */
final class CupelStep extends SmithStep {
    // ⚠️
    private static final float LOW = 0.6F;
    private static final float HIGH = 0.82F;
    private static final float PUMP = 0.09F;
    private static final float DECAY = 0.07F;          // жар в секунду
    private static final float SPAWN_EVERY = 0.55F;    // глёт всплывает, секунд (в полосе жара)
    private static final float GROW = 3.5F;            // пятно растёт, пикселей в секунду
    private static final float MAX_SIZE = 11;
    private static final float BAKE_AFTER = 6.5F;      // столько простоит — запечётся
    private static final float FLASH_SECONDS = 1.2F;

    private final float needed;
    private final List<float[]> scum = new ArrayList<>(); // x, y (от середины), размер, возраст
    private float heat = 0.5F;
    private float burned;
    private float time;
    private float inBand;
    private float spawnTimer;
    private float spatterTimer;
    private int skimmed;
    private int baked;
    private int spatters;
    private float flashAt = -1;
    private boolean skimming;
    private double mouseX = -1;
    private double mouseY = -1;
    private int centerX;
    private int centerY;
    private int radius = 52;

    CupelStep(SmithWorkScreen screen, Random random, int count) {
        super(screen, random);
        this.needed = 12 + count * 3 + random.nextFloat() * 4;
    }

    @Override
    boolean done() {
        return flashAt >= 0 && time - flashAt >= FLASH_SECONDS;
    }

    @Override
    int rawScore() {
        float band = time <= 0 ? 0.5F : inBand / time;
        return clampScore(40 + band * 60 - baked * 9 - spatters * 6 + Math.min(10, skimmed));
    }

    @Override
    List<Integer> result() {
        return List.of(score(), CupelBlockEntity.ON_TIME, baked + spatters);
    }

    @Override
    Component status() {
        return Component.translatable("gui.halgventmod.smith_work.cupel_status", Math.round(Math.min(1, burned / needed) * 100),
                skimmed, baked);
    }

    @Override
    Component hint() {
        return Component.translatable("gui.halgventmod.smith_work.cupel_hint");
    }

    /** Сколько свинца закрыто глётом, 0–1. */
    private float coverage() {
        float area = 0;
        for (float[] s : scum) {
            area += s[2] * s[2] * 3.14F;
        }
        return Math.min(1, area / (radius * radius * 3.14F * 0.7F));
    }

    @Override
    void tick(float dt, long now) {
        time += dt;
        if (flashAt >= 0) {
            return;
        }
        heat = Math.max(0.15F, heat - DECAY * dt);
        boolean band = heat >= LOW && heat <= HIGH;
        if (band) {
            inBand += dt;
            burned += dt * (1 - 0.8F * coverage());
            spawnTimer += dt;
        } else if (heat > HIGH) {
            burned += dt * 0.5F;
            spawnTimer += dt * 1.3F;
            spatterTimer += dt;
            if (spatterTimer >= 1.2F) {
                spatterTimer = 0;
                spatters++;
                screen.show("cupel_spatter", BAD);
                screen.sound(SoundEvents.LAVA_POP, 1.5F);
            }
        }
        if (spawnTimer >= SPAWN_EVERY) {
            spawnTimer = 0;
            double a = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * radius * 0.55;
            scum.add(new float[] {(float) (Math.cos(a) * r), (float) (Math.sin(a) * r * 0.6), 2, 0});
        }
        for (int i = scum.size() - 1; i >= 0; i--) {
            float[] s = scum.get(i);
            s[2] = Math.min(MAX_SIZE, s[2] + GROW * dt);
            s[3] += dt;
            if (s[3] >= BAKE_AFTER) {
                scum.remove(i);
                baked++;
                screen.show("cupel_baked", WARN);
                screen.sound(SoundEvents.BASALT_PLACE, 0.8F);
            }
        }
        if (burned >= needed) {
            flashAt = time;
            scum.clear();
            screen.show("cupel_flash", GOOD);
            screen.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F);
        }
    }

    @Override
    void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, long now) {
        centerX = x + (w - 30) / 2;
        centerY = y + h / 2;
        radius = Math.min(56, h / 2 - 10);
        // Чаша (костяная зола) и свинец: эллипс — смотрим сверху наискосок
        ellipse(g, centerX, centerY, radius + 10, 0xFFB8B0A0);
        ellipse(g, centerX, centerY, radius + 4, mix(0xFFB8B0A0, 0xFFD8A840, Math.min(1, burned / needed) * 0.7F));
        float left = Math.max(0.08F, 1 - burned / needed);
        boolean flash = flashAt >= 0;
        int pool = flash ? mix(0xFFFFFFFF, 0xFFD0D4DC, (time - flashAt) / FLASH_SECONDS) : glow(0.5F + heat * 0.42F);
        ellipse(g, centerX, centerY, Math.round(radius * (0.25F + 0.75F * left)), pool);
        // Глёт: жёлтые пятна, старые — темнее (запекаются)
        for (float[] s : scum) {
            int color = mix(0xFFF0C040, 0xFF8A5A20, s[3] / BAKE_AFTER);
            ellipse(g, centerX + Math.round(s[0]), centerY + Math.round(s[1]), Math.round(s[2]), color);
        }
        // Скребок под мышью
        if (mouseX >= 0 && !flash) {
            g.fill(mouseX - 6, mouseY - 1, mouseX + 6, mouseY + 1, skimming ? IRON_LIGHT : IRON_DARK);
            g.fill(mouseX - 1, mouseY - 14, mouseX + 1, mouseY - 1, WOOD);
        }
        gauge(g, x + w - 14, y + 14, 6, h - 30, heat, LOW, HIGH, heat > HIGH ? BAD : TEXT);
    }

    /** Эллипс 2 : 1 (вид сверху наискосок). */
    private static void ellipse(GuiGraphics g, int cx, int cy, int r, int color) {
        int ry = Math.max(1, r * 6 / 10);
        for (int dy = -ry; dy <= ry; dy++) {
            int half = (int) Math.round(r * Math.sqrt(1 - dy * dy / (double) (ry * ry)));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    @Override
    void mouseMoved(double mouseX, double mouseY) {
        if (skimming && this.mouseX >= 0 && flashAt < 0) {
            float dx = (float) (mouseX - this.mouseX);
            float dy = (float) (mouseY - this.mouseY);
            // Пятна под скребком едут за ним; за краем чаши — впитались
            for (int i = scum.size() - 1; i >= 0; i--) {
                float[] s = scum.get(i);
                double px = centerX + s[0] - mouseX;
                double py = centerY + s[1] - mouseY;
                if (px * px + py * py <= (s[2] + 8) * (s[2] + 8)) {
                    s[0] += dx;
                    s[1] += dy;
                    double ex = s[0] / (double) radius;
                    double ey = s[1] / (radius * 0.6);
                    if (ex * ex + ey * ey > 1) {
                        scum.remove(i);
                        skimmed++;
                        screen.sound(SoundEvents.SAND_PLACE, 1.4F);
                    }
                }
            }
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || flashAt >= 0) {
            return false;
        }
        skimming = true;
        return true;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !skimming) {
            return false;
        }
        skimming = false;
        return true;
    }

    @Override
    boolean keyPressed(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_SPACE || flashAt >= 0) {
            return false;
        }
        heat = Math.min(1, heat + PUMP);
        screen.sound(SoundEvents.FIRECHARGE_USE, 0.5F + heat * 0.4F);
        return true;
    }
}
