package com.nrdhvnstd.halgventmod.item;

import java.util.Locale;

/** Род названия предмета: от него зависит форма признака — [Сырая] шкура, но [Сырой] пергамент. */
public enum Gender {
    M,  // мужской:пергамент, волос
    F,  // женский:шкура, кожа, мездра
    N,  // средний:мясо, сало
    PL; // множественное: ножницы, клещи

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}