package com.nrdhvnstd.halgventmod.client.map;

import net.minecraft.world.item.DyeColor;

/** Метка на карте и в мире. Поля открыты для Gson и окна правки. */
public final class Waypoint {
    public String name;
    public int x;
    public int y;
    public int z;
    /** Значок — MapIcon.id(). */
    public String icon;
    /** Цвет флага — имя красителя; у прочих значков не используется. */
    public String dye;
    /** Значок виден на компасе вверху экрана (CompassHud). У старых меток — false. */
    public boolean compass;
    /** Только у меток старого формата (квадратик с буквой): RGB, из него берётся цвет флага. */
    public Integer color;

    public Waypoint(String name, int x, int y, int z, MapIcon icon, DyeColor dye) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.icon = icon.id();
        this.dye = dye.getSerializedName();
    }

    public MapIcon icon() {
        return MapIcon.byId(icon);
    }

    public DyeColor dye() {
        return DyeColor.byName(dye, DyeColor.RED);
    }

    /** Клетка атласа значков. */
    public int cell() {
        return icon().cell(dye());
    }

    /** Метка старого формата: флаг ближайшего красителя. true — что-то поменялось. */
    boolean upgrade() {
        if (icon != null && dye != null) {
            return false;
        }
        if (icon == null) {
            icon = MapIcon.FLAG.id();
        }
        if (dye == null) {
            dye = nearestDye(color == null ? 0xB02E26 : color).getSerializedName();
        }
        color = null;
        return true;
    }

    private static DyeColor nearestDye(int rgb) {
        DyeColor best = DyeColor.RED;
        int bestDistance = Integer.MAX_VALUE;
        for (DyeColor dye : DyeColor.values()) {
            int other = dye.getTextureDiffuseColor();
            int dr = ((rgb >> 16) & 0xFF) - ((other >> 16) & 0xFF);
            int dg = ((rgb >> 8) & 0xFF) - ((other >> 8) & 0xFF);
            int db = (rgb & 0xFF) - (other & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = dye;
            }
        }
        return best;
    }
}
