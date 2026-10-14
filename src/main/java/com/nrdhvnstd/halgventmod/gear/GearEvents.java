package com.nrdhvnstd.halgventmod.gear;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorEvents;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModAttributes;
import com.nrdhvnstd.halgventmod.storage.Storage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Снаряжение на игроке: синхронизация, прибавка к грузоподъёмности от сумки на спине,
 * выпадение при смерти и страховка скрытых ячеек 9–35.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class GearEvents {
    private static final int HIDDEN_CHECK_INTERVAL = 20; // раз в секунду
    private static final ResourceLocation BAG_CAPACITY =
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "worn_bag");

    private GearEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        // Вещи в скрытых ячейках — от старых сохранений (перенос) или от чужих модов и команд
        if (player.tickCount % HIDDEN_CHECK_INTERVAL == 0 && !player.isSpectator()) {
            Storage.evictHidden(player.getInventory());
        }
        if (!player.hasData(ModAttachments.GEAR)) {
            return;
        }
        GearInventory gear = player.getData(ModAttachments.GEAR);
        boolean changed = gear.consumeDirty();
        // Модификатор не сохраняется — после входа и возрождения его ставит проверка раз в секунду
        if (changed || player.tickCount % HIDDEN_CHECK_INTERVAL == 0) {
            updateCapacity(player, gear);
        }
        if (changed) {
            player.syncData(ModAttachments.GEAR);
        }
    }

    // Сумка на спине прибавляет к грузоподъёмности, пока надета
    private static void updateCapacity(Player player, GearInventory gear) {
        AttributeInstance capacity = player.getAttribute(ModAttributes.CARRY_CAPACITY);
        if (capacity == null) {
            return;
        }
        ModGear.GearInfo bag = ModGear.get(gear.get(GearSlot.BACK));
        double bonus = bag != null ? bag.carryBonusKg() : 0;
        AttributeModifier current = capacity.getModifier(BAG_CAPACITY);
        if (current != null && current.amount() == bonus) {
            return;
        }
        capacity.removeModifier(BAG_CAPACITY);
        if (bonus != 0) {
            capacity.addTransientModifier(new AttributeModifier(BAG_CAPACITY, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // Смерть: снаряжение выпадает, сумки — целиком, с вещами внутри. С keepInventory остаётся (copyOnDeath).
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.hasData(ModAttachments.GEAR)) {
            return;
        }
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        GearInventory gear = player.getData(ModAttachments.GEAR);
        for (GearSlot slot : GearSlot.values()) {
            ItemStack stack = gear.get(slot);
            if (!stack.isEmpty()) {
                event.getDrops().add(ArmorEvents.scattered(player, stack));
                gear.set(slot, ItemStack.EMPTY);
            }
        }
    }
}
