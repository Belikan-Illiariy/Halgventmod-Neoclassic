package com.nrdhvnstd.halgventmod.npc;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

/**
 * Вид точки интереса: куда горожане ходят в течение дня (docs/TZ_npc.md). Вес — как охотно идут сюда в дневные
 * часы и вечером; ноль — туда в это время не ходят. ⚠️ Веса подобраны на глаз.
 */
public enum PoiType {
    SHOP("shop", 2, 0, 0xFFC040),
    MARKET("market", 3, 0, 0x40C0FF),
    TAVERN("tavern", 1, 4, 0xFF6040),
    SQUARE("square", 2, 2, 0x60E060);

    private final String id;
    private final int dayWeight;
    private final int eveningWeight;
    private final int color;

    PoiType(String id, int dayWeight, int eveningWeight, int color) {
        this.id = id;
        this.dayWeight = dayWeight;
        this.eveningWeight = eveningWeight;
        this.color = color;
    }

    public String id() {
        return id;
    }

    /** Есть ли здесь стулья: горожанин садится на свободный (NpcSeats). */
    public boolean hasSeats() {
        return this == TAVERN;
    }

    /** Цвет частиц, которыми жезл показывает точки (RGB). */
    public int color() {
        return color;
    }

    public Component title() {
        return Component.translatable("npc.halgventmod.poi." + id);
    }

    /** Вес при выборе цели для этого занятия; сон — ноль. */
    public int weight(NpcSchedule.Activity activity) {
        return switch (activity) {
            case WORK -> dayWeight;
            case HOME -> eveningWeight;
            case SLEEP -> 0;
        };
    }

    @Nullable
    public static PoiType byId(String id) {
        for (PoiType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
