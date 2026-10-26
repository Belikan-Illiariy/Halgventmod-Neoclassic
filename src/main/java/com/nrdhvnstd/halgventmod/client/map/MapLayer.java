package com.nrdhvnstd.halgventmod.client.map;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.network.chat.Component;

/**
 * Слои окон карты — галочки в правой панели. Снятая галочка не рисует слой и выключает наведение на его элементы.
 * Состояние общее для всех окон карты и помнится между открытиями (до выхода из игры).
 */
public enum MapLayer {
    DRAWING, WAYPOINTS, POINTS, ROUTES, NPC, BIOMES, SQUAD;

    private static final Set<MapLayer> HIDDEN = EnumSet.noneOf(MapLayer.class);

    public boolean shown() {
        return !HIDDEN.contains(this);
    }

    public void setShown(boolean shown) {
        if (shown) {
            HIDDEN.remove(this);
        } else {
            HIDDEN.add(this);
        }
    }

    public Component title() {
        return Component.translatable("gui.halgventmod.map.layer." + name().toLowerCase(Locale.ROOT));
    }
}
