package com.nrdhvnstd.halgventmod.storage;

import java.util.List;
import java.util.function.Predicate;

import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.gear.ModGear;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Хранилище игрока: карманы (ванильный хотбар 0–8), подсумки и спина.
 * Единственный путь «положить вещь игроку» мимо окон: через него идут подбор, /give,
 * возврат вещи с курсора (InventoryMixin).
 */
public final class Storage {
    public static final int POCKETS = Inventory.getSelectionSize(); // 9
    private static final int POP_TIME = 5; // как у ванили: вещь «подпрыгивает» в хотбаре
    // Ячейки снаряжения с вещами внутри, которые чистит /clear
    private static final GearSlot[] CLEARED_SLOTS = {GearSlot.BACK, GearSlot.POUCH_1, GearSlot.POUCH_2, GearSlot.QUIVER};

    private Storage() {}

    /**
     * Творческий режим или администратор: открыты все 27 ячеек хранилища, сумки ничего не меняют.
     * Вещи тогда лежат в самих ванильных ячейках 9–35, а не в сумках; содержимое надетых сумок не трогается.
     * Перестал быть администратором или вышел из творческого — вещи из 9–35 раскладываются по сумкам (evictHidden).
     */
    public static boolean unrestricted(Player player) {
        return player.isCreative() || Permissions.isAdmin(player);
    }

    /** Ванильная ячейка инвентаря для номера сетки хранилища 0–26 (у творческого и администратора). */
    public static int vanillaSlot(int index) {
        return StorageArea.VANILLA_FIRST + index;
    }

    /** Ячейки области (живой список надетой сумки; пустой, если сумки нет). */
    public static NonNullList<ItemStack> items(Player player, StorageArea area) {
        return player.getData(ModAttachments.GEAR).items(area.gearSlot());
    }

    /** Надетая вещь, которая открывает область. */
    public static ItemStack holder(Player player, StorageArea area) {
        return player.getData(ModAttachments.GEAR).get(area.gearSlot());
    }

    /** Сколько ячеек области открыто. */
    public static int open(Player player, StorageArea area) {
        return Math.min(area.size(), items(player, area).size());
    }

    /** Содержимое области поменяли на месте — записать в сумку. */
    public static void changed(Player player, StorageArea area) {
        player.getData(ModAttachments.GEAR).contentsChanged(area.gearSlot());
    }

    /**
     * Можно ли положить вещь внутрь сумки.
     * Сумку или подсумок с вещами — нельзя (ТЗ 4.4), пустые — можно: они сложены.
     */
    public static boolean canStore(ItemStack stack) {
        return ModGear.containerOf(stack) == null || !LiveContents.hasItems(stack);
    }

    /**
     * Положить вещь игроку (ТЗ 6.2): дополнить такие же стопки (рука, вторая рука, карманы, подсумки, спина), стрелы — в колчан,
     * потом свободные ячейки: карманы → подсумок 1 → подсумок 2 → спина.
     * Что не влезло, остаётся в stack. true — положили хоть что-то (как ванильный Inventory.add).
     */
    public static boolean add(Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Player player = inventory.player;
        int before = stack.getCount();

        if (stack.isStackable()) {
            topUp(inventory, inventory.selected, stack);
            topUp(inventory, Inventory.SLOT_OFFHAND, stack);
            for (int i = 0; i < POCKETS && !stack.isEmpty(); i++) {
                topUp(inventory, i, stack);
            }
            if (unrestricted(player)) {
                for (int i = 0; i < StorageArea.INDEX_COUNT && !stack.isEmpty(); i++) {
                    topUp(inventory, vanillaSlot(i), stack);
                }
            }
            for (StorageArea area : unrestricted(player) ? new StorageArea[0] : StorageArea.FILL_ORDER) {
                if (stack.isEmpty()) {
                    break;
                }
                if (topUp(items(player, area), open(player, area), stack)) {
                    changed(player, area);
                }
            }
        }
        Quiver.insert(player, stack); // стрелы — в колчан раньше свободных карманов

        for (int i = 0; i < POCKETS && !stack.isEmpty(); i++) {
            if (inventory.items.get(i).isEmpty()) {
                inventory.items.set(i, stack.split(stack.getMaxStackSize()));
                inventory.items.get(i).setPopTime(POP_TIME);
            }
        }
        if (unrestricted(player)) {
            for (int i = 0; i < StorageArea.INDEX_COUNT && !stack.isEmpty(); i++) {
                if (inventory.items.get(vanillaSlot(i)).isEmpty()) {
                    inventory.items.set(vanillaSlot(i), stack.split(stack.getMaxStackSize()));
                }
            }
        } else if (canStore(stack)) {
            for (StorageArea area : StorageArea.FILL_ORDER) {
                if (stack.isEmpty()) {
                    break;
                }
                if (fillEmpty(items(player, area), open(player, area), stack)) {
                    changed(player, area);
                }
            }
        }

        if (!stack.isEmpty() && stack.getCount() == before && player.hasInfiniteMaterials()) {
            stack.setCount(0); // как в ванили: творческому всё «влезает»
            return true;
        }
        return stack.getCount() < before;
    }

