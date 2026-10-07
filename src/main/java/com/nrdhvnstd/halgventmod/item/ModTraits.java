package com.nrdhvnstd.halgventmod.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;

/** Все признаки предметов: [Сырая], [Оленья], [Оружие]... */
public final class ModTraits {
    public static final int DEFAULT_COLOR = 0xAAAAAA; // серый, как у ванильных описаний

    // Эти поля должны стоять ВЫШЕ списка признаков
    private static final List<Trait> ALL = new ArrayList<>();
    private static final Map<String, Trait> BY_ID = new HashMap<>();

    // trait(id, мужской, женский, средний, множественное, английский)— серый
    // trait(id, мужской, женский, средний, множественное, английский, 0xRRGGBB)— своим цветом
    // trait(id, по-русски, по-английски)— не склоняется, серый
    // trait(id, по-русски, по-английски, 0xRRGGBB)— не склоняется, своим цветом
    // Цвет можно взять и из ванильной палитры: ChatFormatting.GOLD.getColor()
    //
    // Пример:
    // public static final Trait SILVER = trait("silver", "Серебряный", "Серебряная", "Серебряное", "Серебряные", "Silver", 0xC0C0C0);

    // ===== Редкость =====
    public static final Trait COMMON = trait("common", "Обычный", "Обычная", "Обычное", "Обычные", "Common", 0xffffff);
    public static final Trait UNCOMMON = trait("uncommon", "Необычный", "Необычная", "Необычное", "Необычные", "Uncommon", 0x009412);
    public static final Trait RARE = trait("rare", "Редкий", "Редкая", "Редкое", "Редкие", "Rare", 0x006ff5);
    public static final Trait UNIQUE = trait("unique", "Уникальный", "Уникальная", "Уникальное", "Уникальные", "Unique", 0xb80015);
    public static final Trait LEGENDARY = trait("legendary", "Легендарный", "Легендарная", "Легендарное", "Легендарные", "Legendary", 0xe6a500);
    public static final Trait MYTHICAL = trait("mythical", "Мифический", "Мифическая", "Мифическое", "Мифические", "Mythical", 0xf500b9);

    // ===== Состояния =====
    public static final Trait RAW = trait("raw", "Сырой", "Сырая", "Сырое", "Сырые", "Raw", 0xffffff);

    private ModTraits() {}

    public record Trait(String id, String m, String f, String n, String pl, String en, int color) {
        public String ru(Gender gender) {
            return switch (gender) {
                case M -> m;
                case F -> f;
                case N -> n;
                case PL -> pl;
            };
        }
    }

    public static List<Trait> all() {
        return Collections.unmodifiableList(ALL);
    }

    @Nullable
    public static Trait byId(String id) {
        return BY_ID.get(id);
    }

    /** Ключ перевода: trait.halgventmod.raw.f */
    public static String langKey(String traitId, Gender gender) {
        return "trait." + HalgventMod.MODID + "." + traitId + "." + gender.key();
    }

    private static Trait trait(String id, String m, String f, String n, String pl, String en, int color) {
        if (BY_ID.containsKey(id)) {
            throw new IllegalArgumentException("Признак " + id + " объявлен дважды");
        }
        Trait trait = new Trait(id, m, f, n, pl, en, color);
        ALL.add(trait);
        BY_ID.put(id, trait);
        return trait;
    }

    private static Trait trait(String id, String m, String f, String n, String pl, String en) {
        return trait(id, m, f, n, pl, en, DEFAULT_COLOR);
    }

    private static Trait trait(String id, String ru, String en, int color) {
        return trait(id, ru, ru, ru, ru, en, color);
    }

    private static Trait trait(String id, String ru, String en) {
        return trait(id, ru, ru, ru, ru, en, DEFAULT_COLOR);
    }
}