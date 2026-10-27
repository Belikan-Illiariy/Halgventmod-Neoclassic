package com.nrdhvnstd.halgventmod.food;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Жажда игрока. Хранится на игроке и сохраняется вместе с миром. */
public class ThirstData {
    public static final int MAX = 100;

    // "max" — шкала, в которой записан level. В старых сохранениях его нет: там жажда была 0–20
    public static final Codec<ThirstData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(data -> data.level),
            Codec.FLOAT.fieldOf("exhaustion").forGetter(data -> data.exhaustion),
            Codec.INT.optionalFieldOf("max", 20).forGetter(data -> MAX)
    ).apply(instance, ThirstData::new));

    int level = MAX;
    float exhaustion;

    // Служебное, не сохраняется
    int lastDrinkTick = -1000;

    public ThirstData() {}

    ThirstData(int level, float exhaustion, int savedMax) {
        this.level = savedMax == MAX ? level : Math.round(level * (float) MAX / savedMax);
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
