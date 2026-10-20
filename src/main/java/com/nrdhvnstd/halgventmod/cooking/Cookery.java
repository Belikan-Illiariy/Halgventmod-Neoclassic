package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.ModFoods;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * Правила котелка: что чем считается и что из этого выходит. Меняйте здесь.
 *
 * <p>Что положено в котелок, делится по тегам halgventmod:cooking/*. Блюдо решает основа, которой больше всего
 * (при равенстве — по порядку {@link Kind}): мясо → мясная похлёбка, рыба → уха, зерно → каша, грибы → грибная
 * похлёбка, овощи → похлёбка. Только травы с водой — отвар. Соль и травы — приправы: порций не дают, поднимают
 * качество. Молоко — к любой основе, прибавляет сытости (каша на молоке — молочная).
 *
 * <p>Порций — по числу основ (у отвара — по 2 на пучок трав). Сытость порции — сумма сытости основ
 * × {@link #COOKING_BONUS} / порций. Сырое мясо в котле проваривается: риск отравления у похлёбки нет.
 */
public final class Cookery {
    // Меняйте здесь
    public static final float COOKING_BONUS = 1.4F;   // варёное сытнее сырого
    public static final int NON_FOOD_NUTRITION = 15;  // зерно и мука, у которых нет своей сытости
    public static final int MILK_NUTRITION = 15;
    public static final float SATURATION_SHARE = 0.7F;
    public static final int SOUP_THIRST = 20;         // похлёбки утоляют жажду
    public static final int TEA_THIRST = 35;
    public static final int TEA_PORTIONS_PER_HERB = 2;
    public static final int CAPACITY = 6;

    /** Чем считается положенное в котелок. */
    public enum Kind {
        MEAT, FISH, GRAIN, MUSHROOM, VEGETABLE, MILK, SALT, HERB;

        public boolean main() {
            return ordinal() <= VEGETABLE.ordinal();
        }

