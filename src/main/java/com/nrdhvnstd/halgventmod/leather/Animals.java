package com.nrdhvnstd.halgventmod.leather;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.entity.EntityType;

/**
 * С кого снимается шкура и что из неё выйдет. Меняйте здесь.
 *
 * <p>kind(тип, id, кусков кожи, с мехом, пергаментная, качество по умолчанию 0–4, по-русски, чей (для меха), по-английски)
 * <ul>
 *   <li>кусков — сколько штук дублёной (мягкой, сыромятной…) кожи выйдет из целой шкуры;</li>
 *   <li>с мехом — шкуру можно выделать в мех (жировой чан), иначе мех на ней — просто шерсть;</li>
 *   <li>пергаментная — из голья мелкой шкуры выскабливается пергамент;</li>
 *   <li>название — прилагательное: «Воловья шкура», «Солёная воловья шкура»; чей — «Мех волка».</li>
 * </ul>
 */
public final class Animals {
    public record Kind(String id, int pieces, boolean fur, boolean parchment, int baseQuality, String nameRu,
                       String ofRu, String nameEn) {}

    private static final Map<EntityType<?>, Kind> BY_TYPE = new LinkedHashMap<>();
    private static final Map<String, Kind> BY_ID = new LinkedHashMap<>();

    static {
        kind(EntityType.RABBIT,     "rabbit",     1, true,  true,  0, "Кроличья",    "кролика",   "Rabbit");
        kind(EntityType.FOX,        "fox",        2, true,  false, 1, "Лисья",       "лисы",      "Fox");
        kind(EntityType.WOLF,       "wolf",       2, true,  false, 1, "Волчья",      "волка",     "Wolf");
        kind(EntityType.GOAT,       "goat",       2, false, true,  0, "Козья",       "козы",      "Goat");
        kind(EntityType.SHEEP,      "sheep",      3, true,  true,  0, "Овечья",      "овцы",      "Sheep");
        kind(EntityType.PIG,        "pig",        3, false, false, 0, "Свиная",      "свиньи",    "Pig");
        kind(EntityType.HOGLIN,     "hoglin",     4, false, false, 0, "Кабанья",     "кабана",    "Boar");
        kind(EntityType.COW,        "cow",        5, false, false, 0, "Воловья",     "вола",      "Cow");
        kind(EntityType.MOOSHROOM,  "cow",        5, false, false, 0, "Воловья",     "вола",      "Cow");
        kind(EntityType.HORSE,      "horse",      5, false, false, 0, "Конская",     "коня",      "Horse");
        kind(EntityType.DONKEY,     "horse",      4, false, false, 0, "Конская",     "коня",      "Horse");
        kind(EntityType.MULE,       "horse",      4, false, false, 0, "Конская",     "коня",      "Horse");
        kind(EntityType.LLAMA,      "llama",      4, true,  false, 0, "Ламья",       "ламы",      "Llama");
        kind(EntityType.TRADER_LLAMA, "llama",    4, true,  false, 0, "Ламья",       "ламы",      "Llama");
        kind(EntityType.POLAR_BEAR, "bear",       6, true,  false, 2, "Медвежья",    "медведя",   "Bear");
        kind(EntityType.CAMEL,      "camel",      5, false, false, 0, "Верблюжья",   "верблюда",  "Camel");
        kind(EntityType.OCELOT,     "ocelot",     1, true,  false, 2, "Оцелотовая",  "оцелота",   "Ocelot");
    }

    private Animals() {}

    @Nullable
    public static Kind get(EntityType<?> type) {
        return BY_TYPE.get(type);
    }

    @Nullable
    public static Kind byId(String id) {
        return BY_ID.get(id);
    }

    public static Iterable<Kind> all() {
        return BY_ID.values();
    }

    /** Номер зверя в списке (с 1; 0 — неизвестный) — по нему модель предмета берёт иконку шкуры. */
    public static int index(String id) {
        int i = 1;
        for (String key : BY_ID.keySet()) {
            if (key.equals(id)) {
                return i;
            }
            i++;
        }
        return 0;
    }

    /** Ключ перевода прилагательного: hide.halgventmod.cow */
    public static String langKey(String id) {
        return "hide.halgventmod." + id;
    }

    private static void kind(EntityType<?> type, String id, int pieces, boolean fur, boolean parchment,
                             int baseQuality, String nameRu, String ofRu, String nameEn) {
        Kind kind = new Kind(id, pieces, fur, parchment, baseQuality, nameRu, ofRu, nameEn);
        BY_TYPE.put(type, kind);
        BY_ID.putIfAbsent(id, kind);
    }
}
