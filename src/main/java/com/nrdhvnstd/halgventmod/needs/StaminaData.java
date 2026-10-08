package com.nrdhvnstd.halgventmod.needs;

import com.mojang.serialization.Codec;

/** Выносливость игрока. Сохраняется только запас; после смерти — снова полная. */
public class StaminaData {
    public static final int MAX = 100;

    public static final Codec<StaminaData> CODEC = Codec.FLOAT.xmap(StaminaData::new, data -> data.level);

    float level = MAX;

    // Служебное, не сохраняется
    int exhaustedTicks;  // сколько ещё длится изнеможение
    int regenDelay;      // тиков до начала восстановления
    double lastX, lastZ; // где игрок был тиком раньше — чтобы понять, плывёт ли он

    public StaminaData() {}

    private StaminaData(float level) {
        this.level = level;
    }

    public float level() {
        return level;
    }

    public int exhaustedTicks() {
        return exhaustedTicks;
    }

    /** Только для клиента: копия значений, пришедших с сервера. */
    public void setClient(float level, int exhaustedTicks) {
        this.level = level;
        this.exhaustedTicks = exhaustedTicks;
    }

    /** Только для клиента: изнеможение идёт и между пакетами — для затемнения экрана. */
    public void tickClient() {
        if (exhaustedTicks > 0) {
            exhaustedTicks--;
        }
    }
}
