package com.nrdhvnstd.halgventmod.storage;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.gear.ModGear;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Ячейки внутри одной вещи-контейнера (сумки, подсумка), с которыми работает окно.
 * Ванильные окна меняют вещь в ячейке на месте (grow/shrink) и потом зовут setChanged,
 * поэтому им нужен живой список, а компонент вещи неизменяемый. Здесь живой список
 * держится, пока вещь та же самая и её компонент не меняли в обход нас; flush() пишет его обратно.
 *
 * Защита от дюпа: список привязан к самому объекту вещи. Сумку сняли или подменили —
 * при следующем обращении список перечитывается из новой вещи, старый уже никуда не пишет.
 */
public final class LiveContents {
    private ItemStack holder = ItemStack.EMPTY;
    @Nullable
    private ItemContainerContents written; // что лежало в компоненте, когда список собирали или писали
    private NonNullList<ItemStack> items = NonNullList.create();

    /** Ячейки вещи; пустой список, если это не контейнер. */
    public NonNullList<ItemStack> of(ItemStack stack) {
        if (stack != holder || stack.get(ModComponents.BAG_CONTENTS.get()) != written) {
            holder = stack;
            written = stack.get(ModComponents.BAG_CONTENTS.get());
            items = read(stack);
        }
        return items;
    }

    /** Записать список в компонент вещи. */
    public void flush() {
        if (!holder.isEmpty()) {
            written = write(holder, items);
        }
    }

    /** Копия содержимого вещи, по её вместимости. */
    public static NonNullList<ItemStack> read(ItemStack stack) {
        NonNullList<ItemStack> list = NonNullList.withSize(ModGear.capacity(stack), ItemStack.EMPTY);
        ItemContainerContents contents = stack.get(ModComponents.BAG_CONTENTS.get());
        if (contents != null) {
            contents.copyInto(list);
        }
        return list;
    }

    /** Пустая коробка — без компонента, чтобы пустые сумки складывались в стопки и не отличались от новых. */
    @Nullable
    private static ItemContainerContents write(ItemStack stack, NonNullList<ItemStack> list) {
        ItemContainerContents contents = ItemContainerContents.fromItems(list);
        if (contents == ItemContainerContents.EMPTY) {
            stack.remove(ModComponents.BAG_CONTENTS.get());
            return null;
        }
        stack.set(ModComponents.BAG_CONTENTS.get(), contents);
        return stack.get(ModComponents.BAG_CONTENTS.get());
    }

    /** Есть ли внутри вещи хоть что-то. */
    public static boolean hasItems(ItemStack stack) {
        ItemContainerContents contents = stack.get(ModComponents.BAG_CONTENTS.get());
        return contents != null && contents.nonEmptyItems().iterator().hasNext();
    }
}