        public TagKey<Item> tag() {
            return TagKey.create(Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "cooking/" + name().toLowerCase()));
        }
    }

    /** Что выходит из котелка. */
    public enum Result {
        MEAT_STEW, FISH_SOUP, PORRIDGE, MUSHROOM_SOUP, VEGETABLE_SOUP, HERBAL_TEA;

        public Item item() {
            return switch (this) {
                case MEAT_STEW -> ModItems.MEAT_STEW.get();
                case FISH_SOUP -> ModItems.FISH_SOUP.get();
                case PORRIDGE -> ModItems.PORRIDGE.get();
                case MUSHROOM_SOUP -> ModItems.MUSHROOM_SOUP.get();
                case VEGETABLE_SOUP -> ModItems.VEGETABLE_SOUP.get();
                case HERBAL_TEA -> ModItems.HERBAL_TEA.get();
            };
        }

        /** Чем черпают: миской или склянкой. */
        public Item container() {
            return this == HERBAL_TEA ? Items.GLASS_BOTTLE : Items.BOWL;
        }
    }

    /** Итог варки: что, сколько порций и одна порция (с качеством). */
    public record Outcome(Result result, int portions, ItemStack portion) {}

    private Cookery() {}

    @Nullable
    public static Kind kind(ItemStack stack) {
        if (stack.is(Items.MILK_BUCKET)) {
            return Kind.MILK;
        }
        for (Kind kind : Kind.values()) {
            if (stack.is(kind.tag())) {
                return kind;
            }
        }
        return null;
    }

    /** Сытость продукта по шкале 0–100: своя из ModItems, ванильная ×5, у зерна и муки — постоянная. */
    public static int nutrition(ItemStack stack) {
        if (stack.is(Items.MILK_BUCKET)) {
            return MILK_NUTRITION;
        }
        ModFoods.FoodInfo info = ModFoods.get(stack);
        if (info != null && info.nutrition() >= 0) {
            return info.nutrition();
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null ? food.nutrition() * Hunger.VANILLA_SCALE : NON_FOOD_NUTRITION;
    }

    /**
     * Что выйдет из этих продуктов. null — сварить нечего (только соль или молоко).
     * @param scorched сколько раз пригорело (каждый раз — качество −1)
     */
    @Nullable
    public static Outcome cook(List<ItemStack> contents, int scorched, Level level) {
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        Set<Item> mainTypes = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        int total = 0;
        int quality = FoodQuality.NORMAL - scorched;
        boolean stale = false;
        boolean spoiled = false;
        for (ItemStack stack : contents) {
            Kind kind = kind(stack);
            if (kind == null) {
                continue;
            }
            counts.merge(kind, 1, Integer::sum);
            names.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (kind.main()) {
                mainTypes.add(stack.getItem());
                total += nutrition(stack);
            } else if (kind == Kind.MILK) {
                total += nutrition(stack);
            }
            Integer left = Freshness.daysLeft(stack, level);
            if (left != null) {
                spoiled |= left <= 0;
                stale |= left == 1;
            }
            quality += FoodQuality.has(stack) ? (FoodQuality.get(stack) - FoodQuality.NORMAL) / 2 : 0;
        }
        int mains = 0;
        Kind base = null;
        for (Kind kind : Kind.values()) {
            int n = counts.getOrDefault(kind, 0);
            if (kind.main()) {
                mains += n;
                if (n > 0 && (base == null || n > counts.get(base))) {
                    base = kind;
                }
            }
        }

        if (spoiled) {
            quality -= 2;
        } else if (stale) {
            quality -= 1;
        }

        if (mains == 0) {
            int herbs = counts.getOrDefault(Kind.HERB, 0);
            if (herbs == 0) {
                return null;
            }
            int portions = herbs * TEA_PORTIONS_PER_HERB;
            ItemStack tea = new ItemStack(Result.HERBAL_TEA.item());
            tea.set(ModComponents.DISH.get(),
                    new Dish(0, 0, TEA_THIRST, List.copyOf(names)));
            return new Outcome(Result.HERBAL_TEA, portions, FoodQuality.set(tea, quality));
        }

        Result result = switch (base) {
            case MEAT -> Result.MEAT_STEW;
            case FISH -> Result.FISH_SOUP;
            case GRAIN -> Result.PORRIDGE;
            case MUSHROOM -> Result.MUSHROOM_SOUP;
            default -> Result.VEGETABLE_SOUP;
        };
        if (counts.containsKey(Kind.SALT)) {
            quality++;
        }
        if (counts.containsKey(Kind.HERB)) {
            quality++;
        }
        if (mainTypes.size() >= 3) {
            quality++;
        }
        int nutrition = Math.min(100, Math.round(total * COOKING_BONUS / mains));
        int thirst = result == Result.PORRIDGE ? (counts.containsKey(Kind.MILK) ? 5 : 0) : SOUP_THIRST;
        ItemStack portion = new ItemStack(result.item());
        portion.set(ModComponents.DISH.get(),
                new Dish(nutrition, nutrition * SATURATION_SHARE, thirst, List.copyOf(names)));
        return new Outcome(result, mains, FoodQuality.set(portion, quality));
    }

    // ---------------------------------------------------------------- жарка

    /** Во что превращается продукт над углями (ванильные и чужие рецепты костра), или пусто. */
    public static ItemStack grilled(ItemStack raw, Level level) {
        Optional<RecipeHolder<CampfireCookingRecipe>> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(raw), level);
        return recipe.map(holder -> holder.value().getResultItem(level.registryAccess()).copy()).orElse(ItemStack.EMPTY);
    }

    /** Имена продуктов из id — для подсказки блюда. */
    public static List<Item> items(List<String> ids) {
        List<Item> items = new ArrayList<>();
        for (String id : ids) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null) {
                BuiltInRegistries.ITEM.getOptional(key).ifPresent(items::add);
            }
        }
        return items;
    }
}
