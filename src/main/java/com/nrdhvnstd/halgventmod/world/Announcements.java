package com.nrdhvnstd.halgventmod.world;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Очередь надписей вверху экрана на клиенте (рисует client.TopBanner): «Открытая локация» и смена суток.
 * Показываются по одной, следующая — когда погасла предыдущая. Совпали открытие локации и рассвет —
 * сначала локация: она встаёт перед ещё не начатыми рассветами, а только что начатый рассвет уступает ей место.
 * Без клиентских классов: сюда пишут обработчики пакетов.
 */
public final class Announcements {
    /** Сколько миллисекунд рассвет может уже показываться и всё равно уступить локации. */
    private static final long YIELD_MILLIS = 300;
    public static final int COLOR_LABEL = ChatFormatting.GRAY.getColor();
    public static final int COLOR_MAIN = ChatFormatting.GOLD.getColor();

    /** Строка надписи: крупная (название, дата) или мелкая (подпись). */
    public record Line(Component text, boolean big, int color) {}

    public record Entry(boolean location, List<Line> lines) {}

    private static final List<Entry> QUEUE = new ArrayList<>();
    @Nullable
    private static Entry current;
    private static long startedMillis;

    private Announcements() {}

    /** «Открытая локация» серым и под ней название золотом. */
    public static void location(String name) {
        Entry entry = new Entry(true, List.of(
                new Line(Component.translatable("hud.halgventmod.point.discovered"), false, COLOR_LABEL),
                new Line(Component.literal(name), true, COLOR_MAIN)));
        if (current != null && !current.location() && System.currentTimeMillis() - startedMillis < YIELD_MILLIS) {
            QUEUE.add(0, current); // рассвет только начался — пусть подождёт
            current = null;
        }
        int index = 0;
        while (index < QUEUE.size() && QUEUE.get(index).location()) {
            index++;
        }
        QUEUE.add(index, entry);
    }

    /** Смена суток: дата крупно, под ней время года и год (цвета — в самих строках). */
    public static void dawn(Component title, Component subtitle) {
        QUEUE.add(new Entry(false, List.of(
                new Line(title, true, COLOR_MAIN),
                new Line(subtitle, false, COLOR_LABEL))));
    }

    /** Что показывать сейчас; нет текущей — берётся следующая из очереди. */
    @Nullable
    public static Entry current() {
        if (current == null && !QUEUE.isEmpty()) {
            current = QUEUE.remove(0);
            startedMillis = System.currentTimeMillis();
        }
        return current;
    }

    public static long age() {
        return System.currentTimeMillis() - startedMillis;
    }

    /** Текущая погасла. */
    public static void finish() {
        current = null;
    }

    /** Выход из мира. */
    public static void clear() {
        QUEUE.clear();
        current = null;
    }
}
