package com.nrdhvnstd.halgventmod.cooking;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Рецепты разделочного стола. Меняйте здесь.
 *
 * <p>recipe(что выйдет, сколько, замесов, вода нужна?, продукты...). Продукт — что подходит, сколько и какой
 * вещью показывать в окне рецептов. Соль сверх рецепта (если recipe.salted) — качество +1.
 */
public final class KitchenRecipes {
    /** Нужный продукт: что подходит, сколько, чем показывать. */
    public record Need(Predicate<ItemStack> matches, int count, Supplier<Item> icon) {}

    public record Recipe(String id, Supplier<ItemStack> result, int kneads, boolean water, boolean salted, List<Need> needs) {
        public ItemStack makeResult() {
            return result.get();
        }
    }

    private static final Need FLOUR = new Need(stack -> stack.is(ModItems.WHEAT_FLOUR.get()), 2, ModItems.WHEAT_FLOUR::get);
    private static final Need DOUGH = new Need(stack -> stack.is(ModItems.DOUGH.get()), 1, ModItems.DOUGH::get);

    private static final List<Recipe> ALL = List.of(
            new Recipe("dough", () -> new ItemStack(ModItems.DOUGH.get(), 2), 6, true, true, List.of(FLOUR)),
            new Recipe("meat_pie", () -> pie("meat"), 3, false, false, List.of(DOUGH,
                    new Need(stack -> stack.is(Cookery.Kind.MEAT.tag()), 1, () -> Items.PORKCHOP))),
            new Recipe("mushroom_pie", () -> pie("mushroom"), 3, false, false, List.of(DOUGH,
                    new Need(stack -> stack.is(Cookery.Kind.MUSHROOM.tag()), 1, () -> Items.BROWN_MUSHROOM))),
            new Recipe("berry_pie", () -> pie("berry"), 3, false, false, List.of(DOUGH,
                    new Need(stack -> stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.APPLE),
                            1, () -> Items.SWEET_BERRIES))));

    private KitchenRecipes() {}

    private static ItemStack pie(String filling) {
        ItemStack pie = new ItemStack(ModItems.RAW_PIE.get());
        pie.set(ModComponents.PIE_FILLING.get(), filling);
        return pie;
    }

    public static List<Recipe> all() {
        return ALL;
    }

    public static Recipe get(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }
}
