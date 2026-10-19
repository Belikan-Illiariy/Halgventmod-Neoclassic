package com.nrdhvnstd.halgventmod.gear;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;

/**
 * Девять ячеек снаряжения справа от фигурки, сетка 3×3 по строкам.
 * Папка = имя ячейки в сохранении и в ключе перевода.
 */
public enum GearSlot {
    //          папка        по-русски    по-английски  вид снаряжения    висит на поясе
    RING       ("ring",      "Кольцо",    "Ring",       GearType.RING,     false),
    NECK       ("neck",      "Шея",       "Neck",       GearType.NECK,     false),
    BRACELET   ("bracelet",  "Браслет",   "Bracelet",   GearType.BRACELET, false),
    LANTERN    ("lantern",   "Фонарь",    "Lantern",    GearType.LANTERN,  true),
    BELT       ("belt",      "Пояс",      "Belt",       GearType.BELT,     false),
    QUIVER     ("quiver",    "Колчан",    "Quiver",     GearType.QUIVER,   true),
    BACK       ("back",      "Спина",     "Back",       GearType.BACK,     false),
    POUCH_1    ("pouch_1",   "Подсумок 1", "Pouch 1",   GearType.POUCH,    true),
    POUCH_2    ("pouch_2",   "Подсумок 2", "Pouch 2",   GearType.POUCH,    true);

    public static final int COUNT = values().length;
    public static final int COLUMNS = 3;

    private final String folder;
    private final String nameRu;
    private final String nameEn;
    private final GearType type;
    private final boolean onBelt;

    GearSlot(String folder, String nameRu, String nameEn, GearType type, boolean onBelt) {
        this.folder = folder;
        this.nameRu = nameRu;
        this.nameEn = nameEn;
        this.type = type;
        this.onBelt = onBelt;
    }

    public String folder() {
        return folder;
    }

    public String nameRu() {
        return nameRu;
    }

    public String nameEn() {
        return nameEn;
    }

    public GearType type() {
        return type;
    }

    /** Висит на поясе: без пояса не надеть, пока висит — пояс не снять. */
    public boolean onBelt() {
        return onBelt;
    }

    /** Ключ перевода названия ячейки: gear_slot.halgventmod.belt */
    public String langKey() {
        return "gear_slot." + HalgventMod.MODID + "." + folder;
    }

    @Nullable
    public static GearSlot byFolder(String folder) {
        for (GearSlot slot : values()) {
            if (slot.folder.equals(folder)) {
                return slot;
            }
        }
        return null;
    }
}
