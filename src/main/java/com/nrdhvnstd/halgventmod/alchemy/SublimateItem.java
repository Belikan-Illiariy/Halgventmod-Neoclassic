package com.nrdhvnstd.halgventmod.alchemy;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Сулема — яд. В инвентаре ПКМ сулемой по клинку (мечу, топору, кинжалу…) — намазать: следующие
 * {@link Alchemy#COAT_HITS} удара отравляют (AlchemyEvents). Порошок тратится.
 */
public class SublimateItem extends Item {
    public SublimateItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack me, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        ItemStack blade = slot.getItem();
        if (!Alchemy.blade(blade)) {
            return false;
        }
        if (Alchemy.coat(blade) >= Alchemy.COAT_HITS) {
            if (!player.level().isClientSide) {
                Alchemy.say(player, "coat_full");
            }
            return true;
        }
        Alchemy.setCoat(blade, Alchemy.COAT_HITS);
        if (!player.getAbilities().instabuild) {
            me.shrink(1);
        }
        player.playSound(SoundEvents.HONEYCOMB_WAX_ON, 0.6F, 1.4F);
        if (!player.level().isClientSide) {
            Alchemy.say(player, "coated", blade.getHoverName(), Alchemy.COAT_HITS);
        }
        return true;
    }
}
