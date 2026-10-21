package com.nrdhvnstd.halgventmod.brewing;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Правила алкоголя в одном месте. Меняйте здесь ⚠️
 *
 * <pre>
 * сусло (давильня, котелок)  →  бродильная бочка  →  пиво, сидр, вино, медовуха (или уксус, если передержать)
 * пиво, сидр, вино, медовуха →  перегонный куб    →  крепкое: хлебное вино, бренди, яблочный бренди, крепкий мёд
 * крепкое + ягоды/травы/мёд  →  бродильная бочка  →  настойка
 * вино, медовуха, сидр, крепкое, настойки → бочка для выдержки → качество растёт
 * </pre>
 */
public final class Brewing {
    // Брожение, суток
    public static final double BEER_DAYS = 2;
    public static final double CIDER_DAYS = 4;
    public static final double WINE_DAYS = 4;
    public static final double MEAD_DAYS = 6;
    public static final double TINCTURE_DAYS = 3;
    public static final double SOUR_AFTER_DAYS = 3;   // готовое в бочке столько суток — и уже уксус
    public static final double SUMMER_FACTOR = 0.75;  // летом бродит быстрее...
    public static final double WINTER_FACTOR = 1.5;   // ...зимой медленнее, но чище (+1 качество)
    // Перегонка: порций на одну крепкого
    public static final int DISTILL_RATIO = 4;
    // Выдержка: суток на ступень качества
    public static final int AGE_DAYS_CELLAR = 6;
    public static final int AGE_DAYS = 10;

    private Brewing() {}

    @Nullable
    public static Brew brew(ItemStack stack) {
        return stack.get(ModComponents.BREW.get());
    }

    public static ItemStack make(Item item, Brew brew) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.BREW.get(), brew);
        return stack;
    }

    /** Во что перебродит сусло этой основы. */
    public static Item fermentsInto(String kind) {
        if (kind.startsWith("wort")) {
            return ModItems.BEER.get();
        }
        return switch (kind) {
            case "apple" -> ModItems.CIDER.get();
            case "honey" -> ModItems.MEAD.get();
            default -> ModItems.WINE.get(); // berry, glowberry
        };
    }

    /** Сколько суток бродит (без поправки на сезон). */
    public static double ferment(ItemStack base) {
        if (base.is(ModItems.SPIRIT.get())) {
            return TINCTURE_DAYS;
        }
        Brew brew = brew(base);
        String kind = brew == null ? "berry" : brew.kind();
        if (kind.startsWith("wort")) {
            return BEER_DAYS;
        }
        return switch (kind) {
            case "apple" -> CIDER_DAYS;
            case "honey" -> MEAD_DAYS;
            default -> WINE_DAYS;
        };
    }

    public static double seasonFactor(Level level) {
        return switch (Calendar.date(Calendar.today(level)).season()) {
            case SUMMER -> SUMMER_FACTOR;
            case WINTER -> WINTER_FACTOR;
            default -> 1.0;
        };
    }

    public static boolean winter(Level level) {
        return Calendar.date(Calendar.today(level)).season() == Calendar.Season.WINTER;
    }

    /** Основа крепкого после перегонки: пиво — хлебное вино, вино — бренди, сидр — яблочный, медовуха — крепкий мёд. */
    @Nullable
    public static String distillsInto(ItemStack drink) {
        if (drink.is(ModItems.BEER.get())) {
            return "bread";
        }
        if (drink.is(ModItems.WINE.get())) {
            return "brandy";
        }
        if (drink.is(ModItems.CIDER.get())) {
            return "apple";
        }
        if (drink.is(ModItems.MEAD.get())) {
            return "mead";
        }
        return null;
    }

    /** Что выдерживают в бочке для выдержки (пиво — нет: его пьют свежим). */
    public static boolean ageable(ItemStack stack) {
        return stack.is(ModItems.WINE.get()) || stack.is(ModItems.MEAD.get()) || stack.is(ModItems.CIDER.get())
                || stack.is(ModItems.SPIRIT.get()) || stack.is(ModItems.TINCTURE.get());
    }

    /** Добавка в бродильную бочку и её примета: мёд — сладкое, травы — на травах, ягоды — с ягодами. */
    @Nullable
    public static String flavour(ItemStack stack) {
        if (stack.is(ModItems.HONEY.get())) {
            return "honey";
        }
        if (stack.is(ModItems.HERBS.get()) || stack.is(ModItems.DRIED_HERBS.get())) {
            return "herbs";
        }
        if (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES)) {
            return "berry";
        }
        return null;
    }

    /** Крепость напитка: сколько прибавляет к опьянению (шкала 0–100). */
    public static float strength(ItemStack stack) {
        if (stack.is(ModItems.BEER.get())) {
            return 8;
        }
        if (stack.is(ModItems.CIDER.get())) {
            return 10;
        }
        if (stack.is(ModItems.WINE.get()) || stack.is(ModItems.MEAD.get())) {
            return 14;
        }
        if (stack.is(ModItems.TINCTURE.get())) {
            return 24;
        }
        if (stack.is(ModItems.SPIRIT.get())) {
            return 30;
        }
        return 0;
    }
}
