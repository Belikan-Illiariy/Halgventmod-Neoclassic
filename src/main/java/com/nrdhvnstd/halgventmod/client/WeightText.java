package com.nrdhvnstd.halgventmod.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Форматирование веса: до 1000 гр — в граммах, дальше — в килограммах. Один знак после запятой. */
public final class WeightText {
    private WeightText() {}

    public static Component format(double grams) {
        double rounded = round(grams);
        if (rounded < 1000) {
            return Component.translatable("weight.halgventmod.grams", number(rounded));
        }
        return kg(grams / 1000.0);
    }

    public static Component kg(double kg) {
        return Component.translatable("weight.halgventmod.kilograms", number(round(kg)));
    }

    /** Число с одним знаком после запятой: 4.317 → «4,3», 5.0 → «5». Не только для веса. */
    public static String decimal(double value) {
        return number(round(value));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // 5.0 → «5», 3.3 → «3,3» (разделитель берётся из языкового файла)
    private static String number(double value) {
        String text = value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        return text.replace(".", I18n.get("weight.halgventmod.decimal"));
    }
}