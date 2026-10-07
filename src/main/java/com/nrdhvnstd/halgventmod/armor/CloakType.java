package com.nrdhvnstd.halgventmod.armor;

/** Вид накидки: капюшон — только голова, плащ — спина и плечи, или и то и другое. */
public enum CloakType {
    HOOD(true, false),
    CAPE(false, true),
    HOODED_CAPE(true, true);

    private final boolean hood;
    private final boolean cape;

    CloakType(boolean hood, boolean cape) {
        this.hood = hood;
        this.cape = cape;
    }

    /** Есть капюшон: на него действует правило «капюшон — шлем». */
    public boolean hasHood() {
        return hood;
    }

    /** Есть плащ: своя развевающаяся модель. */
    public boolean hasCape() {
        return cape;
    }
}
