package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.combat.CombatState;
import com.nrdhvnstd.halgventmod.combat.Phase;
import com.nrdhvnstd.halgventmod.combat.StrikeSide;
import com.nrdhvnstd.halgventmod.combat.WeaponProfiles;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Метка боя под серединой экрана (ТЗ боя, 12.3) — отдельных шкал боя нет: тонкая полоска показывает свой удар.
 * Замах — заполняется серым, заряд — золотым и пульсирует, удар — белая, окно комбо в начале возврата — зелёная
 * вспышка, возврат — гаснет; блок — синяя. Черта у середины экрана — сторона: откуда идёт свой удар, с какой стороны
 * поднят блок (StrikeSide, по движению мыши). Только с оружием боя в руке.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CombatHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "combat_mark");
    // Меняйте здесь
    private static final int WIDTH = 16, HEIGHT = 2, BELOW = 8;
    private static final int SIDE_GAP = 11, SIDE_LENGTH = 8, SIDE_THICK = 2; // черта стороны: от середины, длина, толщина
    private static final int BACK = 0x60000000;
    private static final int WINDUP = 0xC0C8C8C8, CHARGE = 0xE0E8B848, RELEASE = 0xF0FFFFFF, COMBO = 0xE060E070,
            RECOVERY = 0x80A0A0A0, GUARD = 0xD06090E0;

    private CombatHud() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, CombatHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null || !WeaponProfiles.handles(player)) {
            return;
        }
        CombatState state = player.getData(ModAttachments.COMBAT);
        double now = player.level().getGameTime() + delta.getGameTimeDeltaPartialTick(false);
        float length = Math.max(1, state.phaseEnd - state.phaseStart);
        float progress = Mth.clamp((float) (now - state.phaseStart) / length, 0, 1);
        float fill;
        int color;
        switch (state.phase) {
            case WINDUP -> {
                fill = state.heavy ? 1 : progress;
                color = state.heavy ? pulse(CHARGE, now) : WINDUP;
            }
            case RELEASE -> {
                fill = 1;
                color = RELEASE;
            }
            case RECOVERY -> {
                boolean combo = now - state.phaseStart < Combat.COMBO_WINDOW && state.prevPhase == Phase.RELEASE;
                fill = combo ? 1 : 1 - progress;
                color = combo ? COMBO : RECOVERY;
            }
            case IDLE -> {
                if (!state.blocking) {
                    return;
                }
                fill = 1;
                color = GUARD;
            }
            default -> {
                return;
            }
        }
        int x = (graphics.guiWidth() - WIDTH) / 2;
        int y = graphics.guiHeight() / 2 + BELOW;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACK);
        graphics.fill(x, y, x + Math.round(WIDTH * fill), y + HEIGHT, color);
        // Сторона: у блока — его, у удара — откуда идёт (у укола стороны нет)
        if (state.phase == Phase.IDLE || state.attack != AttackKind.STAB) {
            side(graphics, state.phase == Phase.IDLE ? state.blockSide : state.side, color);
        }
    }

    private static void side(GuiGraphics graphics, StrikeSide side, int color) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int half = SIDE_LENGTH / 2;
        switch (side) {
            case LEFT -> graphics.fill(cx - SIDE_GAP - SIDE_THICK, cy - half, cx - SIDE_GAP, cy + half, color);
            case RIGHT -> graphics.fill(cx + SIDE_GAP, cy - half, cx + SIDE_GAP + SIDE_THICK, cy + half, color);
            case UP -> graphics.fill(cx - half, cy - SIDE_GAP - SIDE_THICK, cx + half, cy - SIDE_GAP, color);
            case DOWN -> graphics.fill(cx - half, cy + SIDE_GAP + SIDE_THICK, cx + half, cy + SIDE_GAP + 2 * SIDE_THICK, color);
        }
    }

    // Заряд пульсирует: прозрачность туда-сюда
    private static int pulse(int color, double now) {
        int alpha = (int) (((color >>> 24) & 0xFF) * (0.6 + 0.4 * Math.sin(now * 0.8)));
        return (alpha << 24) | (color & 0xFFFFFF);
    }
}