    /** Положить, а что не влезло — бросить под ноги. */
    public static void addOrDrop(Inventory inventory, ItemStack stack) {
        add(inventory, stack);
        if (!stack.isEmpty()) {
            inventory.player.drop(stack, false);
        }
    }

    // Дополнить стопку в ячейке инвентаря
    private static void topUp(Inventory inventory, int slot, ItemStack stack) {
        ItemStack target = inventory.getItem(slot);
        if (stack.isEmpty() || target.isEmpty() || !ItemStack.isSameItemSameComponents(target, stack)) {
            return;
        }
        int moved = Math.min(stack.getCount(), inventory.getMaxStackSize(target) - target.getCount());
        if (moved > 0) {
            target.grow(moved);
            target.setPopTime(POP_TIME);
            stack.shrink(moved);
        }
    }

    private static boolean topUp(List<ItemStack> items, int open, ItemStack stack) {
        boolean changed = false;
        for (int i = 0; i < open && !stack.isEmpty(); i++) {
            ItemStack target = items.get(i);
            if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, stack)) {
                int moved = Math.min(stack.getCount(), target.getMaxStackSize() - target.getCount());
                if (moved > 0) {
                    target.grow(moved);
                    stack.shrink(moved);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean fillEmpty(List<ItemStack> items, int open, ItemStack stack) {
        boolean changed = false;
        for (int i = 0; i < open && !stack.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack.split(stack.getMaxStackSize()));
                changed = true;
            }
        }
        return changed;
    }

    /**
     * /clear и подсчёт вещей игрока: то же для содержимого надетых сумок, подсумков и колчана.
     * maxItems: -1 — без предела, 0 — только посчитать. Возвращает, сколько нашлось (убрано).
     */
    public static int clearOrCount(Player player, Predicate<ItemStack> predicate, int maxItems) {
        boolean simulate = maxItems == 0;
        GearInventory gear = player.getData(ModAttachments.GEAR);
        int found = 0;
        for (GearSlot slot : CLEARED_SLOTS) {
            boolean changed = false;
            for (ItemStack stack : gear.items(slot)) {
                int left = maxItems < 0 ? -1 : maxItems - found;
                int n = ContainerHelper.clearOrCountMatchingItems(stack, predicate, left, simulate);
                found += n;
                changed |= n > 0 && !simulate;
            }
            if (changed) {
                gear.contentsChanged(slot);
            }
        }
        return found;
    }

    /** Вещи, попавшие в скрытые ячейки 9–35 (чужой мод, команда) — разложить или бросить под ноги. */
    public static void evictHidden(Inventory inventory) {
        if (unrestricted(inventory.player)) {
            return; // у творческого и администратора 9–35 — открытое хранилище
        }
        for (int i = StorageArea.VANILLA_FIRST; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.items.get(i);
            if (!stack.isEmpty()) {
                inventory.items.set(i, ItemStack.EMPTY);
                addOrDrop(inventory, stack);
            }
        }
    }
}
