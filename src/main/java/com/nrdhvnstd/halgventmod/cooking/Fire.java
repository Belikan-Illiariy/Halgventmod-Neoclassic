package com.nrdhvnstd.halgventmod.cooking;

import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Огонь очага, костра и печи: {@link #SLOTS} ячейки топлива по одному полену, горит по одному — когда текущее
 * прогорит, берётся следующее. Температура тянется к цели (у очага — по числу поленьев), после «Затушить» — 0.
 * Недогоревшее полено остаётся: разожгут снова — догорит.
 */
public final class Fire {
    public static final int SLOTS = 3;

    private final SimpleContainer fuel = new SimpleContainer(SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return fuelValue(stack) > 0;
        }
    };
    private int burnLeft;
    private int burnTotal;
    private float temperature;

    /** Сколько тиков горения даёт предмет; 0 — не топливо. Общее для очага, костра, печи и горна. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(ModItems.FIREWOOD.get())) {
            return 2000; // дрова — основное топливо
        }
        if (stack.is(ItemTags.LOGS_THAT_BURN)) {
            return 1600;
        }
        if (stack.is(ItemTags.PLANKS)) {
            return 600;
        }
        if (stack.is(Items.STICK)) {
            return 150;
        }
        if (stack.is(Items.CHARCOAL) || stack.is(Items.COAL)) {
            return 3200;
        }
        if (stack.is(ModItems.BURNT_FOOD.get())) {
            return 400;
        }
        return 0;
    }

    public SimpleContainer fuel() {
        return fuel;
    }

    public int burnLeft() {
        return burnLeft;
    }

    public int burnTotal() {
        return burnTotal;
    }

    public float temperature() {
        return temperature;
    }

    public boolean hasFuel() {
        return burnLeft > 0 || !fuel.isEmpty();
    }

    /** Сколько поленьев в огне и в запасе (горящее — тоже). */
    public int loaded() {
        int n = burnLeft > 0 ? 1 : 0;
        for (int i = 0; i < SLOTS; i++) {
            n += fuel.getItem(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    /** Сколько тиков горения осталось всего: текущее полено и запас. */
    public int remaining() {
        int total = burnLeft;
        for (int i = 0; i < SLOTS; i++) {
            total += fuelValue(fuel.getItem(i));
        }
        return total;
    }

    /** Подложить полено в свободную ячейку. false — места нет или это не топливо. */
    public boolean add(ItemStack stack) {
        if (fuelValue(stack) <= 0) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (fuel.getItem(i).isEmpty()) {
                fuel.setItem(i, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    /** Сжечь столько единиц; когда полено прогорело — взять следующее. false — топливо кончилось. */
    public boolean burn(int amount) {
        if (burnLeft <= 0 && !next()) {
            return false;
        }
        burnLeft -= amount;
        if (burnLeft <= 0) {
            burnLeft = 0;
            return next();
        }
        return true;
    }

    private boolean next() {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = fuel.getItem(i);
            if (!stack.isEmpty()) {
                burnLeft = burnTotal = fuelValue(stack);
                fuel.setItem(i, ItemStack.EMPTY);
                return true;
            }
        }
        burnTotal = 0;
        return false;
    }

    /** Температура к цели: вверх на up, вниз на down за тик. */
    public void toward(float target, float up, float down) {
        temperature = temperature < target ? Math.min(target, temperature + up) : Math.max(target, temperature - down);
    }

    public void extinguish() {
        temperature = 0;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    /** Чем разжечь из инвентаря: огниво, огненный заряд или факел; −1 — нечем. */
    public static int lighterSlot(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- сохранение

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = fuel.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = (CompoundTag) stack.save(registries);
                entry.putByte("Slot", (byte) i);
                list.add(entry);
            }
        }
        tag.put("FuelSlots", list);
        tag.putInt("BurnLeft", burnLeft);
        tag.putInt("BurnTotal", burnTotal);
        tag.putFloat("Heat", temperature);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        for (int i = 0; i < SLOTS; i++) {
            fuel.setItem(i, ItemStack.EMPTY);
        }
        ListTag list = tag.getList("FuelSlots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < SLOTS) {
                fuel.setItem(slot, ItemStack.parseOptional(registries, entry));
            }
        }
        if (tag.contains("BurnLeft")) {
            burnLeft = tag.getInt("BurnLeft");
            burnTotal = Math.max(burnLeft, tag.getInt("BurnTotal"));
        } else {
            burnLeft = burnTotal = tag.getInt("Fuel"); // из прежних версий: весь запас — одно «полено»
        }
        temperature = tag.getFloat("Heat");
    }
}
