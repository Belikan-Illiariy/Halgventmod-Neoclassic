package com.nrdhvnstd.halgventmod.menu;

import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.gear.ModGear;
import com.nrdhvnstd.halgventmod.storage.ContainerType;
import com.nrdhvnstd.halgventmod.storage.LiveContents;
import com.nrdhvnstd.halgventmod.storage.Storage;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Ячейка открытого мешочка в окне инвентаря. Мешочек лежит в какой-то ячейке окна (holderSlot);
 * пока он там, ячейка смотрит в его содержимое. Мешочек переложили — панель закрывается.
 */
public class PurseSlot extends Slot {
    private static final SimpleContainer NO_CONTAINER = new SimpleContainer(0);

    private final Supplier<Slot> holderSlot; // ячейка с открытым мешочком; null — ни один не открыт
    private final LiveContents contents;
    private final int row;

    public PurseSlot(Supplier<Slot> holderSlot, LiveContents contents, int row, int x, int y) {
        super(NO_CONTAINER, row, x, y);
        this.holderSlot = holderSlot;
        this.contents = contents;
        this.row = row;
    }

    private ItemStack purse() {
        Slot slot = holderSlot.get();
        ItemStack stack = slot != null ? slot.getItem() : ItemStack.EMPTY;
        return ModGear.containerOf(stack) == ContainerType.PURSE ? stack : ItemStack.EMPTY;
    }

    private NonNullList<ItemStack> items() {
        return contents.of(purse());
    }

    public boolean isOpen() {
        return row < items().size();
    }

    @Override
    public ItemStack getItem() {
        return isOpen() ? items().get(row) : ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    @Override
    public void set(ItemStack stack) {
        if (isOpen()) {
            items().set(row, stack);
            setChanged();
        }
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        set(newStack);
    }

    // Записать в мешочек, а ячейке, где он лежит, сказать, что он изменился (сумка запишет его в себя)
    @Override
    public void setChanged() {
        if (isOpen()) {
            contents.flush();
            holderSlot.get().setChanged();
        }
    }

    @Override
    public ItemStack remove(int amount) {
        if (!isOpen()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = items().get(row).split(amount);
        setChanged();
        return taken;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return isOpen() && Storage.canStoreInPurse(stack) && stack != purse();
    }

    // Пока мешочек не открыт или он меньше — ячейки нет совсем
    @Override
    public boolean isActive() {
        return isOpen();
    }
}
