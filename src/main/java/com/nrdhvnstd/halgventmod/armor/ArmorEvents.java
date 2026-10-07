package com.nrdhvnstd.halgventmod.armor;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Броня на игроке: синхронизация после изменений и выпадение при смерти.
 * Первую синхронизацию (вход в игру, возрождение, смена измерения, игрок появился в поле зрения)
 * NeoForge делает сам — вложение помечено .sync(...).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ArmorEvents {
    private static final int DROP_PICKUP_DELAY = 40; // как у вещей, выпавших из инвентаря

    private ArmorEvents() {}

    // Изменения за тик уходят одним пакетом
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.hasData(ModAttachments.ARMOR)) {
            return;
        }
        if (player.getData(ModAttachments.ARMOR).consumeDirty()) {
            player.syncData(ModAttachments.ARMOR);
        }
    }

    // Смерть: броня выпадает, как инвентарь. С keepInventory остаётся и переносится при возрождении (copyOnDeath).
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.hasData(ModAttachments.ARMOR)) {
            return;
        }
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        ArmorInventory armor = player.getData(ModAttachments.ARMOR);
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = armor.get(slot);
            if (!stack.isEmpty()) {
                event.getDrops().add(scattered(player, stack));
                armor.set(slot, ItemStack.EMPTY);
            }
        }
    }

    // Разлёт во все стороны — как у ванильного выпадения инвентаря
    private static ItemEntity scattered(Player player, ItemStack stack) {
        ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), stack);
        entity.setPickUpDelay(DROP_PICKUP_DELAY);
        float speed = player.getRandom().nextFloat() * 0.5F;
        float angle = player.getRandom().nextFloat() * Mth.TWO_PI;
        entity.setDeltaMovement(-Mth.sin(angle) * speed, 0.2, Mth.cos(angle) * speed);
        return entity;
    }
}
