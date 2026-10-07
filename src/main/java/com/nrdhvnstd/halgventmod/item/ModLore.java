package com.nrdhvnstd.halgventmod.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Описания (lore) любых предметов: мода, ванильных и других модов. */
public final class ModLore {
    public static final int DEFAULT_COLOR = 0xAAAAAA; // серый

    // Должно стоять ВЫШЕ блока с описаниями
    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();

    private static final String SECTION = "\u00A7"; // знак §, которым Minecraft задаёт форматирование

    /** **жирный** → §l…§r, *курсив* → §o…§r */
    public static String markup(String text) {
        return text
                .replaceAll("(?s)\\*\\*(.+?)\\*\\*", SECTION + "l$1" + SECTION + "r")
                .replaceAll("(?s)\\*(.+?)\\*", SECTION + "o$1" + SECTION + "r");
    }

    // ===== Описания ванильных предметов и предметов других модов =====
    // Для предметов своего мода удобнее писать .lore(...) прямо в ModItems.
    //
    // lore(Items.<ПРЕДМЕТ>, "по-русски", "по-английски")            — серым
    // lore(Items.<ПРЕДМЕТ>, "по-русски", "по-английски", 0xRRGGBB)  — своим цветом
    // lore("othermod:item", "по-русски", "по-английски")            — предмет другого мода по ID
    static {
        // Пример:
        // lore(Items.IRON_INGOT, "Крица, перекованная до сносного железа.", "A bloom beaten into passable iron.");
    }

    private ModLore() {}

    public record Entry(String ru, String en, int color) {}

    public static void lore(ItemLike item, String ru, String en) {
        lore(item, ru, en, DEFAULT_COLOR);
    }

    public static void lore(ItemLike item, String ru, String en, int color) {
        put(BuiltInRegistries.ITEM.getKey(item.asItem()), ru, en, color);
    }

    public static void lore(String itemId, String ru, String en) {
        lore(itemId, ru, en, DEFAULT_COLOR);
    }

    public static void lore(String itemId, String ru, String en, int color) {
        put(ResourceLocation.parse(itemId), ru, en, color);
    }

    /** Используется и билдером ModItems. */
    public static void put(ResourceLocation itemId, String ru, String en, int color) {
        if (ENTRIES.containsKey(itemId)) {
            throw new IllegalArgumentException("Описание для " + itemId + " задано дважды");
        }
        ENTRIES.put(itemId, new Entry(ru, en, color));
    }

    @Nullable
    public static Entry get(Item item) {
        return ENTRIES.get(BuiltInRegistries.ITEM.getKey(item));
    }

    public static Map<ResourceLocation, Entry> entries() {
        return Collections.unmodifiableMap(ENTRIES);
    }

    /** Ключ перевода описания: item.minecraft.iron_ingot.lore */
    public static String langKey(Item item) {
        return item.getDescriptionId() + ".lore";
    }

    /** То же по ID; работает и для предметов модов, которых нет при генерации. */
    public static String langKey(ResourceLocation itemId) {
        return BuiltInRegistries.ITEM.getOptional(itemId)
                .map(Item::getDescriptionId)
                .orElseGet(() -> Util.makeDescriptionId("item", itemId)) + ".lore";
    }
}