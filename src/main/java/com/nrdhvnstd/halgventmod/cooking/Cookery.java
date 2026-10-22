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
import com.nrdhvnstd.halgventmod.brewing.Brew;
import com.nrdhvnstd.halgventmod.brewing.Brewing;
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
 * Правила горшка: что чем считается и что из этого выходит. Меняйте здесь.
 *
 * <p>Что положено в горшок, делится по тегам halgventmod:cooking/*. Блюдо решает основа, которой больше всего
 * (при равенстве — по порядку {@link Kind}): мясо → мясная похлёбка, рыба → уха, зерно → каша по зерну, которого
 * больше (пшеничная, ржаная, ячменная, овсяная), грибы → грибная похлёбка, овощи → картофельная похлёбка (если из
 * овощей один картофель, лук и чеснок не в счёт) или овощная. Только травы с водой — отвар. Соль, травы, чеснок,
 * пряности — приправы: порций не дают, поднимают качество. Молоко — к любой основе, прибавляет сытости. Мёд —
 * сладость: +качество и немного сытости (отвар с мёдом).
 *
 * <p>Порций — по числу основ (у отвара — по 2 на пучок трав). Сытость и жажда порции у блюд из таблицы баланса
 * (похлёбки мясная, картофельная, овощная, каши) — постоянные, из ModItems; у прочих — сумма сытости основ
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

    /** Чем считается положенное в горшок. */
    public enum Kind {
        MEAT, FISH, GRAIN, MUSHROOM, VEGETABLE, MILK, SALT, HERB, SWEET, MALT;

        public boolean main() {
            return ordinal() <= VEGETABLE.ordinal();
        }

        public TagKey<Item> tag() {
            return TagKey.create(Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "cooking/" + name().toLowerCase()));
        }
    }

    /** Что выходит из горшка. */
    public enum Result {
        MEAT_STEW, FISH_SOUP, PORRIDGE, MUSHROOM_SOUP, VEGETABLE_SOUP, HERBAL_TEA, WORT, HONEY_MUST,
        POTATO_STEW, WHEAT_PORRIDGE, RYE_PORRIDGE, BARLEY_PORRIDGE, OAT_PORRIDGE;

        public Item item() {
            return switch (this) {
                case MEAT_STEW -> ModItems.MEAT_STEW.get();
                case FISH_SOUP -> ModItems.FISH_SOUP.get();
                case PORRIDGE -> ModItems.PORRIDGE.get();
                case MUSHROOM_SOUP -> ModItems.MUSHROOM_SOUP.get();
                case VEGETABLE_SOUP -> ModItems.VEGETABLE_SOUP.get();
                case POTATO_STEW -> ModItems.POTATO_STEW.get();
                case WHEAT_PORRIDGE -> ModItems.WHEAT_PORRIDGE.get();
                case RYE_PORRIDGE -> ModItems.RYE_PORRIDGE.get();
                case BARLEY_PORRIDGE -> ModItems.BARLEY_PORRIDGE.get();
                case OAT_PORRIDGE -> ModItems.OAT_PORRIDGE.get();
                case HERBAL_TEA -> ModItems.HERBAL_TEA.get();
                case WORT, HONEY_MUST -> ModItems.MUST.get(); // сусло для бродильной бочки
            };
        }

        /** Чем черпают: миской или склянкой. */
        public Item container() {
            return this == HERBAL_TEA || this == WORT || this == HONEY_MUST ? Items.GLASS_BOTTLE : Items.BOWL;
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
            } else if (kind == Kind.MILK || kind == Kind.SWEET) {
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

        // Солод с водой — пивное сусло (по самому тёмному солоду); травы — «на травах», мёд — «с мёдом»
        int malts = counts.getOrDefault(Kind.MALT, 0);
        if (malts > 0) {
            String kind = "wort_pale";
            for (ItemStack stack : contents) {
                if (stack.is(ModItems.DARK_MALT.get())) {
                    kind = "wort_dark";
                } else if (stack.is(ModItems.AMBER_MALT.get()) && !kind.equals("wort_dark")) {
                    kind = "wort_amber";
                }
            }
            Brew brew = Brew.of(kind);
            if (counts.containsKey(Kind.HERB)) {
                brew = brew.withFlag("herbs");
            }
            if (counts.containsKey(Kind.SWEET)) {
                brew = brew.withFlag("honey");
            }
            ItemStack wort = Brewing.make(ModItems.MUST.get(), brew);
            return new Outcome(Result.WORT, malts * 2, FoodQuality.set(wort, quality));
        }

        if (mains == 0) {
            int herbs = counts.getOrDefault(Kind.HERB, 0);
            if (herbs == 0) {
                int honey = counts.getOrDefault(Kind.SWEET, 0);
                if (honey == 0) {
                    return null;
                }
                // Мёд с водой — медовый взвар для медовухи
                return new Outcome(Result.HONEY_MUST, honey * 2,
                        FoodQuality.set(Brewing.make(ModItems.MUST.get(), Brew.of("honey")), quality));
            }
            if (counts.containsKey(Kind.SWEET)) {
                quality++; // отвар с мёдом
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
            case GRAIN -> porridge(contents);
            case MUSHROOM -> Result.MUSHROOM_SOUP;
            default -> onlyPotatoes(contents) ? Result.POTATO_STEW : Result.VEGETABLE_SOUP;
        };
        if (counts.containsKey(Kind.SALT)) {
            quality++;
        }
        if (counts.containsKey(Kind.HERB)) {
            quality++;
        }
        if (counts.containsKey(Kind.SWEET)) {
            quality++; // мёд
        }
        if (mainTypes.size() >= 3) {
            quality++;
        }
        int nutrition = Math.min(100, Math.round(total * COOKING_BONUS / mains));
        int thirst = result == Result.PORRIDGE ? (counts.containsKey(Kind.MILK) ? 5 : 0) : SOUP_THIRST;
        float saturation = nutrition * SATURATION_SHARE;
        // Блюда из таблицы баланса: сытость и жажда порции — постоянные, из описания блюда (ModItems)
        ModFoods.FoodInfo table = ModFoods.get(result.item());
        if (TABLE_DISHES.contains(result) && table != null && table.nutrition() >= 0) {
            nutrition = table.nutrition();
            saturation = table.saturation();
            thirst = table.thirst();
        }
        ItemStack portion = new ItemStack(result.item());
        portion.set(ModComponents.DISH.get(), new Dish(nutrition, saturation, thirst, List.copyOf(names)));
        return new Outcome(result, mains, FoodQuality.set(portion, quality));
    }

    /** Блюда, у которых сытость и жажда порции — из таблицы, а не из суммы продуктов. */
    private static final Set<Result> TABLE_DISHES = java.util.EnumSet.of(Result.MEAT_STEW, Result.VEGETABLE_SOUP,
            Result.POTATO_STEW, Result.WHEAT_PORRIDGE, Result.RYE_PORRIDGE, Result.BARLEY_PORRIDGE, Result.OAT_PORRIDGE);

    /** Каша — по зерну, которого больше: пшеница, рожь, ячмень или овёс (мука — как её зерно). */
    private static Result porridge(List<ItemStack> contents) {
        Map<Result, Integer> grains = new EnumMap<>(Result.class);
        for (ItemStack stack : contents) {
            Result kind = stack.is(Items.WHEAT) || stack.is(ModItems.WHEAT_FLOUR.get()) ? Result.WHEAT_PORRIDGE
                    : stack.is(ModItems.RYE.get()) || stack.is(ModItems.RYE_FLOUR.get()) ? Result.RYE_PORRIDGE
                    : stack.is(ModItems.BARLEY.get()) ? Result.BARLEY_PORRIDGE
                    : stack.is(ModItems.OATS.get()) ? Result.OAT_PORRIDGE : null;
            if (kind != null) {
                grains.merge(kind, 1, Integer::sum);
            }
        }
        Result best = Result.WHEAT_PORRIDGE;
        int most = 0;
        for (Map.Entry<Result, Integer> grain : grains.entrySet()) {
            if (grain.getValue() > most) {
                best = grain.getKey();
                most = grain.getValue();
            }
        }
        return best;
    }

    /** Из овощей один картофель (лук и чеснок — не в счёт): картофельная похлёбка, иначе — овощная. */
    private static boolean onlyPotatoes(List<ItemStack> contents) {
        for (ItemStack stack : contents) {
            boolean vegetable = kind(stack) == Kind.VEGETABLE;
            boolean potato = stack.is(Items.POTATO) || stack.is(Items.BAKED_POTATO);
            boolean seasoning = stack.is(ModItems.ONION.get()) || stack.is(ModItems.GARLIC.get());
            if (vegetable && !potato && !seasoning) {
                return false;
            }
        }
        return true;
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
