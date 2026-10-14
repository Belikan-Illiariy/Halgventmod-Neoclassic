package com.nrdhvnstd.halgventmod.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Серп и коса: срезают растения любой стадии (что именно — тег halgventmod:sickle_harvestable,
 * в режиме приключения его разрешает компонент can_break из FarmingEvents).
 * Серп — один блок, коса — ряд 3×1 поперёк взгляда. Каждый срезанный блок — 1 прочности.
 */
public class HarvestToolItem extends Item {
    // Сколько соседних блоков срезается в каждую сторону от того, по которому ударили
    private final int sideReach;
    // Соседей коса срезает через destroyBlock, а он снова зовёт mineBlock — без этого флага ушли бы в рекурсию
    private static boolean sweeping;

    public HarvestToolItem(Properties properties, int sideReach) {
        super(properties);
        this.sideReach = sideReach;
    }

    public static HarvestToolItem sickle(Properties properties) {
        return new HarvestToolItem(properties, 0);
    }

    public static HarvestToolItem scythe(Properties properties) {
        return new HarvestToolItem(properties, 1);
    }

    // Ванильный износ инструментов не срабатывает: растения ломаются мгновенно (прочность блока 0)
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!state.is(FarmingRules.HARVESTABLE)) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
        if (sideReach > 0 && !sweeping && !stack.isEmpty() && miner instanceof ServerPlayer player) {
            sweep(player, pos);
        }
        return true;
    }

    // Обычная ломка блока от имени игрока: добыча, события защиты территорий, проверка режима приключения
    private void sweep(ServerPlayer player, BlockPos center) {
        Direction side = player.getDirection().getClockWise();
        sweeping = true;
        try {
            for (int i = -sideReach; i <= sideReach; i++) {
                if (i == 0) {
                    continue;
                }
                BlockPos at = center.relative(side, i);
                BlockState target = player.level().getBlockState(at);
                if (target.is(FarmingRules.HARVESTABLE) && player.gameMode.destroyBlock(at)) {
                    // Частицы ломки сервер шлёт всем, кроме самого игрока: тот обычно рисует их сам
                    player.connection.send(new ClientboundLevelEventPacket(
                            LevelEvent.PARTICLES_DESTROY_BLOCK, at, Block.getId(target), false));
                }
            }
        } finally {
            sweeping = false;
        }
    }
}
