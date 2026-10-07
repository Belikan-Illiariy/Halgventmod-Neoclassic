package com.nrdhvnstd.halgventmod.food;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

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
    //   жажда: + утоляет, − усиливает, 0 — не влияет (1 = полкапли)
    //   срок: 0 — не портится
    // food("othermod:item", жажда, срок)
    static {
        // Примеры:
        // food(Items.MELON_SLICE, 2, 3);  // утоляет жажду, портится через 3 суток
        // food(Items.DRIED_KELP, -1, 0);  // солёное, сушит, не портится
        // food(Items.BREAD, 0, 7);        // портится через 7 суток
    }

    private ModFoods() {}

    public record FoodInfo(int thirst, int shelfLifeDays) {}

    public static void food(ItemLike item, int thirst, int shelfLifeDays) {
        put(BuiltInRegistries.ITEM.getKey(item.asItem()), new FoodInfo(thirst, shelfLifeDays));
    }

    public static void food(String itemId, int thirst, int shelfLifeDays) {
        put(ResourceLocation.parse(itemId), new FoodInfo(thirst, shelfLifeDays));
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

    /** Есть ли у предмета срок годности. */
    public static boolean perishable(ItemStack stack) {
        FoodInfo info = get(stack);
        return info != null && info.shelfLifeDays() > 0;
    }
}