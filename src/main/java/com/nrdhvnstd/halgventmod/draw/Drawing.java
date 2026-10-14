package com.nrdhvnstd.halgventmod.draw;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Доставание предмета в руку (ТЗ 5.3): после смены предмета в основной руке им какое-то время нельзя
 * ни бить, ни пользоваться. Считают и сервер (он решает), и клиент своего игрока (чтобы не слать
 * заведомо отклонённые удары и растянуть анимацию, ItemInHandRendererMixin) — каждый по своим тикам.
 *
 * При быстрой прокрутке доставание начинается, только когда ячейка не менялась SETTLE_TICKS:
 * звук — один раз, на том кармане, где игрок остановился. Пустая рука и творческий режим — сразу.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Drawing {
    public static final int SETTLE_TICKS = 2;
    private static final float SOUND_VOLUME = 0.5F;
    private static final float SOUND_PITCH = 1.0F;

    private Drawing() {}

    /** Состояние доставания. Вложение без сохранения: после входа и возрождения предмет уже в руке. */
    public static final class State {
        private boolean seen;      // первый тик — запомнить, что в руке, без доставания
        private Item item = Items.AIR;
        private int slot = -1;
        private int changedAt;     // тик смены предмета
        private boolean started = true;
        private int startedAt;     // тик, когда пошли звук и подъём
        private int readyAt;       // с этого тика предметом можно пользоваться
    }

    /** Предмет в основной руке достан. */
    public static boolean ready(Player player) {
        State state = player.getData(ModAttachments.DRAW);
        return state.started && player.tickCount >= state.readyAt;
    }

    /** Насколько поднят предмет: 0 — ещё внизу, 1 — достан (для анимации на клиенте). */
    public static float progress(Player player, float partialTick) {
        State state = player.getData(ModAttachments.DRAW);
        if (!state.started) {
            return 0;
        }
        int duration = state.readyAt - state.startedAt;
        if (duration <= 0) {
            return 1;
        }
        return Mth.clamp((player.tickCount + partialTick - state.startedAt) / duration, 0, 1);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide && !player.isLocalPlayer()) {
            return; // чужие игроки на клиенте — не наше дело, решает сервер
        }
        State state = player.getData(ModAttachments.DRAW);
        ItemStack hand = player.getMainHandItem();
        Item item = hand.getItem();
        int slot = player.getInventory().selected;
        int now = player.tickCount;

        if (!state.seen || item != state.item || slot != state.slot) {
            boolean instant = !state.seen || hand.isEmpty() || player.getAbilities().instabuild;
            state.seen = true;
            state.item = item;
            state.slot = slot;
            state.changedAt = now;
            state.started = instant;
            state.startedAt = now;
            state.readyAt = now;
            return;
        }
        if (!state.started && now - state.changedAt >= SETTLE_TICKS) {
            DrawType type = ModDraw.of(hand);
            state.started = true;
            state.startedAt = now;
            state.readyAt = Math.max(now, state.changedAt + type.ticks());
            if (!player.level().isClientSide) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        type.sound(), SoundSource.PLAYERS, SOUND_VOLUME, SOUND_PITCH);
            }
        }
    }

    // ----- Пока не достан: ни удара, ни использования -----

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!ready(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // Ломать блок (бить по нему) — тоже удар
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!ready(event.getEntity()) && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && !ready(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    // Дверь, сундук, рычаг — можно; поставить блок или применить предмет к блоку — нет
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && !ready(event.getEntity())) {
            event.setUseItem(TriState.FALSE);
        }
    }

    // ПКМ по существу предметом (покормить, остричь, надеть седло)
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && !event.getItemStack().isEmpty() && !ready(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
}
