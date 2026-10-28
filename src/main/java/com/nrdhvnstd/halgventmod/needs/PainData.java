package com.nrdhvnstd.halgventmod.needs;

import com.mojang.serialization.Codec;

/** Боль игрока (ТЗ боя, 8). Сохраняется только сама боль; шок и иммунитет к нему после выхода пропадают. */
public class PainData {
    public static final Codec<PainData> CODEC = Codec.FLOAT.xmap(PainData::new, data -> data.level);

    float level;

    // Служебное, не сохраняется
    int shockTicks;            // сколько ещё длится болевой шок
    long immuneUntil;          // до какого тика шок не наступает снова
    long lastCombat = Long.MIN_VALUE / 2; // когда последний раз бил или получил удар — для «в бою»
    int movePenalty = -1;      // какой штраф к ходьбе сейчас лежит на игроке (×100), чтобы не трогать атрибут каждый тик

    public PainData() {}

    private PainData(float level) {
        this.level = level;
    }

    public float level() {
        return level;
    }

    public int shockTicks() {
        return shockTicks;
    }

    /** Только для клиента: копия с сервера. */
    public void setClient(float level, int shockTicks) {
        this.level = level;
        this.shockTicks = shockTicks;
    }
}
