package com.nrdhvnstd.halgventmod.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.storage.Storage;
import com.nrdhvnstd.halgventmod.storage.StorageArea;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Любое окно (инвентарь, сундук, печь, окна других модов), которое добавляет ячейку бывшего рюкзака
 * (инвентарь игрока, 9–35), получает вместо неё ячейку хранилища на том же месте и под тем же номером.
 * Так сумки видны во всех окнах, а Shift+клик из сундука кладёт в них.
 *
 * Shift+клик в инвентарь игрока (диапазон, где есть ячейки сумок) идёт в порядке ТЗ 6.2, а не ванильном:
 * сначала дополнить такие же стопки, потом свободные ячейки — карманы → подсумок 1 → подсумок 2 → спина.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    // Порядок групп ячеек при раскладке
    private static final int ORDER_POCKETS = 0;
    private static final int ORDER_STORAGE = 1; // + номер области в StorageArea.FILL_ORDER
    private static final int ORDER_OTHER = 100;  // после всех областей

    @Shadow @Final public NonNullList<Slot> slots;

    @ModifyVariable(method = "addSlot", at = @At("HEAD"), argsOnly = true)
    private Slot halgventmod$redirectHiddenSlot(Slot slot) {
        if (slot.container instanceof Inventory inventory && !(slot instanceof StorageSlot)) {
            int index = slot.getContainerSlot() - StorageArea.VANILLA_FIRST;
            if (index >= 0 && index < StorageArea.INDEX_COUNT) {
                return new StorageSlot(inventory.player, index, slot.x, slot.y);
            }
        }
        return slot;
    }

    // Та же ванильная раскладка в два прохода, только по ячейкам, отсортированным по 6.2
    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void halgventmod$storageOrder(ItemStack stack, int start, int end, boolean reverse,
                                          CallbackInfoReturnable<Boolean> cir) {
        List<Slot> ordered = new ArrayList<>(Math.max(0, end - start));
        boolean hasStorage = false;
        for (int i = start; i < end; i++) {
            Slot slot = slots.get(i);
            ordered.add(slot);
            hasStorage |= slot instanceof StorageSlot;
        }
        if (!hasStorage) {
            return; // не инвентарь игрока — как в ванили
        }
        ordered.sort(Comparator.comparingInt(AbstractContainerMenuMixin::halgventmod$group)
                .thenComparingInt(AbstractContainerMenuMixin::halgventmod$place));

        boolean moved = false;
        if (stack.isStackable()) {
            for (Slot slot : ordered) {
                if (stack.isEmpty()) {
                    break;
                }
                ItemStack inside = slot.getItem();
                if (!inside.isEmpty() && ItemStack.isSameItemSameComponents(stack, inside)) {
                    int max = slot.getMaxStackSize(inside);
                    int add = Math.min(stack.getCount(), max - inside.getCount());
                    if (add > 0) {
                        stack.shrink(add);
                        inside.grow(add);
                        slot.setChanged();
                        moved = true;
                    }
                }
            }
        }
        if (!stack.isEmpty()) {
            for (Slot slot : ordered) {
                if (slot.getItem().isEmpty() && slot.mayPlace(stack)) {
                    slot.setByPlayer(stack.split(Math.min(stack.getCount(), slot.getMaxStackSize(stack))));
                    slot.setChanged();
                    moved = true;
                    break; // как в ванили: одна стопка за раз, остальное — следующим вызовом
                }
            }
        }
        cir.setReturnValue(moved);
    }

    private static int halgventmod$group(Slot slot) {
        if (slot instanceof StorageSlot storage) {
            for (int i = 0; i < StorageArea.FILL_ORDER.length; i++) {
                if (StorageArea.FILL_ORDER[i] == storage.area()) {
                    return ORDER_STORAGE + i;
                }
            }
        }
        if (slot.container instanceof Inventory && slot.getContainerSlot() < Storage.POCKETS) {
            return ORDER_POCKETS;
        }
        return ORDER_OTHER;
    }

    // Внутри группы: карманы по номеру, сумки — по месту в сумке, прочее — как было (сортировка устойчивая)
    private static int halgventmod$place(Slot slot) {
        if (slot instanceof StorageSlot storage) {
            return storage.local();
        }
        if (slot.container instanceof Inventory && slot.getContainerSlot() < Storage.POCKETS) {
            return slot.getContainerSlot();
        }
        return 0;
    }
}
