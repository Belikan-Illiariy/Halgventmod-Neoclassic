package com.nrdhvnstd.halgventmod.gear;

import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Снаряжение: ПКМ с ним в руке — надеть в свою ячейку; если она занята — поменять местами. */
public class GearItem extends Item {
    public GearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        GearInventory gear = player.getData(ModAttachments.GEAR);
        GearSlot slot = GearRules.slotFor(gear, held);
        if (slot == null) {
            return InteractionResultHolder.fail(held);
        }
        Component refusal = GearRules.refusal(gear, slot, held);
        if (refusal == null && !gear.get(slot).isEmpty()) {
            refusal = GearRules.takeOffRefusal(gear, slot);
        }
        if (refusal != null) {
            ArmorRules.tell(player, refusal);
            return InteractionResultHolder.fail(held);
        }

        // Надевание решает сервер, клиент получит результат синхронизацией
        if (!level.isClientSide) {
            ItemStack worn = gear.get(slot);
            gear.set(slot, held.copyWithCount(1));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
            return InteractionResultHolder.success(worn); // снятая вещь — в руку (или пустая рука)
        }
        return InteractionResultHolder.success(held);
    }
}
