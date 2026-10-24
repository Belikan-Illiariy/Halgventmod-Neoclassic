package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.alchemy.Alchemy;
import com.nrdhvnstd.halgventmod.alchemy.AlchemyBlocks;
import com.nrdhvnstd.halgventmod.alchemy.RetortBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно реторты: «в колбу» и «добавка», полоса возгонки, жар под ретортой; «приёмник» (летучее) и «остаток».
 */
public class RetortMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int IN_X = 112, IN_Y = 22;
    public static final int ADD_X = 166, ADD_Y = 22;
    public static final int OUT_X = 112, OUT_Y = 76;
    public static final int RESIDUE_X = 166, RESIDUE_Y = 76;
    public static final int RETORT_START = PLAYER_END;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final RetortBlockEntity retort;

    public static RetortMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new RetortMenu(containerId, inventory, new SimpleContainer(RetortBlockEntity.SLOTS),
                new SimpleContainerData(RetortBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public RetortMenu(int containerId, Inventory inventory, RetortBlockEntity retort) {
        this(containerId, inventory, retort.slots(), retort.data(), retort.getBlockPos(), retort);
    }

    private RetortMenu(int containerId, Inventory inventory, Container slots, ContainerData data, BlockPos pos,
                       @Nullable RetortBlockEntity retort) {
        super(AlchemyBlocks.RETORT_MENU.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.retort = retort;
        addSlot(new FilteredSlot(slots, RetortBlockEntity.SLOT_IN, IN_X, IN_Y));
        addSlot(new FilteredSlot(slots, RetortBlockEntity.SLOT_ADD, ADD_X, ADD_Y));
        addSlot(new Slot(slots, RetortBlockEntity.SLOT_OUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new Slot(slots, RetortBlockEntity.SLOT_RESIDUE, RESIDUE_X, RESIDUE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public boolean stillValid(Player player) {
        return retort == null || Container.stillValidBlockEntity(retort, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        int slot = Alchemy.sublimation(stack) != null ? RetortBlockEntity.SLOT_IN
                : Alchemy.sublimationAdditive(stack) ? RetortBlockEntity.SLOT_ADD : -1;
        return slot >= 0 && moveItemStackTo(stack, RETORT_START + slot, RETORT_START + slot + 1, false);
    }
}
