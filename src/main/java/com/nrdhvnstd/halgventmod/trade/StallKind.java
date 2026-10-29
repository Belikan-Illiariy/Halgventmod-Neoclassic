package com.nrdhvnstd.halgventmod.trade;

import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/** Вид прилавка: что на нём можно держать, продавать и скупать. Остальное прилавок не принимает. */
public enum StallKind {
    //                         ячеек  в ячейке  вид
    WEAPONS("weapon_stall",    4,     1),    // настенная стойка: 4 крюка
    ARMOR("armor_stall",       ArmorSlot.COUNT, 1), // манекен: ячейки как броня игрока, каждая — свой слот
    FOOD("food_stall",         8,     64),   // витрина: 3 полки
    GOODS("goods_stall",       12,    64),   // прилавок товаров: 3 ступени по 4
    TAP("tap_stall",           8,     1),    // бочонок с краном: питьё внутри, в ячейках — кружки торговца (Mug)
    STORAGE("merchant_storage", 27,    64);   // хранилище: запас, из него торговец пополняет прилавки

    private final String id;
    private final int slots;
    private final int perSlot;

    StallKind(String id, int slots, int perSlot) {
        this.id = id;
        this.slots = slots;
        this.perSlot = perSlot;
    }

    /** Сколько ячеек товара. */
    public int slots() {
        return slots;
    }

    /** Сколько вещей в ячейке (оружие и броня — по одной: висят на стойке и манекене). */
    public int perSlot() {
        return perSlot;
    }

    /** Подходит ли вещь в эту ячейку: у манекена каждая ячейка — свой слот брони (как у игрока). */
    public boolean accepts(ItemStack stack, int slot) {
        if (!accepts(stack)) {
            return false;
        }
        return this != ARMOR || (slot >= 0 && slot < ArmorSlot.COUNT && ModArmor.slotOf(stack) == ArmorSlot.values()[slot]);
    }

    /** Имя блока в реестре. */
    public String id() {
        return id;
    }

    public Component title() {
        return Component.translatable("block.halgventmod." + id);
    }

    /** Подходит ли вещь этому прилавку. Прочие товары — всё, что не оружие, не броня и не еда. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (this) {
            case WEAPONS -> GearQuality.isWeapon(stack);
            case ARMOR -> ModArmor.slotOf(stack) != null; // броня мода: у неё есть слот на манекене
            case FOOD -> isFood(stack);
            case GOODS -> !GearQuality.isWeapon(stack) && !GearQuality.isArmor(stack) && !isFood(stack);
            case TAP -> Mug.empty(stack);
            case STORAGE -> true;
        };
    }

    /** Питьё, которое льют в бочонок: пьётся (склянка, бутыль), но не посудина. */
    public static boolean isDrink(ItemStack stack) {
        return !stack.isEmpty() && stack.getUseAnimation() == UseAnim.DRINK && Pottery.drinkCapacity(stack) <= 0
                && Pottery.waterCapacity(stack) <= 0;
    }

    // Еда и питьё: съедобное, а также всё из «Пищи» и «Алкоголя» (вода, вино)
    private static boolean isFood(ItemStack stack) {
        if (stack.has(DataComponents.FOOD)) {
            return true;
        }
        ModItems.Definition def = ModItems.definition(stack.getItem());
        return def != null && (def.category() == Category.FOOD || def.category() == Category.ALCOHOL);
    }
}
