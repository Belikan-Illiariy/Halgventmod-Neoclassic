package com.nrdhvnstd.halgventmod.pottery;

/**
 * Что вытягивают на гончарном круге. Тело — по образцу в {@link VesselGeometry}, приставные детали (ручки, носик)
 * лепят на остановленном круге. Глины на форму — комьев гончарной глины; масштаб — каким сосуд виден в мире
 * (сетка 16×16×16 вокселей = блок × масштаб).
 */
public enum VesselForm {
    //       id         комьев  масштаб  деталей  на две точки (ручка) или на одну (носик)
    CUP     ("cup",     1,      0.40F,   1,       true),
    BOWL    ("bowl",    1,      0.45F,   0,       false),
    JUG     ("jug",     2,      0.60F,   1,       true),
    POT     ("pot",     3,      0.60F,   0,       false),
    AMPHORA ("amphora", 6,      0.95F,   2,       true),
    LAMP    ("lamp",    1,      0.35F,   1,       false),
    CRUCIBLE("crucible", 1,     0.35F,   0,       false);

    private final String id;
    private final int lumps;
    private final float scale;
    private final int details;
    private final boolean handles;

    VesselForm(String id, int lumps, float scale, int details, boolean handles) {
        this.id = id;
        this.lumps = lumps;
        this.scale = scale;
        this.details = details;
        this.handles = handles;
    }

    public String id() {
        return id;
    }

    /** Сколько комьев гончарной глины уходит на сосуд. */
    public int lumps() {
        return lumps;
    }

    /** Размер в мире: сетка 16 вокселей = блок × масштаб. */
    public float scale() {
        return scale;
    }

    /** Сколько отдельных приставных деталей нужно (ручек, ушек, носиков). */
    public int details() {
        return details;
    }

    /** Деталь держится в двух местах (ручка) — иначе хватает одного (носик). */
    public boolean handles() {
        return handles;
    }

    public String langKey() {
        return "pottery.halgventmod.form." + id;
    }

    /** Название со строчной — внутри фразы: «Сырец: кувшин». */
    public String lowerKey() {
        return "pottery.halgventmod.form_lower." + id;
    }

    public static VesselForm byId(String id) {
        for (VesselForm form : values()) {
            if (form.id.equals(id)) {
                return form;
            }
        }
        return CUP;
    }

    public static VesselForm byIndex(int index) {
        VesselForm[] all = values();
        return all[Math.floorMod(index, all.length)];
    }
}
