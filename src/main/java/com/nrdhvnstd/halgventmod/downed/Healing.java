package com.nrdhvnstd.halgventmod.downed;

import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Лечебные предметы (docs/TZ_downed_and_hp.md, 1, 5): здоровье само не растёт, восстанавливают только они.
 * Себя — предмет в руке, E без цели; поверженного — любой такой предмет из инвентаря, через меню E.
 * Голодание и Жажда урезают лечение (needs/Starvation).
 */
public final class Healing {
    public static final float BANDAGE = 15;        // ⚠️ числа — из ТЗ предметов, когда будет
    public static final float HEALING_SALVE = 30;  // ⚠️

    private Healing() {}

    /** Сколько здоровья даёт предмет; 0 — не лечит. */
    public static float amount(ItemStack stack) {
        if (stack.is(ModItems.BANDAGE.get())) {
            return BANDAGE;
        }
        if (stack.is(ModItems.HEALING_SALVE.get())) {
            return HEALING_SALVE;
        }
        return 0;
    }

    public static boolean heals(ItemStack stack) {
        return amount(stack) > 0;
    }

    /** Чем лечить другого: из руки, иначе первое подходящее в инвентаре. Пусто — нечем. */
    public static ItemStack find(Player healer) {
        if (heals(healer.getMainHandItem())) {
            return healer.getMainHandItem();
        }
        Inventory inventory = healer.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (heals(inventory.getItem(i))) {
                return inventory.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }
}
