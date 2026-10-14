package com.nrdhvnstd.halgventmod.storage;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.gear.GearSlot;

/**
 * Области хранилища на месте бывшего рюкзака — сетка 9×3, как ванильный инвентарь:
 * столбцы 0–4 — спина (3×5), 5–6 — подсумок 1 (3×2), 7–8 — подсумок 2 (3×2).
 * Номер ячейки хранилища (0–26) — номер в сетке по строкам, ровно как ванильные ячейки 9–35,
 * поэтому в любом окне (сундук, печь) сумки встают на место бывшего рюкзака сами (StorageSlot).
 * Внутри области ячейки открываются по строкам: слева направо, сверху вниз.
 */
public enum StorageArea {
    //        ячейка снаряжения   первый столбец   столбцов
    BACK     (GearSlot.BACK,      0,               5),
    POUCH_1  (GearSlot.POUCH_1,   5,               2),
    POUCH_2  (GearSlot.POUCH_2,   7,               2);

    public static final int COLUMNS = 9;
    public static final int ROWS = 3;
    /** Номеров хранилища — столько же, сколько скрытых ячеек ванильного рюкзака. */
    public static final int INDEX_COUNT = COLUMNS * ROWS;
    /** Первая скрытая ячейка ванильного инвентаря; 9–35 — бывший рюкзак. */
    public static final int VANILLA_FIRST = 9;

    /** Порядок раскладки вещей: подсумки раньше спины (ТЗ 6.2). */
    public static final StorageArea[] FILL_ORDER = {POUCH_1, POUCH_2, BACK};

    private final GearSlot gearSlot;
    private final int firstColumn;
    private final int columns;

    StorageArea(GearSlot gearSlot, int firstColumn, int columns) {
        this.gearSlot = gearSlot;
        this.firstColumn = firstColumn;
        this.columns = columns;
    }

    public GearSlot gearSlot() {
        return gearSlot;
    }

    public int firstColumn() {
        return firstColumn;
    }

    public int columns() {
        return columns;
    }

    /** Ячеек в области. */
    public int size() {
        return columns * ROWS;
    }

    /** Место внутри области (по строкам) для сквозного номера сетки. */
    public int localIndex(int index) {
        return (index / COLUMNS) * columns + (index % COLUMNS - firstColumn);
    }

    /** Сквозной номер сетки для места внутри области. */
    public int gridIndex(int local) {
        return (local / columns) * COLUMNS + firstColumn + local % columns;
    }

    /** Ключ подписи области: gui.halgventmod.storage.back */
    public String langKey() {
        return "gui." + HalgventMod.MODID + ".storage." + name().toLowerCase();
    }

    /** Область по сквозному номеру сетки 0–26. */
    public static StorageArea of(int index) {
        int column = index % COLUMNS;
        for (StorageArea area : values()) {
            if (column >= area.firstColumn && column < area.firstColumn + area.columns) {
                return area;
            }
        }
        throw new IllegalArgumentException("Нет области для ячейки " + index);
    }
}
