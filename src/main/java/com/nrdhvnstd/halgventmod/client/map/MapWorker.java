package com.nrdhvnstd.halgventmod.client.map;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * Фоновый поток карты, как MapProcessor у Xaero: чтение и запись файлов, сборка картинок по стилю,
 * построение уровней отдаления (MapPyramid). Поток один — задачи идут строго по очереди, поэтому
 * запись региона всегда заканчивается раньше, чем его снова прочитают.
 * Результаты возвращаются в главный поток через toMain и забираются там в runMainTasks — понемногу за раз.
 * Смена мира увеличивает «поколение»: результаты задач прошлого мира выбрасываются.
 */
final class MapWorker {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Halgventmod map");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final Queue<Runnable> MAIN = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final AtomicInteger PENDING = new AtomicInteger();

    private MapWorker() {}

    /** Поколение — для задач, которые должны пропасть при смене мира. */
    static int generation() {
        return GENERATION.get();
    }

    /** Новый мир: всё, что ещё вернётся от прошлого, выбрасывается. Записи на диск доделываются. */
    static void newGeneration() {
        GENERATION.incrementAndGet();
    }

    /** Задача в фоновом потоке. */
    static void submit(Runnable task) {
        PENDING.incrementAndGet();
        EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.error("Ошибка в фоновом потоке карты", e);
            } finally {
                PENDING.decrementAndGet();
            }
        });
    }

    /** Сколько задач ещё в очереди — чтобы не заваливать её запросами. */
    static int pending() {
        return PENDING.get();
    }

    /** Из фонового потока: выполнить в главном, если мир за это время не сменился. */
    static void toMain(int generation, Runnable task) {
        toMain(generation, task, null);
    }

    /** То же; если мир сменился — выполнить discard (освободить картинку и т. п.), в любом потоке. */
    static void toMain(int generation, Runnable task, Runnable discard) {
        if (generation != GENERATION.get()) {
            if (discard != null) {
                discard.run();
            }
            return;
        }
        MAIN.add(() -> {
            if (generation == GENERATION.get()) {
                task.run();
            } else if (discard != null) {
                discard.run();
            }
        });
    }

    /** Главный поток: забрать готовые результаты, пока не кончилось отведённое время. */
    static void runMainTasks(long budgetNanos) {
        long until = System.nanoTime() + budgetNanos;
        Runnable task;
        while ((task = MAIN.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.error("Ошибка при приёме данных карты", e);
            }
            if (System.nanoTime() > until) {
                return;
            }
        }
    }
}
