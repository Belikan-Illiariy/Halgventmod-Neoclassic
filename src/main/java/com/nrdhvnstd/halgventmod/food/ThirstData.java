package com.nrdhvnstd.halgventmod.food;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Жажда игрока. Хранится на игроке и сохраняется вместе с миром. */
public class ThirstData {
    public static final int MAX = 20; // 20 = 10 капель, как 10 окорочков у голода

    public static final Codec<ThirstData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(data -> data.level),
            Codec.FLOAT.fieldOf("exhaustion").forGetter(data -> data.exhaustion)
    ).apply(instance, ThirstData::new));

    int level = MAX;
    float exhaustion;

    // Служебное, не сохраняется
    int damageTimer;
    int lastDrinkTick = -1000;
    int lastSyncedLevel = -1;
    float lastSyncedSaturation = -1;

    public ThirstData() {}

    ThirstData(int level, float exhaustion) {
        this.level = level;
        this.exhaustion = exhaustion;
    }

    public int level() {
        return level;
    }

    /** Только для клиента: копия значения, пришедшего с сервера. На сервере — Thirst.add(). */
    public void setClientLevel(int level) {
        this.level = level;
    }
}