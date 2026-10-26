package com.nrdhvnstd.halgventmod.quality;

import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.registry.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Заточка на верстаке: затупленное оружие + точильный камень в любых клетках → то же оружие без «Затуплен»,
 * но с износом на Dullness.SHARPEN_COST от наибольшей прочности. Оружие, которое от этого сломалось бы, не точится.
 * Точильный камень остаётся в сетке, изношенный на единицу; стёрся до конца — пропадает.
 */
public class SharpenRecipe extends CustomRecipe {
    public SharpenRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return weaponSlot(input) >= 0;
    }

    // Клетка оружия, если в сетке ровно оружие и точильный камень; иначе -1
    private static int weaponSlot(CraftingInput input) {
        int weapon = -1;
        int whetstones = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.WHETSTONE.get())) {
                whetstones++;
            } else if (weapon < 0 && Dullness.applies(stack) && Dullness.isDull(stack)
                    && stack.getMaxDamage() - stack.getDamageValue() > Dullness.sharpenCost(stack)) {
                weapon = i;
            } else {
                return -1;
            }
        }
        return whetstones == 1 ? weapon : -1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int slot = weaponSlot(input);
        if (slot < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = input.getItem(slot).copyWithCount(1);
        Dullness.sharpen(result);
        result.setDamageValue(result.getDamageValue() + Dullness.sharpenCost(result));
        return result;
    }

    // Камень — обратно в сетку, изношенный на единицу
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(ModItems.WHETSTONE.get())) {
                ItemStack worn = stack.copyWithCount(1);
                worn.setDamageValue(worn.getDamageValue() + 1);
                remaining.set(i, worn.getDamageValue() >= worn.getMaxDamage() ? ItemStack.EMPTY : worn);
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SHARPEN.get();
    }
}
