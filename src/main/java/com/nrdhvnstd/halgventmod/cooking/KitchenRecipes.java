package com.nrdhvnstd.halgventmod.cooking;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.herbs.HerbGathering;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Рецепты кухонного стола. Меняйте здесь.
 *
 * <p>Рецепт: что выйдет, замесов, нужна ли вода, можно ли посолить (+качество), обязательные продукты и
 * <b>добавки</b> — необязательные, до {@link #MAX_EXTRAS} разных: каждая прибавляет готовому насыщения
 * ({@link Extra#saturation}). Ещё может быть второй выход (у отжима сот — воск).
 */
public final class KitchenRecipes {
    public static final int MAX_EXTRAS = 3;
    /** Ягоды для пирогов и морса: halgventmod:berries. */
    public static final TagKey<Item> BERRY_TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "berries"));

    /** Обязательный продукт: что подходит, сколько, чем показывать. */
    public record Need(Predicate<ItemStack> matches, int count, Supplier<Item> icon) {}

    /** Необязательная добавка: что подходит, чем показывать, сколько насыщения прибавляет. */
    public record Extra(Predicate<ItemStack> matches, Supplier<Item> icon, float saturation) {}

    public record Recipe(String id, Supplier<ItemStack> result, Supplier<ItemStack> byproduct, int kneads,
                         boolean water, boolean salted, List<Need> needs, List<Extra> extras) {
        public ItemStack makeResult() {
            return result.get();
        }

        public ItemStack makeByproduct() {
            return byproduct.get();
        }
    }

    // ---------------------------------------------------------------- продукты и добавки

    private static final Need FLOUR = new Need(stack -> stack.is(ModItems.WHEAT_FLOUR.get()), 2, ModItems.WHEAT_FLOUR::get);
    private static final Need DOUGH = new Need(stack -> stack.is(ModItems.DOUGH.get()), 1, ModItems.DOUGH::get);
    private static final Need SWEETENER = new Need(stack -> stack.is(ModItems.HONEY.get()), 1, ModItems.HONEY::get);
    // Пряные травы — свежие или сушёные (чеснок и пряности — отдельно)
    private static final Need HERB_NEED = new Need(stack -> stack.is(HerbGathering.HERBS) || stack.is(ModItems.DRIED_HERBS.get()),
            1, ModItems.THYME::get);

    private static Extra extra(Item item, float saturation) {
        return new Extra(stack -> stack.is(item), () -> item, saturation);
    }

    private static Extra extra(Supplier<? extends Item> item, float saturation) {
        return new Extra(stack -> stack.is(item.get()), item::get, saturation);
    }

    private static final Extra CARROT = extra(Items.CARROT, 8);
    private static final Extra POTATO = extra(Items.POTATO, 8);
    private static final Extra EGG = extra(Items.EGG, 8);
    private static final Extra MUSHROOM = new Extra(stack -> stack.is(Cookery.Kind.MUSHROOM.tag()), () -> Items.BROWN_MUSHROOM, 6);
    private static final Extra HERBS = new Extra(stack -> stack.is(Cookery.Kind.HERB.tag()), ModItems.THYME::get, 4);
    private static final Extra CHEESE = extra(ModItems.CHEESE, 12);
    private static final Extra SALO = extra(ModItems.SALO, 10);
    private static final Extra HONEY = extra(ModItems.HONEY, 10);
    private static final Extra APPLE = extra(Items.APPLE, 6);
    private static final Extra BERRIES = new Extra(stack -> stack.is(BERRY_TAG) || stack.is(Items.GLOW_BERRIES),
            () -> Items.SWEET_BERRIES, 6);
    private static final Extra GARLIC = extra(ModItems.GARLIC, 4);
    private static final Extra ONION = extra(ModItems.ONION, 4);

    private static final List<Recipe> ALL = List.of(
            new Recipe("dough", () -> new ItemStack(ModItems.DOUGH.get(), 2), () -> ItemStack.EMPTY, 6, true, true,
                    List.of(FLOUR), List.of()),
            new Recipe("rye_dough", () -> new ItemStack(ModItems.RYE_DOUGH.get(), 2), () -> ItemStack.EMPTY, 6, true, true,
                    List.of(new Need(stack -> stack.is(ModItems.RYE_FLOUR.get()), 2, ModItems.RYE_FLOUR::get)),
                    List.of(HERBS, HONEY)),
            new Recipe("meat_pie", () -> pie("meat"), () -> ItemStack.EMPTY, 3, false, false,
                    List.of(DOUGH, new Need(stack -> stack.is(Cookery.Kind.MEAT.tag()), 1, () -> Items.PORKCHOP)),
                    List.of(CARROT, POTATO, MUSHROOM, HERBS, CHEESE, SALO, EGG)),
            new Recipe("mushroom_pie", () -> pie("mushroom"), () -> ItemStack.EMPTY, 3, false, false,
                    List.of(DOUGH, new Need(stack -> stack.is(Cookery.Kind.MUSHROOM.tag()), 1, () -> Items.BROWN_MUSHROOM)),
                    List.of(EGG, CHEESE, HERBS, POTATO, SALO)),
            new Recipe("berry_pie", () -> pie("berry"), () -> ItemStack.EMPTY, 3, false, false,
                    List.of(DOUGH, new Need(stack -> stack.is(BERRY_TAG) || stack.is(Items.GLOW_BERRIES)
                            || stack.is(Items.APPLE), 2, () -> Items.SWEET_BERRIES)),
                    List.of(HONEY, APPLE, BERRIES, EGG)),
            new Recipe("sweet_bun", () -> new ItemStack(ModItems.RAW_BUN.get(), 2), () -> ItemStack.EMPTY, 4, false, false,
                    List.of(DOUGH, SWEETENER),
                    List.of(EGG, BERRIES, APPLE, CHEESE, HONEY)),
            // Блюда для печи: рыба или мясо с приправами; соль — по желанию, +качество
            new Recipe("salmon_herbs", () -> RawDishItem.of(RawDishItem.Kind.SALMON_HERBS), () -> ItemStack.EMPTY, 2, false, true,
                    List.of(new Need(stack -> stack.is(Items.SALMON), 1, () -> Items.SALMON), HERB_NEED),
                    List.of(ONION, GARLIC)),
            new Recipe("hare_herbs", () -> RawDishItem.of(RawDishItem.Kind.HARE_HERBS), () -> ItemStack.EMPTY, 2, false, true,
                    List.of(new Need(stack -> stack.is(Items.RABBIT), 1, () -> Items.RABBIT), HERB_NEED),
                    List.of(ONION, GARLIC, CARROT)),
            new Recipe("exquisite_mutton", () -> RawDishItem.of(RawDishItem.Kind.EXQUISITE_MUTTON), () -> ItemStack.EMPTY, 3,
                    false, true,
                    List.of(new Need(stack -> stack.is(Items.MUTTON), 1, () -> Items.MUTTON),
                            new Need(stack -> stack.is(ModItems.SPICES.get()), 1, ModItems.SPICES::get),
                            new Need(stack -> stack.is(ModItems.GARLIC.get()), 1, ModItems.GARLIC::get)),
                    List.of(ONION, HERBS)),
            // Морс: ягоды с водой — в склянку
            new Recipe("berry_juice", () -> new ItemStack(ModItems.BERRY_JUICE.get()), () -> ItemStack.EMPTY, 2, true, false,
                    List.of(new Need(stack -> stack.is(BERRY_TAG), 3, () -> Items.SWEET_BERRIES),
                            new Need(stack -> stack.is(Items.GLASS_BOTTLE), 1, () -> Items.GLASS_BOTTLE)),
                    List.of(HONEY)),
            new Recipe("soak_grain", () -> new ItemStack(ModItems.WET_GRAIN.get(), 4), () -> ItemStack.EMPTY, 1, true, false,
                    List.of(new Need(stack -> stack.is(Items.WHEAT) || stack.is(ModItems.RYE.get()) || stack.is(ModItems.BARLEY.get())
                            || stack.is(ModItems.OATS.get()), 4, ModItems.BARLEY::get)),
                    List.of()),
            new Recipe("honey", () -> new ItemStack(ModItems.HONEY.get()), () -> new ItemStack(ModItems.BEESWAX.get()),
                    4, false, false,
                    List.of(new Need(stack -> stack.is(ModItems.HONEYCOMB_FULL.get()), 1, ModItems.HONEYCOMB_FULL::get)),
                    List.of()));

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
