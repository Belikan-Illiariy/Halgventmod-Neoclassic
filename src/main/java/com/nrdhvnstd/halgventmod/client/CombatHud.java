package com.nrdhvnstd.halgventmod.client;

import javax.annotation.Nullable;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Метка боя под серединой экрана (ТЗ боя, 12.3) — отдельных шкал боя нет: тонкая полоска показывает свой удар.
 * Замах — заполняется серым, заряд — золотым и пульсирует, удар — белая, окно комбо в начале возврата — зелёная
 * вспышка, возврат — гаснет; блок — синяя. Черта у середины экрана — сторона: откуда идёт свой удар, с какой стороны
 * поднят блок (StrikeSide, по движению мыши). Только с оружием боя в руке. Вокруг прицела — звезда сторон (star).
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

    // Звезда сторон и захват ⚠️
    private static final int STAR_GAP = 14, STAR_SIZE = 4;
    private static final int STAR_IDLE = 0x50FFFFFF, STAR_STANCE = 0xE0E8B848, STAR_INCOMING = 0xF0E04040;
    private static final int LOCK_GAP = 9, LOCK_LEN = 3, LOCK_COLOR = 0xC0E04040;
    private static final double INCOMING_RANGE = 5.0;
    private static final double INCOMING_FACING = 0.5;

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
        star(graphics, player, state, now);
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

    /**
     * Звезда сторон у прицела (как в KCD): четыре стрелки; золотая — своя стойка (CombatInput.stance), красная —
     * откуда блокировать замах противника рядом, который смотрит на тебя (сторона блока против его удара,
     * StrikeSide.blockedBy; укол — снизу). Видна в бою: в захвате цели, недавно бил или блокировал, на тебя замахнулись.
     * В захвате — уголки вокруг прицела.
     */
    private static void star(GuiGraphics graphics, LocalPlayer player, CombatState state, double now) {
        StrikeSide incoming = incoming(player);
        boolean locked = CombatLock.locked();
        if (!locked && incoming == null && state.phase == Phase.IDLE && !state.blocking && now > state.engagedUntil) {
            return;
        }
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        StrikeSide stance = CombatInput.stance();
        for (StrikeSide side : StrikeSide.values()) {
            int color = side == incoming ? pulse(STAR_INCOMING, now * 2) : side == stance ? STAR_STANCE : STAR_IDLE;
            arrow(graphics, cx, cy, side, color);
        }
        if (locked) {
            int g = LOCK_GAP;
            int l = LOCK_LEN;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    int x = cx + sx * g;
                    int y = cy + sy * g;
                    graphics.fill(Math.min(x, x - sx * l), y, Math.max(x, x - sx * l) + 1, y + 1, LOCK_COLOR);
                    graphics.fill(x, Math.min(y, y - sy * l), x + 1, Math.max(y, y - sy * l) + 1, LOCK_COLOR);
                }
            }
        }
    }

    // Стрелка наружу: треугольник из полосок
    private static void arrow(GuiGraphics graphics, int cx, int cy, StrikeSide side, int color) {
        for (int i = 0; i < STAR_SIZE; i++) {
            int half = STAR_SIZE - 1 - i;
            int d = STAR_GAP + i;
            switch (side) {
                case RIGHT -> graphics.fill(cx + d, cy - half, cx + d + 1, cy + half + 1, color);
                case LEFT -> graphics.fill(cx - d - 1, cy - half, cx - d, cy + half + 1, color);
                case UP -> graphics.fill(cx - half, cy - d - 1, cx + half + 1, cy - d, color);
                case DOWN -> graphics.fill(cx - half, cy + d, cx + half + 1, cy + d + 1, color);
            }
        }
    }

    // Чей замах идёт на меня: ближний противник в замахе или ударе, лицом ко мне → сторона, с которой его блокировать
    @Nullable
    private static StrikeSide incoming(LocalPlayer player) {
        StrikeSide best = null;
        double bestDistance = INCOMING_RANGE * INCOMING_RANGE;
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(INCOMING_RANGE),
                e -> e instanceof LivingEntity living && living.isAlive())) {
            CombatState other = entity.getData(ModAttachments.COMBAT);
            if (other.phase != Phase.WINDUP && other.phase != Phase.RELEASE) {
                continue;
            }
            Vec3 to = player.getEyePosition().subtract(entity.getEyePosition());
            double distance = to.lengthSqr();
            if (distance > bestDistance || to.normalize().dot(entity.getViewVector(1.0F)) < INCOMING_FACING) {
                continue;
            }
            bestDistance = distance;
            best = other.attack == AttackKind.STAB ? StrikeSide.DOWN : other.side.blockedBy();
        }
        return best;
    }

    // Заряд пульсирует: прозрачность туда-сюда
    private static int pulse(int color, double now) {
        int alpha = (int) (((color >>> 24) & 0xFF) * (0.6 + 0.4 * Math.sin(now * 0.8)));
        return (alpha << 24) | (color & 0xFFFFFF);
    }
}
