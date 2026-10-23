package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.network.SmithResultPayload;
import com.nrdhvnstd.halgventmod.network.StrikerHitPayload;
import com.nrdhvnstd.halgventmod.smithing.Minigame;
import com.nrdhvnstd.halgventmod.smithing.Striker;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно молотобойца: кувалда поднимается и падает в такт; ЛКМ — ударить, когда она в зелёном. Каждый удар уходит
 * кузнецу — тяжёлый удар туда, куда он указывает молотком. Чем ровнее в такт, тем точнее удар. Esc — отойти.
 */
public class StrikerScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final int WIDTH = 220;
    private static final int HEIGHT = 150;
    private static final int BEAT_MS = 900;
    private static final int WINDOW_MS = 160;

    private final BlockPos pos;
    private final long start = System.currentTimeMillis();
    private long struckAt = -1;
    private long lastHit = -1;
    private int hits;
    private int lastAccuracy = -1;
    private boolean sent;

    StrikerScreen(BlockPos pos) {
        super(Component.translatable("gui.halgventmod.striker.title"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private long offset(long now) {
        long phase = (now - start) % BEAT_MS;
        return Math.min(phase, BEAT_MS - phase);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = System.currentTimeMillis();
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        g.blitSprite(PANEL, x, y, WIDTH, HEIGHT);
        TextFit.drawCentered(g, font, title, x + WIDTH / 2, y + 9, WIDTH - 20, SmithStep.TEXT);
        int cx = x + WIDTH / 2;
        int base = y + HEIGHT - 44;
        SmithStep.anvil(g, cx - 60, base, 120);
        double phase = ((now - start) % BEAT_MS) / (double) BEAT_MS;
        int lift = (int) Math.round((1 + Math.cos(phase * Math.PI * 2)) / 2 * 46);
        if (struckAt >= 0 && now - struckAt < 120) {
            lift = 0;
        }
        g.fill(cx - 12, base - 18 - lift, cx + 12, base - 4 - lift, SmithStep.IRON_LIGHT);
        g.fill(cx - 2, base - 60 - lift, cx + 2, base - 18 - lift, SmithStep.WOOD);
        boolean window = offset(now) <= WINDOW_MS;
        g.fill(x + WIDTH - 30, y + 24, x + WIDTH - 18, y + 36, window ? SmithStep.GOOD : SmithStep.TRACK);
        Component line = lastAccuracy < 0 ? Component.translatable("gui.halgventmod.striker.hint")
                : Component.translatable("gui.halgventmod.striker.status", hits, lastAccuracy);
        TextFit.drawCentered(g, font, line, cx, y + HEIGHT - 22, WIDTH - 20, SmithStep.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            long now = System.currentTimeMillis();
            if (lastHit >= 0 && now - lastHit < Striker.MIN_INTERVAL * 50L) {
                return true;
            }
            lastHit = now;
            struckAt = now;
            long off = offset(now);
            lastAccuracy = (int) Math.max(0, Math.round(100 - off * 100.0 / (BEAT_MS / 2.0)));
            if (off <= WINDOW_MS) {
                lastAccuracy = Math.max(lastAccuracy, 85);
            }
            hits++;
            PacketDistributor.sendToServer(new StrikerHitPayload(pos, lastAccuracy));
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_PLACE, 0.6F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new SmithResultPayload(pos, Minigame.STRIKER, false, List.of()));
        }
        super.onClose();
    }
}
