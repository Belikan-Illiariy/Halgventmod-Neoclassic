package com.nrdhvnstd.halgventmod.client.leather;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.SewingResultPayload;
import com.nrdhvnstd.halgventmod.registry.ModItems;

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
 * Мини-игра шитья на столе кожевника. По шву из отметок бегает игла; ЛКМ или Пробел, когда игла над
 * следующей отметкой, — стежок, иначе промах. С каждым стежком игла быстрее. Итог (число промахов)
 * уходит на сервер ({@link SewingResultPayload}), там же решается качество. Esc — бросить: раскроенная
 * вещь остаётся на столе.
 */
public class SewingScreen extends Screen {
    // Меняйте здесь
    private static final float BASE_SPEED = 0.45F;      // шва в секунду
    private static final float SPEED_PER_STITCH = 0.05F;
    private static final int TOLERANCE = 5;             // пикселей до отметки, которые прощаются
    private static final int FINISH_DELAY_MS = 600;

    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int WIDTH = 260;
    private static final int HEIGHT = 150;
    private static final int LEATHER = 0xFF8A5A33;
    private static final int LEATHER_DARK = 0xFF5A3720;
    private static final int LEATHER_LIGHT = 0xFFA06C40;
    private static final int THREAD = 0xFFE9DCB4;
    private static final int THREAD_SHADOW = 0xFF3A2414;
    private static final int HOLE = 0xFF2A180C;

    private final BlockPos pos;
    private final ItemStack result;
    private final int stitches;
    private final ItemStack needle = new ItemStack(ModItems.NEEDLE.get());
    private int done;
    private int misses;
    private long missFlashUntil;
    private long finishedAt = -1;
    private boolean sent;
    private float position; // 0..1 по шву
    private int direction = 1;
    private long lastFrame = -1;

    public SewingScreen(BlockPos pos, ItemStack result, int stitches) {
        super(Component.translatable("gui.halgventmod.sewing.title", result.getHoverName()));
        this.pos = pos;
        this.result = result;
        this.stitches = Math.max(1, stitches);
    }

    public static void open(BlockPos pos, ItemStack result, int stitches) {
        Minecraft.getInstance().setScreen(new SewingScreen(pos, result, stitches));
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

    // Шов: stitches+1 отметка слева направо, лёгкой волной
    private float holeX(int i) {
        return left() + 34 + (WIDTH - 68) * (float) i / stitches;
    }

    private float seamY(float x) {
        float t = (x - left() - 34) / (WIDTH - 68);
        return top() + 76 + Mth.sin(t * Mth.PI * 2.0F) * 7.0F;
    }

    private float needleX() {
        return left() + 34 + (WIDTH - 68) * position;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = System.currentTimeMillis();
        advance(now);
        int x = left();
        int y = top();
        g.blitSprite(PANEL, x, y, WIDTH, HEIGHT);
        g.drawCenteredString(font, title, width / 2, y + 10, 0xFFE9DCB4);

        // Кусок кожи
        g.fill(x + 18, y + 40, x + WIDTH - 18, y + 112, LEATHER_DARK);
        g.fill(x + 20, y + 42, x + WIDTH - 20, y + 110, LEATHER);
        for (int i = 0; i < 40; i++) {
            int sx = x + 24 + (i * 37) % (WIDTH - 48);
            int sy = y + 46 + (i * 23) % 60;
            g.fill(sx, sy, sx + 2, sy + 1, LEATHER_LIGHT);
        }

        // Сделанные стежки — нитка между отметками
        for (int i = 0; i < done; i++) {
            stitch(g, holeX(i), holeX(i + 1));
        }
        // Отметки: следующая — мигает
        for (int i = 0; i <= stitches; i++) {
            float hx = holeX(i);
            int hy = (int) seamY(hx);
            boolean next = i == done + 1 && finishedAt < 0;
            if (next && (now / 250) % 2 == 0) {
                g.fill((int) hx - 3, hy - 3, (int) hx + 3, hy + 3, 0xFFFFD27F);
            }
            g.fill((int) hx - 1, hy - 1, (int) hx + 1, hy + 1, HOLE);
        }

        // Игла
        if (finishedAt < 0) {
            float nx = needleX();
            int ny = (int) seamY(nx);
            g.renderItem(needle, (int) nx - 8, ny - 15);
        }

        // Промах — красная вспышка по краю
        if (now < missFlashUntil) {
            g.fill(x + 18, y + 40, x + WIDTH - 18, y + 42, 0xFFD03030);
            g.fill(x + 18, y + 110, x + WIDTH - 18, y + 112, 0xFFD03030);
        }

        g.renderItem(result, x + 16, y + 118);
        Component progress = finishedAt >= 0
                ? Component.translatable("gui.halgventmod.sewing.done")
                : Component.translatable("gui.halgventmod.sewing.progress", done, stitches, misses);
        g.drawString(font, progress, x + 38, y + 118, 0xFFE9DCB4, false);
        g.drawString(font, Component.translatable("gui.halgventmod.sewing.hint"), x + 38, y + 130, 0xFFAA9C80, false);

        if (finishedAt >= 0 && now - finishedAt > FINISH_DELAY_MS) {
            send();
            onClose();
        }
    }

    /** Нитка между двумя отметками по шву. */
    private void stitch(GuiGraphics g, float x0, float x1) {
        int steps = Math.max(1, (int) (x1 - x0));
        for (int s = 0; s <= steps; s++) {
            float x = x0 + (x1 - x0) * s / steps;
            int y = (int) seamY(x);
            g.fill((int) x, y + 1, (int) x + 1, y + 2, THREAD_SHADOW);
            g.fill((int) x, y - 1, (int) x + 1, y + 1, THREAD);
        }
    }

    /** Игла бегает туда-обратно по шву. */
    private void advance(long now) {
        if (lastFrame < 0) {
            lastFrame = now;
        }
        float seconds = Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        if (finishedAt >= 0) {
            return;
        }
        position += direction * (BASE_SPEED + SPEED_PER_STITCH * done) * seconds;
        if (position >= 1.0F) {
            position = 2.0F - position;
            direction = -1;
        } else if (position <= 0.0F) {
            position = -position;
            direction = 1;
        }
    }

    private void pierce() {
        if (finishedAt >= 0 || minecraft == null) {
            return;
        }
        float target = holeX(done + 1);
        if (Math.abs(needleX() - target) <= TOLERANCE) {
            done++;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOOL_HIT, 1.3F));
            if (done >= stitches) {
                finishedAt = System.currentTimeMillis();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2F));
            }
        } else {
            misses++;
            missFlashUntil = System.currentTimeMillis() + 200;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SHEEP_SHEAR, 0.6F));
        }
    }

    private void send() {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new SewingResultPayload(pos, misses));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pierce();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            pierce();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
