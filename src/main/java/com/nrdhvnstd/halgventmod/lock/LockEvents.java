package com.nrdhvnstd.halgventmod.lock;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Клики по дверям и сундукам. Работает и в режиме приключения.
 *
 * ПКМ чем угодно          — открыть, если не заперто; иначе «Заперто»
 * ПКМ навесным замком      — повесить замок (если его там ещё нет)
 * Shift + ПКМ ключом       — запереть или отпереть (связкой — тоже)
 * Shift + ПКМ пустой рукой — снять отпертый навесной замок
 * Набор слесаря            — врезной замок, сведения о замке, снятие любого замка (администрация)
 *
 * Обработчик срабатывает и на клиенте: там он только отменяет открывание, чтобы дверь не дёргалась,
 * а всё остальное делает сервер.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class LockEvents {
    private LockEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos at = event.getPos();
        BlockState state = level.getBlockState(at);
        if (!Locks.isLockable(state)) {
            return;
        }
        Player player = event.getEntity();
        List<BlockPos> place = Locks.place(level, at, state);
        LockData lock = Locks.find(level, place);

        // Вторая рука ничего не делает с замком, только не даёт открыть запертое
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (lock != null && lock.locked() && !player.isCreative()) {
                cancel(event, InteractionResult.FAIL);
            }
            return;
        }

        ItemStack main = player.getMainHandItem();
        boolean sneaking = player.isShiftKeyDown();
        ServerLevel server = level instanceof ServerLevel serverLevel ? serverLevel : null;
        ServerPlayer serverPlayer = player instanceof ServerPlayer sp ? sp : null;

        if (main.is(ModItems.LOCKSMITH_KIT.get())) {
            cancel(event, InteractionResult.SUCCESS);
            if (server != null && serverPlayer != null) {
                LockActions.locksmithKit(server, serverPlayer, at, place, lock, sneaking);
            }
            return;
        }

        if (lock == null) {
            if (main.is(ModItems.PADLOCK.get())) {
                cancel(event, InteractionResult.SUCCESS);
                if (server != null && serverPlayer != null) {
                    LockActions.installPadlock(server, serverPlayer, at, place, main);
                }
            }
            return; // замка нет — всё как обычно
        }

        if (sneaking && (main.is(ModItems.KEY.get()) || main.is(ModItems.KEY_RING.get()))) {
            cancel(event, InteractionResult.SUCCESS);
            if (server != null && serverPlayer != null) {
                LockActions.useKey(server, serverPlayer, at, place, lock, main);
            }
            return;
        }

        if (sneaking && main.isEmpty()) {
            cancel(event, InteractionResult.SUCCESS);
            if (server != null && serverPlayer != null) {
                LockActions.removePadlock(server, serverPlayer, at, place, lock);
            }
            return;
        }

        // Заперто: не открываем. В творческом режиме администрация проходит сквозь замки
        if (lock.locked() && !player.isCreative()) {
            cancel(event, InteractionResult.FAIL);
            if (server != null && serverPlayer != null) {
                LockActions.denied(server, serverPlayer, at);
            }
        }
    }

    // Дверь или сундук сломали (администрация в творческом) — убираем замок, чтобы он не достался новому блоку
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && Locks.isLockable(event.getState())) {
            BlockPos anchor = Locks.place(level, event.getPos(), event.getState()).get(0);
            if (Locks.get(level, anchor) != null) {
                Locks.put(level, List.of(anchor), null);
            }
        }
    }

    private static void cancel(PlayerInteractEvent.RightClickBlock event, InteractionResult result) {
        event.setCanceled(true);
        event.setCancellationResult(result);
    }
}