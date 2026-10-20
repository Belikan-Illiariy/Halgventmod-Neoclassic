package com.nrdhvnstd.halgventmod.leather;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.entity.EntityType;

/**
 * С кого снимается шкура и что из неё выйдет. Меняйте здесь.
 *
 * <p>kind(тип, id, кусков кожи, с мехом, пергаментная, качество по умолчанию 0–4, по-русски, по-английски)
 * <ul>
 *   <li>кусков — сколько штук дублёной (мягкой, сыромятной…) кожи выйдет из целой шкуры;</li>
 *   <li>с мехом — шкуру можно выделать в мех (жировой чан), иначе мех на ней — просто шерсть;</li>
 *   <li>пергаментная — из голья мелкой шкуры выскабливается пергамент;</li>
 *   <li>название — прилагательное для подсказки: «Воловья шкура · кусков кожи: 5».</li>
 * </ul>
 */
public final class Animals {
    public record Kind(String id, int pieces, boolean fur, boolean parchment, int baseQuality, String nameRu,
                       String nameEn) {}

    private static final Map<EntityType<?>, Kind> BY_TYPE = new LinkedHashMap<>();
    private static final Map<String, Kind> BY_ID = new HashMap<>();

    static {
        kind(EntityType.RABBIT,     "rabbit",     1, true,  true,  0, "Кроличья",    "Rabbit");
        kind(EntityType.FOX,        "fox",        2, true,  false, 1, "Лисья",       "Fox");
        kind(EntityType.WOLF,       "wolf",       2, true,  false, 1, "Волчья",      "Wolf");
        kind(EntityType.GOAT,       "goat",       2, false, true,  0, "Козья",       "Goat");
        kind(EntityType.SHEEP,      "sheep",      3, true,  true,  0, "Овечья",      "Sheep");
        kind(EntityType.PIG,        "pig",        3, false, false, 0, "Свиная",      "Pig");
        kind(EntityType.HOGLIN,     "hoglin",     4, false, false, 0, "Кабанья",     "Boar");
        kind(EntityType.COW,        "cow",        5, false, false, 0, "Воловья",     "Cowhide");
        kind(EntityType.MOOSHROOM,  "cow",        5, false, false, 0, "Воловья",     "Cowhide");
        kind(EntityType.HORSE,      "horse",      5, false, false, 0, "Конская",     "Horse");
        kind(EntityType.DONKEY,     "horse",      4, false, false, 0, "Конская",     "Horse");
        kind(EntityType.MULE,       "horse",      4, false, false, 0, "Конская",     "Horse");
        kind(EntityType.LLAMA,      "llama",      4, true,  false, 0, "Ламья",       "Llama");
        kind(EntityType.POLAR_BEAR, "bear",       6, true,  false, 2, "Медвежья",    "Bear");
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

    /** Ключ перевода прилагательного: hide.halgventmod.cow */
    public static String langKey(String id) {
        return "hide.halgventmod." + id;
    }

    private static void kind(EntityType<?> type, String id, int pieces, boolean fur, boolean parchment,
                             int baseQuality, String nameRu, String nameEn) {
        Kind kind = new Kind(id, pieces, fur, parchment, baseQuality, nameRu, nameEn);
        BY_TYPE.put(type, kind);
        BY_ID.putIfAbsent(id, kind);
    }
}
