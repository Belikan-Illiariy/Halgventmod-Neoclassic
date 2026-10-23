package com.nrdhvnstd.halgventmod.smithing;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Металлы тигля. В тигле всё хранится долями чистых металлов (медь, олово, галмей-цинк, свинец, серебро, золото):
 * слиток — {@link Crucible#PER_INGOT} долей, бронзовый слиток раскладывается на 9 меди и 1 олово и т. д. Что выйдет
 * из расплава — решает состав ({@link Crucible#resolve}). Температуры — для мини-игры плавки (°C). Меняйте здесь ⚠️
 */
public enum Metal {
    //        id         слиток                          плавится  лить от  до     цвет
    COPPER  ("copper",   SmithingItems.COPPER_INGOT,     1085,     1120,    1200,  0xFFC0703E),
    TIN     ("tin",      SmithingItems.TIN_INGOT,        232,      270,     340,   0xFFC8CCD2),
    ZINC    ("zinc",     null,                           420,      0,       0,     0xFFB4BCC4),
    LEAD    ("lead",     SmithingItems.LEAD_INGOT,       327,      370,     450,   0xFF6E747E),
    SILVER  ("silver",   SmithingItems.SILVER_INGOT,     962,      1000,    1080,  0xFFDCE0E6),
    GOLD    ("gold",     SmithingItems.GOLD_INGOT,       1064,     1100,    1180,  0xFFEAC24A),
    BRONZE  ("bronze",   SmithingItems.BRONZE_INGOT,     950,      1040,    1120,  0xFFB88C46),
    BRASS   ("brass",    SmithingItems.BRASS_INGOT,      920,      960,     1030,  0xFFD6B656),
    PEWTER  ("pewter",   SmithingItems.PEWTER_INGOT,     245,      290,     360,   0xFFA8AEB6),
    // Свинец с серебром из блеска: не сплавляют, льют в купель (CupelBlockEntity)
    SILVER_LEAD("silver_lead", SmithingItems.ARGENTIFEROUS_LEAD, 327, 370,  450,   0xFF5E6470);

    /** Выше этого латунь теряет цинк — он выгорает белым дымом. */
    public static final int ZINC_FUMES = 1040;

    private final String id;
    @Nullable
    private final Supplier<? extends Item> ingot;
    private final int melt;
    private final int pourLow;
    private final int pourHigh;
    private final int color;

    Metal(String id, @Nullable Supplier<? extends Item> ingot, int melt, int pourLow, int pourHigh, int color) {
        this.id = id;
        this.ingot = ingot;
        this.melt = melt;
        this.pourLow = pourLow;
        this.pourHigh = pourHigh;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String langKey() {
        return "metal.halgventmod." + id;
    }

    /** Льётся ли сам по себе (у галмея-цинка слитка нет). */
    public boolean castable() {
        return ingot != null;
    }

    public ItemStack ingot() {
        return ingot == null ? ItemStack.EMPTY : new ItemStack(ingot.get());
    }

    public int melt() {
        return melt;
    }

    public int pourLow() {
        return pourLow;
    }

    public int pourHigh() {
        return pourHigh;
    }

    public int color() {
        return color;
    }

    @Nullable
    public static Metal byId(String id) {
        for (Metal metal : values()) {
            if (metal.id.equals(id)) {
                return metal;
            }
        }
        return null;
    }

    /**
     * Сколько каких чистых металлов (в долях) даёт в тигель одна штука; null — это не шихта. Сплавы раскладываются
     * по своему составу. Железо и сталь — тоже null: в тигле на горне их не расплавить ({@link #iron}).
     */
    @Nullable
    public static Map<Metal, Integer> charge(ItemStack stack) {
        Map<Metal, Integer> parts = new EnumMap<>(Metal.class);
        if (stack.is(SmithingItems.GOLD_DUST.get())) {
            parts.put(GOLD, 2);
        } else if (stack.is(SmithingItems.ZINC_ORE.get())) {
            parts.put(ZINC, 5);
        } else if (stack.is(SmithingItems.BRONZE_INGOT.get())) {
            parts.put(COPPER, 9);
            parts.put(TIN, 1);
        } else if (stack.is(SmithingItems.BRASS_INGOT.get())) {
            parts.put(COPPER, 7);
            parts.put(ZINC, 3);
        } else if (stack.is(SmithingItems.PEWTER_INGOT.get())) {
            parts.put(TIN, 8);
            parts.put(LEAD, 2);
        } else if (stack.is(SmithingItems.ARGENTIFEROUS_LEAD.get())) {
            parts.put(SILVER_LEAD, Crucible.PER_INGOT);
        } else {
            for (Metal metal : new Metal[] {COPPER, TIN, LEAD, SILVER, GOLD}) {
                if (stack.is(metal.ingot.get())) {
                    parts.put(metal, Crucible.PER_INGOT);
                }
            }
        }
        return parts.isEmpty() ? null : parts;
    }

    /** Железо и сталь: их куют, а не льют. */
    public static boolean iron(ItemStack stack) {
        return stack.is(SmithingItems.IRON_INGOT.get()) || stack.is(SmithingItems.STEEL_INGOT.get())
                || stack.is(SmithingItems.IRON_BLOOM.get());
    }
}
