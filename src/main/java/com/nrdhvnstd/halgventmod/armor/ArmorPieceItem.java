package com.nrdhvnstd.halgventmod.armor;

import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Вещь брони: ПКМ с ней в руке — надеть в свою ячейку; если ячейка занята — поменять местами. */
public class ArmorPieceItem extends Item {
    public ArmorPieceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ArmorSlot slot = ModArmor.slotOf(held);
        ArmorInventory armor = player.getData(ModAttachments.ARMOR);
        if (slot == null || !armor.isItemValid(slot.ordinal(), held)) {
            return InteractionResultHolder.fail(held);
        }
        Component refusal = ArmorRules.refusal(armor, slot, held);
        if (refusal != null) {
            ArmorRules.tell(player, refusal);
            return InteractionResultHolder.fail(held);
        }

        // Надевание решает сервер, клиент получит результат синхронизацией
        if (!level.isClientSide) {
            ItemStack worn = armor.get(slot);
            armor.set(slot, held.copyWithCount(1));
            ArmorSounds.equip(player, held);
            return InteractionResultHolder.success(worn); // снятая вещь — в руку (или пустая рука)
        }
        return InteractionResultHolder.success(held);
    }
}
