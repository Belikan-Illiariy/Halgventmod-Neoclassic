package com.nrdhvnstd.halgventmod.menu;

import com.nrdhvnstd.halgventmod.smithing.Crucible;
import com.nrdhvnstd.halgventmod.smithing.ForgeOp;
import com.nrdhvnstd.halgventmod.smithing.Smithing;
import com.nrdhvnstd.halgventmod.smithing.WorkshopBlockEntity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

/** Ячейка тигля (кузнечный и сыродутный горн): один тигель; с жидким металлом голыми руками не взять — нужны клещи. */
public class CrucibleSlot extends StationMenu.FilteredSlot {
    public CrucibleSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPickup(Player player) {
        if (Crucible.liquid(getItem(), player.level())
                && WorkshopBlockEntity.toolSlot(player, ForgeOp.Tool.TONGS) < 0) {
            if (!player.level().isClientSide) {
                Smithing.say(player, "crucible_need_tongs");
            }
            return false;
        }
        return true;
    }
}
