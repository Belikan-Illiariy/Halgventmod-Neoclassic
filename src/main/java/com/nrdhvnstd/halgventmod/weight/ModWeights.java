package com.nrdhvnstd.halgventmod.weight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.pottery.Stored;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ItemLike;

/** Вес предметов. Всё хранится в граммах. */
public final class ModWeights {
    // Вес предметов, для которых он нигде не задан
    public static final double DEFAULT_ITEM_GRAMS = 100;   // обычный предмет
    public static final double DEFAULT_BLOCK_GRAMS = 1000; // блок
    public static final double WATER_MEASURE_GRAMS = 250;  // мера воды в кружке, кувшине, корчаге

    // Должны стоять ВЫШЕ блока с весами
    private static final Map<ResourceLocation, Double> BY_ID = new HashMap<>();
    private static final List<TagWeight> BY_TAG = new ArrayList<>();

    // ===== Вес ванильных предметов и предметов других модов =====
    // Для предметов своего мода вес пишется через .grams(...) или .kg(...) в ModItems.
    //
    // grams(Items.GOLD_NUGGET, 5);          — один предмет, в граммах
    // kg(Items.IRON_INGOT, 1);              — один предмет, в килограммах
    // kg(ItemTags.LOGS, 3);                 — все предметы с тегом сразу
    // kg("othermod:item", 2);               — предмет другого мода по ID
    // kg("#c:ingots", 1);                   — тег по имени (с решёткой)
    // Точный вес предмета важнее веса по тегу; из тегов берётся первый подходящий сверху.
    static {
        // Примеры:
        // kg(Items.COBBLESTONE, 2);
        // kg(ItemTags.LOGS, 3);

        // Еда — таблица баланса
        kg(Items.PORKCHOP, 0.5);
        kg(Items.BEEF, 0.5);
        kg(Items.CHICKEN, 0.4);
        kg(Items.RABBIT, 0.4);
        kg(Items.MUTTON, 0.5);
        kg(Items.COOKED_PORKCHOP, 0.4);
        kg(Items.COOKED_BEEF, 0.4);
        kg(Items.COOKED_CHICKEN, 0.3);
        kg(Items.COOKED_RABBIT, 0.3);
        kg(Items.COOKED_MUTTON, 0.4);
        kg(Items.SALMON, 1.0);
        kg(Items.COOKED_SALMON, 0.8);
        kg(Items.POTATO, 0.2);
        kg(Items.BAKED_POTATO, 0.2);
        kg(Items.CARROT, 0.1);
        kg(Items.BEETROOT, 0.3);
        kg(Items.APPLE, 0.2);
    }

    private ModWeights() {}

    private record TagWeight(TagKey<Item> tag, double grams) {}

    // ----- Задание веса -----

    public static void grams(ItemLike item, double grams) {
        put(BuiltInRegistries.ITEM.getKey(item.asItem()), grams);
    }

    public static void kg(ItemLike item, double kg) {
        grams(item, kg * 1000);
    }

    public static void grams(TagKey<Item> tag, double grams) {
        BY_TAG.add(new TagWeight(tag, grams));
    }

    public static void kg(TagKey<Item> tag, double kg) {
        grams(tag, kg * 1000);
    }

    public static void grams(String id, double grams) {
        if (id.startsWith("#")) {
            grams(TagKey.create(Registries.ITEM, ResourceLocation.parse(id.substring(1))), grams);
        } else {
            put(ResourceLocation.parse(id), grams);
        }
    }

    public static void kg(String id, double kg) {
        grams(id, kg * 1000);
    }

    /** Используется и билдером ModItems. */
    public static void put(ResourceLocation itemId, double grams) {
        if (BY_ID.containsKey(itemId)) {
            throw new IllegalArgumentException("Вес для " + itemId + " задан дважды");
        }
        BY_ID.put(itemId, grams);
    }

    // ----- Подсчёт -----

    // Итоговый вес по предмету: инвентари пересчитываются постоянно, а id и теги каждый раз искать дорого.
    // Сбрасывается, когда меняются теги (вход в мир, /reload). Читается и сервером, и клиентом — поэтому Concurrent
    private static final Map<Item, Double> RESOLVED = new ConcurrentHashMap<>();

    public static void clearCache() {
        RESOLVED.clear();
    }

    /** Вес одного предмета. */
    public static double unitGrams(ItemStack stack) {
        return RESOLVED.computeIfAbsent(stack.getItem(), item -> resolve(new ItemStack(item)));
    }

    private static double resolve(ItemStack stack) {
        Double exact = BY_ID.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (exact != null) {
            return exact;
        }
        for (TagWeight tagWeight : BY_TAG) {
            if (stack.is(tagWeight.tag())) {
                return tagWeight.grams();
            }
        }
        // Семена и корнеплоды — «блоки», но весят как мелочь
        boolean block = stack.getItem() instanceof BlockItem && !(stack.getItem() instanceof ItemNameBlockItem);
        return block ? DEFAULT_BLOCK_GRAMS : DEFAULT_ITEM_GRAMS;
    }

    /** Вес всего стака, вместе с содержимым шалкеров, сумок, колчана, горшка и корчаги. */
    public static double stackGrams(ItemStack stack) {
        return stackGrams(stack, 0);
    }

    private static double stackGrams(ItemStack stack, int depth) {
        if (stack.isEmpty()) {
            return 0;
        }
        double one = unitGrams(stack);
        if (depth < 4) {
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
            if (container != null) {
                for (ItemStack inner : container.nonEmptyItems()) {
                    one += stackGrams(inner, depth + 1);
                }
            }
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) {
                for (ItemStack inner : bundle.items()) {
                    one += stackGrams(inner, depth + 1);
                }
            }
            ItemContainerContents bag = stack.get(ModComponents.BAG_CONTENTS.get());
            if (bag != null) {
                for (ItemStack inner : bag.nonEmptyItems()) {
                    one += stackGrams(inner, depth + 1);
                }
            }
            Stored stored = stack.get(ModComponents.STORED.get());
            if (stored != null) {
                one += stackGrams(stored.stack(), depth + 1);
            }
            Integer water = stack.get(ModComponents.WATER.get());
            if (water != null) {
                one += water * WATER_MEASURE_GRAMS;
            }
            List<ItemStack> ring = stack.get(ModComponents.KEY_RING.get());
            if (ring != null) {
                for (ItemStack inner : ring) {
                    one += stackGrams(inner, depth + 1);
                }
            }
        }

        return one * stack.getCount();
    }

    /** Всё, что несёт игрок: инвентарь, надетая броня и снаряжение с сумками, вторая рука и предмет на курсоре. */
    public static double inventoryGrams(Player player) {
        Inventory inventory = player.getInventory();
        double total = 0;
        for (ItemStack stack : inventory.items) {
            total += stackGrams(stack);
        }
        for (ItemStack stack : inventory.armor) {
            total += stackGrams(stack);
        }
        for (ItemStack stack : inventory.offhand) {
            total += stackGrams(stack);
        }
        ArmorInventory armor = player.getData(ModAttachments.ARMOR);
        for (int i = 0; i < armor.getSlots(); i++) {
            total += stackGrams(armor.getStackInSlot(i));
        }
        GearInventory gear = player.getData(ModAttachments.GEAR); // сумки — вместе с содержимым
        for (int i = 0; i < gear.getSlots(); i++) {
            total += stackGrams(gear.getStackInSlot(i));
        }
        total += stackGrams(player.containerMenu.getCarried());
        return total;
    }
}