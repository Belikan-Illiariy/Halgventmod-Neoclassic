package com.nrdhvnstd.halgventmod.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Оружие Conquest Medieval Arms (conquest_armory): русские названия (сам мод перевода не имеет, ModLanguageProvider кладёт
 * их в наш ru_ru) и порядок во вкладке «Оружие» (ModCreativeTabs). Клинки и древковое — с вариантами refined_ и exquisite_.
 * Новый предмет того мода — добавить строку сюда.
 */
public final class ConquestWeapons {
    private static final String MOD = "conquest_armory";
    private static final Map<String, String> NAMES = new LinkedHashMap<>(); // id → название

    static {
        // Ножи и мечи
        melee("hunting_knife", "Охотничий нож", 'm');
        melee("quillon_dagger", "Кинжал с гардой", 'm');
        melee("shortsword", "Короткий меч", 'm');
        melee("shortsword_with_gilded_hilt", "Короткий меч с позолоченной рукоятью", 'm');
        melee("viking_sword", "Меч викингов", 'm');
        melee("crusader_sword", "Меч крестоносца", 'm');
        melee("scimitar", "Скимитар", 'm');
        melee("longsword", "Длинный меч", 'm');
        melee("bastard_sword", "Полуторный меч", 'm');
        melee("large_viking_sword", "Большой меч викингов", 'm');
        melee("leafblade_claymore", "Клеймор с листовидным клинком", 'm');
        melee("flammenschwert", "Фламберг", 'm');
        melee("zweihander", "Цвайхендер", 'm');
        // Топоры
        melee("bearded_axe", "Бородовидный топор", 'm');
        melee("viking_bearded_axe", "Бородовидный топор викингов", 'm');
        melee("horsemans_axe", "Топор всадника", 'm');
        melee("crowbeak_hatchet", "Топорик с клювом", 'm');
        melee("studded_battle_axe", "Боевой топор с шипами", 'm');
        melee("broadaxe", "Широкий топор", 'm');
        melee("bearded_great_axe", "Большой бородовидный топор", 'm');
        // Булавы и цепы
        melee("flanged_mace", "Пернач", 'm');
        melee("spiked_mace", "Шипастая булава", 'f');
        melee("shestopyor", "Шестопёр", 'm');
        melee("flail", "Боевой цеп", 'm');
        melee("spiked_flail", "Шипастый цеп", 'm');
        melee("double_flail", "Двойной цеп", 'm');
        // Древковое
        melee("polehammer", "Молот на древке", 'm');
        melee("large_bec_de_corbin", "Большой вороний клюв", 'm');
        melee("pole_flail", "Цеп на древке", 'm');
        melee("winged_spear", "Копьё с крыльями", 'n');
        melee("winged_leaf_shaped_spear", "Листовидное копьё с крыльями", 'n');
        melee("jousting_lance", "Турнирное копьё", 'n');
        melee("ranseur", "Рансер", 'm');
        melee("glaive", "Глефа", 'f');
        melee("voulge", "Вульж", 'm');
        melee("heavy_voulge", "Тяжёлый вульж", 'm');
        melee("bardiche", "Бердыш", 'm');
        melee("bill", "Билль", 'm');
        melee("bill_hook", "Билль с крюком", 'm');
        melee("simple_halberd", "Простая алебарда", 'f');
        melee("heavy_halberd", "Тяжёлая алебарда", 'f');
        melee("swiss_halberd", "Швейцарская алебарда", 'f');
        melee("poleaxe", "Поллакс", 'm');
        melee("war_scythe", "Боевая коса", 'f');
        melee("pitchfork", "Вилы", 'p');
        // Луки и арбалеты
        NAMES.put("shortbow", "Короткий лук");
        NAMES.put("steppe_recurve_bow", "Степной составной лук");
        NAMES.put("small_warbow", "Малый боевой лук");
        NAMES.put("heavy_warbow", "Тяжёлый боевой лук");
        NAMES.put("english_longbow", "Английский длинный лук");
        NAMES.put("light_crossbow", "Лёгкий арбалет");
        NAMES.put("medium_crossbow", "Средний арбалет");
        NAMES.put("heavy_crossbow", "Тяжёлый арбалет");
        // Щиты: круглые и цветные — со вторым рисунком _1
        round("wooden_round_shield", "Деревянный круглый щит");
        round("blue_round_shield", "Синий круглый щит");
        round("red_round_shield", "Красный круглый щит");
        round("yellow_round_shield", "Жёлтый круглый щит");
        round("green_round_shield", "Зелёный круглый щит");
        round("blue_and_black_round_shield", "Сине-чёрный круглый щит");
        round("red_and_black_round_shield", "Красно-чёрный круглый щит");
        round("yellow_and_black_round_shield", "Жёлто-чёрный круглый щит");
        NAMES.put("arabian_round_shield", "Арабский круглый щит");
        NAMES.put("round_shield_with_birds", "Круглый щит с птицами");
        NAMES.put("round_shield_with_triskele", "Круглый щит с трискелем");
        NAMES.put("targe_shield", "Тарч");
        NAMES.put("wooden_kiteshield", "Деревянный каплевидный щит");
        NAMES.put("yellow_kiteshield", "Жёлтый каплевидный щит");
        NAMES.put("arabian_kite_shield", "Арабский каплевидный щит");
        NAMES.put("norman_kite_shield", "Норманнский каплевидный щит");
        NAMES.put("crusaders_kite_shield", "Каплевидный щит крестоносца");
        NAMES.put("blue_heater_shield", "Синий треугольный щит");
        NAMES.put("crusaders_heater_shield", "Треугольный щит крестоносца");
        NAMES.put("fleur_de_lis_heater_shield", "Треугольный щит с лилией");
        NAMES.put("heraldic_heater_shield", "Гербовый треугольный щит");
        NAMES.put("heraldic_rounded_heater_shield", "Гербовый скруглённый щит");
        NAMES.put("heraldic_sharp_heater_shield", "Гербовый заострённый щит");
        round("heraldic_eagle_heater_shield", "Треугольный щит с орлом");
        NAMES.put("heraldic_wankel_shield", "Гербовый щит-ванкель");
        NAMES.put("wooden_wankel_shield", "Деревянный щит-ванкель");
        NAMES.put("wooden_pavise_shield", "Деревянная павеза");
        NAMES.put("pavise_shield", "Павеза");
        NAMES.put("heraldic_pavise_shield", "Гербовая павеза");
        NAMES.put("jousting_shield", "Турнирный щит");
    }

    private ConquestWeapons() {}

    // Простой, улучшенный и изысканный; род — для прилагательного: m, f, n, p (множественное)
    private static void melee(String id, String name, char gender) {
        NAMES.put(id, name);
        NAMES.put("refined_" + id, adjective("Улучшенн", gender) + " " + lower(name));
        NAMES.put("exquisite_" + id, adjective("Изысканн", gender) + " " + lower(name));
    }

    private static void round(String id, String name) {
        NAMES.put(id, name);
        NAMES.put(id + "_1", name + ", второй рисунок");
    }

    private static String adjective(String stem, char gender) {
        return stem + switch (gender) {
            case 'f' -> "ая";
            case 'n' -> "ое";
            case 'p' -> "ые";
            default -> "ый";
        };
    }

    private static String lower(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** Ключ перевода предмета → русское название. */
    public static Map<String, String> translations() {
        Map<String, String> result = new LinkedHashMap<>();
        NAMES.forEach((id, name) -> result.put("item." + MOD + "." + id, name));
        return result;
    }

    /** Предметы того мода, что есть в игре, в порядке вкладки. Мода нет — пусто. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (String id : NAMES.keySet()) {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(MOD, id)).ifPresent(items::add);
        }
        return items;
    }
}
