package com.nrdhvnstd.halgventmod.leather;

import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Работа на колоде (мини-игра ScrapeScreen). Числа самого скобления — вверху ScrapeScreen. */
public enum ScrapeJob {
    // Меняйте здесь ⚠️: износ инструмента, выносливость и наименьшее время работы (тиков) за всю шкуру
    FLESH("flesh", ModItems.FLESHING_KNIFE, 8, 15.0F, 60),
    HAIR("hair", ModItems.SCRAPER, 5, 12.0F, 60),
    PARCHMENT("parchment", ModItems.SCRAPER, 3, 10.0F, 60);

    private final String id;
    private final Supplier<? extends Item> tool;
    private final int wear;
    private final float stamina;
    private final int minTicks;

    ScrapeJob(String id, Supplier<? extends Item> tool, int wear, float stamina, int minTicks) {
        this.id = id;
        this.tool = tool;
        this.wear = wear;
        this.stamina = stamina;
        this.minTicks = minTicks;
    }

    public String id() {
        return id;
    }

    public boolean usesTool(ItemStack stack) {
        return stack.is(tool.get());
    }

    public int wear() {
        return wear;
    }

    public float stamina() {
        return stamina;
    }

    public int minTicks() {
        return minTicks;
    }

    public static ScrapeJob byIndex(int index) {
        ScrapeJob[] all = values();
        return all[Math.floorMod(index, all.length)];
    }
}
