package com.nrdhvnstd.halgventmod.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно станции (очаг, печь, горн): внизу сумки, карманы и вторая рука игрока — ячейки 0–36, дальше ячейки станции.
 * Shift+клик: из станции — в карманы и сумки (через Slot.remove, чтобы печь и горн отдали готовое, а не сырое),
 * из инвентаря — куда примет станция ({@link #moveIntoStation}).
 */
public abstract class StationMenu extends AbstractContainerMenu {
    public static final int STEP = 18;
    public static final int PLAYER_END = 37; // 27 ячеек сумок, 9 карманов, вторая рука

    protected StationMenu(MenuType<?> type, int containerId, Inventory inventory, int pocketsX, int storageY) {
        super(type, containerId);
        // Ячейки 9–35 инвентаря становятся ячейками сумок (AbstractContainerMenuMixin)
        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(inventory, 9 + i, pocketsX + (i % 9) * STEP, storageY + (i / 9) * STEP));
        }
        int pocketsY = storageY + 3 * STEP + 4;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, pocketsX + col * STEP, pocketsY));
        }
        addSlot(new Slot(inventory, Inventory.SLOT_OFFHAND, pocketsX - 22, pocketsY));
    }

    /** Положить из инвентаря в станцию. */
    protected abstract boolean moveIntoStation(ItemStack stack);

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        if (index >= PLAYER_END) {
            ItemStack taken = slot.remove(slot.getItem().getCount());
            ItemStack original = taken.copy();
            slot.onTake(player, taken);
            moveItemStackTo(taken, 0, PLAYER_END, false);
            if (!taken.isEmpty()) {
                player.drop(taken, false);
            }
            return ItemStack.EMPTY; // одно перемещение за щелчок: не повторять
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveIntoStation(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, original);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
    }

    /** Ячейка станции, которая берёт только то, что примет её контейнер (canPlaceItem). */
    public static class FilteredSlot extends Slot {
        public FilteredSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
