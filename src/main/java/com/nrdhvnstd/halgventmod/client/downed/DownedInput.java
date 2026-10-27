package com.nrdhvnstd.halgventmod.client.downed;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.UseHint;
import com.nrdhvnstd.halgventmod.client.social.SocialInput;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.network.SocialActionPayload;
import com.nrdhvnstd.halgventmod.network.SurrenderPayload;
import com.nrdhvnstd.halgventmod.social.Social;
import com.nrdhvnstd.halgventmod.social.SocialAction;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клавиша E и руки поверженного и несущего (docs/TZ_downed_and_hp.md, 3, 6). Поверженный: удержание E дольше
 * SURRENDER_HOLD_TICKS — «Сдаться»; не бьёт, не использует, не листает хотбар, инвентарь и другие окна не открываются.
 * Несущий: E — бросить; не бьёт, не использует, не выбрасывает, не листает хотбар. Сервер проверяет то же (Downed).
 * Ещё здесь — «перевязаться»: E без цели с повязкой или мазью в руке (Social.canHealSelf).
 * Клавишу E опрашивает PickupInput: пока руки заняты, нажатие целиком наше.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class DownedInput {
    public static final int SURRENDER_HOLD_TICKS = 40; // 2 с — случайно не сдаться ⚠️

    private static int holdTicks;
    private static boolean swallowing; // нажатие началось, пока руки были заняты: до отпускания E — наше
    private static boolean sent;
    private static boolean wasDown;

    private DownedInput() {}

    /** Доля удержания до «Сдаться», 0–1. */
    public static float surrenderProgress() {
        return Math.min(holdTicks / (float) SURRENDER_HOLD_TICKS, 1);
    }

    /** Клавиша E (PickupInput): true — нажатие наше, ни подбора, ни меню, ни блока. */
    public static boolean handleKey(LocalPlayer player, boolean down) {
        boolean downed = Downed.isDowned(player);
        Player carried = Downed.carried(player);
        boolean busy = downed || carried != null;
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (!down) {
            holdTicks = 0;
            swallowing = false;
            sent = false;
            return busy;
        }
        if (pressed && busy) {
            swallowing = true;
        } else if (!swallowing) {
            return busy; // E держали ещё до падения или до броска — это нажатие ничего не делает
        }
        if (downed) {
            if (++holdTicks >= SURRENDER_HOLD_TICKS && !sent) {
                PacketDistributor.sendToServer(SurrenderPayload.INSTANCE);
                sent = true;
            }
        } else if (carried != null && pressed) {
            PacketDistributor.sendToServer(new SocialActionPayload(carried.getId(), SocialAction.DROP));
        }
        return true;
    }

    /** E без цели перевяжет: повязка или мазь в руке, есть что лечить, под прицелом нет ни игрока, ни блока с действием. */
    public static boolean selfHealReady(Minecraft minecraft, LocalPlayer player) {
        if (!Social.canHealSelf(player) || SocialInput.target() != null || SocialInput.menu() != null) {
            return false;
        }
        return !(minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && minecraft.level != null && UseHint.usable(minecraft.level.getBlockState(hit.getBlockPos())));
    }

    /** E без цели — перевязать себя (шкала — как у социального меню). */
    public static void healSelf(LocalPlayer player) {
        PacketDistributor.sendToServer(new SocialActionPayload(player.getId(), SocialAction.HEAL));
    }

    // До ванильной обработки клавиш: выбросить, сменить руку, цифры хотбара; поверженному — и инвентарь
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !Downed.busy(player)) {
            return;
        }
        drain(minecraft.options.keyDrop);
        drain(minecraft.options.keySwapOffhand);
        for (KeyMapping key : minecraft.options.keyHotbarSlots) {
            drain(key);
        }
        if (Downed.isDowned(player)) {
            drain(minecraft.options.keyInventory);
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // нажатие пропадает
        }
    }

    // ЛКМ, ПКМ, колёсико-выбор — ни удара, ни использования, ни взмаха
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && Downed.busy(player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    // Колёсико не листает хотбар
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null && Downed.busy(minecraft.player)) {
            event.setCanceled(true);
        }
    }

    // Поверженному не открыть ни инвентарь, ни сундук; чат и пауза — можно
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && Downed.isDowned(player) && event.getNewScreen() instanceof AbstractContainerScreen<?>) {
            event.setCanceled(true);
        }
    }
}
