package com.nrdhvnstd.halgventmod.food;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Голод игрока: сытость, насыщение и накопленная усталость. Хранится на игроке. */
public class HungerData {
    public static final int MAX = 100;

    public static final Codec<HungerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(data -> data.level),
            Codec.FLOAT.fieldOf("saturation").forGetter(data -> data.saturation),
            Codec.FLOAT.fieldOf("exhaustion").forGetter(data -> data.exhaustion)
    ).apply(instance, HungerData::new));

    int level = MAX;
    float saturation = 25; // как у ванильного игрока при появлении (5 из 20)
    float exhaustion;

    public HungerData() {}

    HungerData(int level, float saturation, float exhaustion) {
        this.level = level;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
    }

    public int level() {
        return level;
    }

    public float saturation() {
        return saturation;
    }

    /** Только для клиента: копия значений, пришедших с сервера. На сервере — Hunger.add(). */
    public void setClient(int level, float saturation) {
        this.level = level;
        this.saturation = saturation;
    }
}
