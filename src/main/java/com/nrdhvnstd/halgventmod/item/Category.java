package com.nrdhvnstd.halgventmod.item;

import com.nrdhvnstd.halgventmod.HalgventMod;

/**
 * Категории предметов. Категория = вкладка в креативе = папка иконок:
 * assets/halgventmod/textures/item/<папка>/<id>.png
 * Порядок здесь = порядок вкладок в креативе.
 */
public enum Category {
    //        папка        по-русски         по-английски     в руке как оружие
    ARMOR    ("armor",     "Доспехи",        "Armor",         false),
    WEAPONS  ("weapons",   "Оружие",         "Weapons",       true),
    TOOLS    ("tools",   "Инструменты",         "Tools",       true),
    FOOD     ("food",      "Пища",           "Food",          false),
    ALCOHOL  ("alcohol",   "Алкоголь",       "Alcohol",       false),
    JEWELRY  ("jewelry",   "Драгоценности",  "Jewelry",       false),
    METALS   ("metals",    "Металлы",        "Metals",        false),
    MINERALS ("minerals",  "Минералы",       "Minerals",      false),
    MATERIALS("materials", "Материалы",      "Materials",     false),
    MISC     ("misc",      "Прочее",         "Miscellaneous", false);

    private final String folder;
    private final String nameRu;
    private final String nameEn;
    private final boolean handheld;

    Category(String folder, String nameRu, String nameEn, boolean handheld) {
        this.folder = folder;
        this.nameRu = nameRu;
        this.nameEn = nameEn;
        this.handheld = handheld;
    }

    /** Папка иконок и ID вкладки. */
    public String folder() {
        return folder;
    }

    public String nameRu() {
        return nameRu;
    }

    public String nameEn() {
        return nameEn;
    }

    /** true — предмет держится в руке наискосок, как меч. */
    public boolean handheld() {
        return handheld;
    }

    /** Ключ перевода названия вкладки: itemGroup.halgventmod.metals */
    public String langKey() {
        return "itemGroup." + HalgventMod.MODID + "." + folder;
    }
}