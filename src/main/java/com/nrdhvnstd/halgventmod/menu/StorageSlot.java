package com.nrdhvnstd.halgventmod.menu;

import com.nrdhvnstd.halgventmod.storage.Storage;
import com.nrdhvnstd.halgventmod.storage.StorageArea;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Ячейка хранилища в любом окне: смотрит в надетую сумку (спина, подсумок 1, подсумок 2).
 * Встаёт вместо ванильных ячеек 9–35 во всех окнах (AbstractContainerMenuMixin), поэтому номера
 * ячеек окна не меняются и сервер с клиентом остаются согласны.
 *
 * Своей копии вещи не хранит: каждый раз читает живой список сумки, которая надета прямо сейчас.
 * Сумку сняли — ячейка тут же закрыта.
 */
public class StorageSlot extends Slot {
    private static final SimpleContainer NO_CONTAINER = new SimpleContainer(0);

    private final Player player;
    private final StorageArea area;
    private final int local; // место внутри области, по строкам

    public StorageSlot(Player player, int index, int x, int y) {
        super(NO_CONTAINER, index, x, y);
        this.player = player;
        this.area = StorageArea.of(index);
        this.local = area.localIndex(index);
    }

    public StorageArea area() {
        return area;
    }

    /** Место внутри своей области: слева направо, сверху вниз. */
    public int local() {
        return local;
    }

    /** Открыта ли ячейка: надета сумка и её вместимость дотягивается до этого места. */
    public boolean isOpen() {
        return local < Storage.open(player, area);
    }

    /** Подсказка над пустой ячейкой: открытая — какая это область, закрытая — чем она открывается. */
    public Component hint() {
        return isOpen() ? Component.translatable(area.langKey()) : lockedHint();
    }

    /** Подсказка над закрытой ячейкой: чем она открывается. */
    public Component lockedHint() {
        return Component.translatable(area == StorageArea.BACK
                ? "gui.halgventmod.storage.locked.back"
                : "gui.halgventmod.storage.locked.pouch");
    }

    @Override
    public ItemStack getItem() {
        return isOpen() ? Storage.items(player, area).get(local) : ItemStack.EMPTY;
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    @Override
    public void set(ItemStack stack) {
        if (isOpen()) {
            Storage.items(player, area).set(local, stack);
            setChanged();
        } else if (!stack.isEmpty() && !player.level().isClientSide) {
            // Сюда ничего не кладут (mayPlace), но если кто-то положил в обход — не теряем вещь
            Storage.addOrDrop(player.getInventory(), stack);
        }
    }

    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        set(newStack);
    }

    // Окна меняют вещь на месте (grow/shrink) и зовут setChanged — тогда и записываем в сумку
    @Override
    public void setChanged() {
        if (isOpen()) {
            Storage.changed(player, area);
        }
    }

    @Override
    public ItemStack remove(int amount) {
        if (!isOpen()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = Storage.items(player, area).get(local).split(amount);
        setChanged();
        return taken;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // Надетую сумку — не в саму себя (при Shift+клике она ещё надета, пока её перекладывают)
        return isOpen() && Storage.canStore(stack) && stack != Storage.holder(player, area);
    }
}
