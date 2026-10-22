package com.nrdhvnstd.halgventmod.draw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Как достаётся предмет (DrawType). Устроено как веса (ModWeights). */
public final class ModDraw {
    // Предметы, для которых ничего не задано: блоки — тяжёлые, остальное — мелочь (ТЗ 5.3) ⚠️
    public static final DrawType DEFAULT_ITEM = DrawType.SMALL;
    public static final DrawType DEFAULT_BLOCK = DrawType.HEAVY;

    // Должны стоять ВЫШЕ блока с таблицей
    private static final Map<ResourceLocation, DrawType> BY_ID = new HashMap<>();
    private static final List<TagDraw> BY_TAG = new ArrayList<>();

    // ===== Ванильные предметы и предметы других модов =====
    // Для предметов своего мода — .draw(DrawType.X) в ModItems.
    //
    // set(Items.SHIELD, DrawType.HEAVY);     — один предмет
    // set(ItemTags.SWORDS, DrawType.TOOL);   — все предметы с тегом
    // Точное значение важнее тега; из тегов берётся первый подходящий сверху.
    static {
        set(Items.SHIELD, DrawType.HEAVY);
        set(Items.TRIDENT, DrawType.HEAVY);
        set(Items.MACE, DrawType.HEAVY);
        set(Items.BOW, DrawType.TOOL);
        set(Items.CROSSBOW, DrawType.TOOL);
        set(Items.FISHING_ROD, DrawType.TOOL);
        set(Items.SHEARS, DrawType.TOOL);
        set(Items.FLINT_AND_STEEL, DrawType.TOOL);
        set(Items.TORCH, DrawType.TOOL);       // блоки, но в руке — не тяжесть
        set(Items.SOUL_TORCH, DrawType.TOOL);
        set(Items.LANTERN, DrawType.TOOL);
        set(Items.SOUL_LANTERN, DrawType.TOOL);
        set(ItemTags.SWORDS, DrawType.TOOL);
        set(ItemTags.AXES, DrawType.TOOL);
        set(ItemTags.PICKAXES, DrawType.TOOL);
        set(ItemTags.SHOVELS, DrawType.TOOL);
        set(ItemTags.HOES, DrawType.TOOL);
    }

    private ModDraw() {}

    private record TagDraw(TagKey<Item> tag, DrawType type) {}

    public static void set(ItemLike item, DrawType type) {
        put(BuiltInRegistries.ITEM.getKey(item.asItem()), type);
    }

    public static void set(TagKey<Item> tag, DrawType type) {
        BY_TAG.add(new TagDraw(tag, type));
    }

    /** Используется и билдером ModItems. */
    public static void put(ResourceLocation itemId, DrawType type) {
        if (BY_ID.containsKey(itemId)) {
            throw new IllegalArgumentException("Доставание для " + itemId + " задано дважды");
        }
        BY_ID.put(itemId, type);
    }

    // Итог по предмету; сбрасывается, когда меняются теги (Encumbrance.onTagsUpdated). Читают сервер и клиент
    private static final Map<Item, DrawType> RESOLVED = new ConcurrentHashMap<>();

    public static void clearCache() {
        RESOLVED.clear();
    }

    public static DrawType of(ItemStack stack) {
        return RESOLVED.computeIfAbsent(stack.getItem(), item -> resolve(new ItemStack(item)));
    }

    private static DrawType resolve(ItemStack stack) {
        DrawType exact = BY_ID.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (exact != null) {
            return exact;
        }
        for (TagDraw tagDraw : BY_TAG) {
            if (stack.is(tagDraw.tag())) {
                return tagDraw.type();
            }
        }
        // Семена и корнеплоды (морковь, рожь, лук) — тоже «блоки», но в руку берутся как мелочь
        boolean block = stack.getItem() instanceof BlockItem && !(stack.getItem() instanceof ItemNameBlockItem);
        return block ? DEFAULT_BLOCK : DEFAULT_ITEM;
    }
}
