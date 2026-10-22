package com.nrdhvnstd.halgventmod.food;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Жажда и срок годности еды и питья: мода, ванильных и других модов. */
public final class ModFoods {
    // Должно стоять ВЫШЕ блока с описаниями
    private static final Map<ResourceLocation, FoodInfo> BY_ID = new HashMap<>();

    // ===== Ванильная еда и еда других модов =====
    // Для предметов своего мода — .thirst(...) и .shelfLife(...) в ModItems.
    //
    // food(Items.<ПРЕДМЕТ>, жажда, срок годности в сутках)
    //   жажда: + утоляет, − усиливает, 0 — не влияет (шкала жажды 0–100)
    //   срок: 0 — не портится
    // food("othermod:item", жажда, срок)
    // Сытость такой еды — ванильная ×5 (Hunger.VANILLA_SCALE): хлеб 5 → 25 из 100.
    // food(Items.<ПРЕДМЕТ>, сытость, насыщение, жажда, срок) — свои сытость и насыщение вместо ванильных ×5
    static {
        // Сырое мясо и рыба — быстро (таблица баланса: мясо 2 суток, рыба 1)
        food(Items.BEEF, 0, 2);
        food(Items.PORKCHOP, 0, 2);
        food(Items.MUTTON, 0, 2);
        food(Items.CHICKEN, 0, 2);
        food(Items.RABBIT, 0, 2);
        food(Items.COD, 0, 1);
        food(Items.SALMON, 0, 1);
        food(Items.TROPICAL_FISH, 0, 1);
        food(Items.PUFFERFISH, 0, 1);
        // Жареное (таблица баланса: свинина, говядина, баранина +30 −15; курица, заяц, лосось +25 −10)
        food(Items.COOKED_BEEF, 30, 15, -15, 3);
        food(Items.COOKED_PORKCHOP, 30, 15, -15, 3);
        food(Items.COOKED_MUTTON, 30, 15, -15, 3);
        food(Items.COOKED_CHICKEN, 25, 12.5F, -10, 3);
        food(Items.COOKED_RABBIT, 25, 12.5F, -10, 3);
        food(Items.COOKED_COD, 0, 3);
        food(Items.COOKED_SALMON, 25, 12.5F, -10, 2);
        food(Items.BAKED_POTATO, 10, 5, -3, 2);
        // Похлёбки и выпечка
        food(Items.MUSHROOM_STEW, 15, 2);
        food(Items.BEETROOT_SOUP, 15, 2);
        food(Items.RABBIT_STEW, 15, 2);
        food(Items.SUSPICIOUS_STEW, 15, 2);
        food(Items.BREAD, 0, 6);
        food(Items.PUMPKIN_PIE, 0, 4);
        food(Items.COOKIE, 0, 12);
        // Овощи, фрукты, ягоды (таблица баланса: овощи не портятся; картофель сырым не насыщает)
        food(Items.APPLE, 5, 2.5F, 3, 3);
        food(Items.CARROT, 3, 1.5F, 0, 0);
        food(Items.POTATO, 0, 0, 0, 0);
        food(Items.POISONOUS_POTATO, 0, 8);
        food(Items.BEETROOT, 4, 2, 0, 0);
        food(Items.MELON_SLICE, 10, 3);
        food(Items.SWEET_BERRIES, 3, 4);
        food(Items.GLOW_BERRIES, 3, 6);
        food(Items.CHORUS_FRUIT, 0, 16);
        food(Items.GOLDEN_CARROT, 0, 32);
        // Сушёное и молоко
        food(Items.DRIED_KELP, -5, 64);
        food(Items.MILK_BUCKET, 20, 2);
        // Не портятся: мёд, золотые яблоки, гнилая плоть и паучий глаз (уже), — 0
        food(Items.HONEY_BOTTLE, 10, 0);
        food(Items.GOLDEN_APPLE, 0, 0);
        food(Items.ENCHANTED_GOLDEN_APPLE, 0, 0);
        food(Items.ROTTEN_FLESH, 0, 0);
        food(Items.SPIDER_EYE, 0, 0);
    }

    /** Срок годности еды, которой нет в таблице (чужие моды), — суток. */
    public static final int DEFAULT_SHELF_LIFE = 8;

    private ModFoods() {}

    /** nutrition < 0 — сытость и насыщение берутся из ванильных свойств еды ×5. */
    public record FoodInfo(int nutrition, float saturation, int thirst, int shelfLifeDays) {}

    public static void food(ItemLike item, int thirst, int shelfLifeDays) {
        put(BuiltInRegistries.ITEM.getKey(item.asItem()), new FoodInfo(-1, 0, thirst, shelfLifeDays));
    }

    public static void food(ItemLike item, int nutrition, float saturation, int thirst, int shelfLifeDays) {
        put(BuiltInRegistries.ITEM.getKey(item.asItem()), new FoodInfo(nutrition, saturation, thirst, shelfLifeDays));
    }

    public static void food(String itemId, int thirst, int shelfLifeDays) {
        put(ResourceLocation.parse(itemId), new FoodInfo(-1, 0, thirst, shelfLifeDays));
    }

    /** Используется и билдером ModItems. */
    public static void put(ResourceLocation itemId, FoodInfo info) {
        if (BY_ID.containsKey(itemId)) {
            throw new IllegalArgumentException("Жажда и срок годности для " + itemId + " заданы дважды");
        }
        BY_ID.put(itemId, info);
    }

    @Nullable
    public static FoodInfo get(Item item) {
        return BY_ID.get(BuiltInRegistries.ITEM.getKey(item));
    }

    @Nullable
    public static FoodInfo get(ItemStack stack) {
        return stack.isEmpty() ? null : get(stack.getItem());
    }

    /**
     * Срок годности в сутках; 0 — не портится. Своё и ванильное — из таблицы и ModItems, любая другая еда
     * (чужие моды) — {@link #DEFAULT_SHELF_LIFE}.
     */
    public static int shelfLife(ItemStack stack) {
        FoodInfo info = get(stack);
        if (info != null) {
            return info.shelfLifeDays();
        }
        return !stack.isEmpty() && stack.has(DataComponents.FOOD) ? DEFAULT_SHELF_LIFE : 0;
    }

    /** Есть ли у предмета срок годности. */
    public static boolean perishable(ItemStack stack) {
        return shelfLife(stack) > 0;
    }
}