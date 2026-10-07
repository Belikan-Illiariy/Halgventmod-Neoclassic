package com.nrdhvnstd.halgventmod.armor;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;

/**
 * Двенадцать слотов брони вместо четырёх ванильных.
 * Папка = textures/armor/<папка>/ и имя слота в сохранении.
 * Порядок отрисовки: больше число — ближе к внешней стороне.
 */
public enum ArmorSlot {
    //           папка          по-русски      по-английски    часть тела      плоскость     порядок
    CLOAK       ("cloak",       "Накидка",     "Cloak",        BodyPart.HEAD,  Plane.OUTER,  12),
    HELMET      ("helmet",      "Шлем",        "Helmet",       BodyPart.HEAD,  Plane.OUTER,  11),
    COIF        ("coif",        "Койф",        "Coif",         BodyPart.HEAD,  Plane.OUTER,  7),
    ARMING_CAP  ("arming_cap",  "Подшлемник",  "Arming cap",   BodyPart.HEAD,  Plane.OUTER,  5),
    GLOVES      ("gloves",      "Кисти",       "Gloves",       BodyPart.TORSO, Plane.OUTER,  9),
    BREASTPLATE ("breastplate", "Нагрудник",   "Breastplate",  BodyPart.TORSO, Plane.OUTER,  10),
    MAIL        ("mail",        "Кольчуга",    "Mail",         BodyPart.TORSO, Plane.OUTER,  8),
    PADDING     ("padding",     "Поддоспех",   "Padding",      BodyPart.TORSO, Plane.OUTER,  6),
    BOOTS       ("boots",       "Обувь",       "Boots",        BodyPart.LEGS,  Plane.LEGS,   3),
    GREAVES     ("greaves",     "Поножи",      "Greaves",      BodyPart.LEGS,  Plane.LEGS,   4),
    CHAUSSES    ("chausses",    "Шоссы",       "Chausses",     BodyPart.LEGS,  Plane.LEGS,   2),
    TROUSERS    ("trousers",    "Штаны",       "Trousers",     BodyPart.LEGS,  Plane.LEGS,   1);

    public static final int COUNT = values().length;

    /** Часть тела: по ней слоты группируются в окне инвентаря. */
    public enum BodyPart { HEAD, TORSO, LEGS }

    /** Плоскость отрисовки: ноги (как ванильные поножи) или внешняя (как нагрудник). */
    public enum Plane { LEGS, OUTER }

    private final String folder;
    private final String nameRu;
    private final String nameEn;
    private final BodyPart bodyPart;
    private final Plane plane;
    private final int defaultOrder;

    ArmorSlot(String folder, String nameRu, String nameEn, BodyPart bodyPart, Plane plane, int defaultOrder) {
        this.folder = folder;
        this.nameRu = nameRu;
        this.nameEn = nameEn;
        this.bodyPart = bodyPart;
        this.plane = plane;
        this.defaultOrder = defaultOrder;
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

    public BodyPart bodyPart() {
        return bodyPart;
    }

    public Plane plane() {
        return plane;
    }

    public int defaultOrder() {
        return defaultOrder;
    }

    /** Ключ перевода названия слота: armor_slot.halgventmod.helmet */
    public String langKey() {
        return "armor_slot." + HalgventMod.MODID + "." + folder;
    }

    @Nullable
    public static ArmorSlot byFolder(String folder) {
        for (ArmorSlot slot : values()) {
            if (slot.folder.equals(folder)) {
                return slot;
            }
        }
        return null;
    }
}
