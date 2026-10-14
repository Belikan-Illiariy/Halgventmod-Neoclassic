package com.nrdhvnstd.halgventmod.squad;

import java.util.Locale;

/**
 * Кто из отряда видит товарищей на карте мира:
 * OFF — никто; RANKS — лидер и участники с отмеченными лидером званиями; ALL — все.
 */
public enum SquadMapMode {
    OFF, RANKS, ALL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Следующий режим по кругу — для иконки в окне отряда. */
    public SquadMapMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static SquadMapMode byId(String id) {
        for (SquadMapMode mode : values()) {
            if (mode.id().equals(id)) {
                return mode;
            }
        }
        return ALL;
    }
}
