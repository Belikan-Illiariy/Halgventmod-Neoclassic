package com.nrdhvnstd.halgventmod.client.social;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.SocialActionPayload;
import com.nrdhvnstd.halgventmod.pickup.Pickup;
import com.nrdhvnstd.halgventmod.social.Social;
import com.nrdhvnstd.halgventmod.social.SocialAction;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Социальное меню на клиенте (docs/TZ_social_interactions.md, 2–3): игрок под прицелом в пределах 2 блоков —
 * подсказка «[E] Взаимодействовать» с RP-именем над ней (SocialMenuHud). Короткое E по игроку ничего не делает,
 * удержание дольше MENU_HOLD_TICKS открывает круговое меню в центре экрана. Мышь двигает не камеру, а виртуальный
 * курсор; отпустить E на пункте — выбрать, в центре («Отмена») или Esc — выйти без действия.
 * Ходить и прыгать в меню можно, бить и использовать — нет. Решает сервер (social/Social); здесь только выбор.
 * Клавишу E опрашивает PickupInput: вещь под прицелом важнее игрока, игрок — блока за ним.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class SocialInput {
    public static final int MENU_HOLD_TICKS = 6;        // 0,3 с — дольше короткого нажатия ⚠️
    public static final float MENU_RADIUS = 90;          // дальше центра виртуальный курсор не уходит, в пикселях интерфейса ⚠️
    public static final float MENU_CENTER_RADIUS = 24;   // центр — «Отмена» ⚠️
    private static final double RAY_EXTRA = 1.0;          // луч длиннее радиуса: до хитбокса он доходит позже ближайшей точки
    private static final double SCAN_RADIUS = 4.0;        // игроков ищем только поблизости
    private static final double NO_TURN = -1.0 / 3.0;     // чувствительность, при которой камера не поворачивается (MouseHandler.turnPlayer)

    /** Открытое меню: над кем, какие пункты, где виртуальный курсор (от центра экрана). */
    public static final class Menu {
        private final Player target;
        private final List<SocialAction> options;
        private float cursorX;
        private float cursorY;

        private Menu(Player target) {
            this.target = target;
            this.options = SocialAction.menuFor(target);
        }

        public List<SocialAction> options() {
            return options;
        }

        public float cursorX() {
            return cursorX;
        }

        public float cursorY() {
            return cursorY;
        }

        /** Номер пункта под курсором; -1 — центр («Отмена»). Пункт 0 — сверху, дальше по часовой стрелке. */
        public int hovered() {
            if (cursorX * cursorX + cursorY * cursorY < MENU_CENTER_RADIUS * MENU_CENTER_RADIUS) {
                return -1;
            }
            double step = 2 * Math.PI / options.size();
            double angle = Math.atan2(cursorY, cursorX) + Math.PI / 2; // от верха
            return Math.floorMod((int) Math.round(angle / step), options.size());
        }

        private void move(double dx, double dy) {
            cursorX += (float) dx;
            cursorY += (float) dy;
            float length = Mth.sqrt(cursorX * cursorX + cursorY * cursorY);
            if (length > MENU_RADIUS) {
                cursorX *= MENU_RADIUS / length;
                cursorY *= MENU_RADIUS / length;
            }
        }
    }

    @Nullable
    private static Player target;
    @Nullable
    private static Player holdTarget;
    private static int holdTicks;
    private static boolean wasDown;
    @Nullable
    private static Menu menu;

    private SocialInput() {}

    /** Игрок под прицелом, с которым можно взаимодействовать, или null. */
    @Nullable
    public static Player target() {
        return target;
    }

    @Nullable
    public static Menu menu() {
        return menu;
    }

    /** Каждый тик из PickupInput: цель — только если под прицелом нет вещи. */
    public static void update(Minecraft minecraft, @Nullable LocalPlayer player, boolean itemTargeted) {
        target = player != null && minecraft.screen == null && !itemTargeted ? findTarget(player) : null;
        if (menu != null && (player == null || minecraft.screen != null || !menu.target.isAlive() || menu.target.isRemoved()
                || !Pickup.inReach(player, menu.target, Social.INTERACT_REACH))) {
            menu = null; // отошёл дальше радиуса до отпускания E — меню закрыто, действие не начато (ТЗ 3.4)
        }
    }

    /** Клавиша E (PickupInput): true — нажатие наше, подбирать и открывать блок не нужно. */
    public static boolean handleKey(LocalPlayer player, boolean down) {
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (menu != null) {
            if (!down) {
                release(menu);
                menu = null;
            }
            return true;
        }
        if (pressed && target != null) {
            holdTarget = target;
            holdTicks = 0;
        }
        if (!down || holdTarget == null) {
            holdTarget = null;
            return false;
        }
        if (holdTarget != target) {
            holdTarget = null; // отвёл прицел до открытия меню — нажатие пропало
            return true;
        }
        if (++holdTicks >= MENU_HOLD_TICKS) {
            menu = new Menu(holdTarget);
            holdTarget = null;
        }
        return true;
    }

    private static void release(Menu open) {
        int hovered = open.hovered();
        if (hovered >= 0) {
            PacketDistributor.sendToServer(new SocialActionPayload(open.target.getId(), open.options.get(hovered)));
        }
    }

    // Ближайший игрок на луче взгляда до первого блока, в радиусе от глаз до хитбокса
    @Nullable
    private static Player findTarget(LocalPlayer player) {
        if (player.isSpectator()) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(Social.INTERACT_REACH + RAY_EXTRA));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player other : player.level().players()) {
            if (other.distanceToSqr(player) > SCAN_RADIUS * SCAN_RADIUS || !Social.canTarget(player, other)) {
                continue;
            }
            Optional<Vec3> hit = other.getBoundingBox().clip(eye, end);
            if (hit.isPresent() && Pickup.inReach(player, other, Social.INTERACT_REACH)) {
                double distance = eye.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    best = other;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    // В меню мышь двигает виртуальный курсор, камера стоит
    @SubscribeEvent
    public static void onTurn(CalculatePlayerTurnEvent event) {
        if (menu == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        menu.move(minecraft.mouseHandler.getXVelocity() * window.getGuiScaledWidth() / window.getScreenWidth(),
                minecraft.mouseHandler.getYVelocity() * window.getGuiScaledHeight() / window.getScreenHeight());
        event.setMouseSensitivity(NO_TURN);
        event.setCinematicCameraEnabled(false);
    }

    // Esc в меню — выйти без действия, а не пауза
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (menu != null && event.getNewScreen() instanceof PauseScreen) {
            menu = null;
            event.setCanceled(true);
        }
    }

    // В меню не бьют и не используют
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (menu != null) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }
}
