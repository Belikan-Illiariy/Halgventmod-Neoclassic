package com.nrdhvnstd.halgventmod.leather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Выкройки стола кожевника. Меняйте здесь.
 *
 * <p>pattern(что выйдет, сколько, стежков в шитье, ниток)
 *     .leather(кожа, кусков) — кроится ножом; качество вещи — худшее из кусков
 *     .extra(фурнитура, штук) — кладётся после раскроя: железо, клей, ремешки
 * <p>Стежков 0 — вещь готова сразу после раскроя (ремешки). Нитки — «Нитки» мода или простая нить, берутся из инвентаря.
 */
public final class LeatherPatterns {
    public record Need(Supplier<? extends Item> item, int count) {
        public boolean matches(ItemStack stack) {
            return stack.is(item.get());
        }
    }

    public static final class Pattern {
        private final Supplier<? extends Item> result;
        private final int resultCount;
        private final int stitches;
        private final int thread;
        private final List<Need> leather = new ArrayList<>();
        private final List<Need> extras = new ArrayList<>();

        private Pattern(Supplier<? extends Item> result, int resultCount, int stitches, int thread) {
            this.result = result;
            this.resultCount = resultCount;
            this.stitches = stitches;
            this.thread = thread;
        }

        private Pattern leather(Supplier<? extends Item> item, int count) {
            leather.add(new Need(item, count));
            return this;
        }

        private Pattern extra(Supplier<? extends Item> item, int count) {
            extras.add(new Need(item, count));
            return this;
        }

        public ItemStack result() {
            return new ItemStack(result.get(), resultCount);
        }

        public int stitches() {
            return stitches;
        }

        public int thread() {
            return thread;
        }

        public List<Need> leather() {
            return Collections.unmodifiableList(leather);
        }

        public List<Need> extras() {
            return Collections.unmodifiableList(extras);
        }
    }

    private static final List<Pattern> ALL = new ArrayList<>();

    static {
        pattern(ModItems.LEATHER_STRAP, 2, 0, 0).leather(ModItems.LEATHER_SCRAPS, 2);
        pattern(ModItems.LEATHER_STRAP, 4, 0, 0).leather(ModItems.TANNED_LEATHER, 1);
        pattern(ModItems.LEATHER_BELT, 1, 6, 1).leather(ModItems.TANNED_LEATHER, 2).extra(() -> Items.IRON_NUGGET, 1);
        pattern(ModItems.SMALL_POUCH, 1, 6, 1).leather(ModItems.TANNED_LEATHER, 1).extra(ModItems.LEATHER_STRAP, 1);
        pattern(ModItems.POUCH, 1, 8, 1).leather(ModItems.TANNED_LEATHER, 2).extra(ModItems.LEATHER_STRAP, 1);
        pattern(ModItems.SHOULDER_BAG, 1, 10, 2).leather(ModItems.TANNED_LEATHER, 3).extra(ModItems.LEATHER_STRAP, 2);
        pattern(ModItems.QUIVER, 1, 8, 1).leather(ModItems.TANNED_LEATHER, 3).extra(ModItems.LEATHER_STRAP, 1);
        pattern(ModItems.RAWHIDE_MITTENS, 1, 6, 1).leather(ModItems.RAWHIDE, 2).extra(ModItems.LEATHER_STRAP, 1);
        pattern(ModItems.ARCHER_GLOVES, 1, 8, 1).leather(ModItems.SOFT_LEATHER, 2);
        pattern(ModItems.LEATHER_GAUNTLETS, 1, 10, 2).leather(ModItems.BOILED_LEATHER, 2)
                .extra(ModItems.HIDE_GLUE, 1).extra(() -> Items.IRON_NUGGET, 4);
        pattern(ModItems.LEATHER_BRIGANDINE, 1, 14, 3).leather(ModItems.BOILED_LEATHER, 4)
                .extra(ModItems.HIDE_GLUE, 2).extra(() -> Items.IRON_INGOT, 2).extra(() -> Items.IRON_NUGGET, 8)
                .extra(ModItems.LEATHER_STRAP, 2);
        pattern(ModItems.FUR_CLOAK, 1, 10, 2).leather(ModItems.DRESSED_FUR, 3).extra(ModItems.LEATHER_STRAP, 1);
    }

    private LeatherPatterns() {}

    public static List<Pattern> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Pattern get(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    /** Кожаные вещи со стола: их смазывают, они пересыхают. */
    public static boolean isProduct(ItemStack stack) {
        for (Pattern pattern : ALL) {
            if (pattern.stitches > 0 && stack.is(pattern.result.get())) {
                return true;
            }
        }
        return false;
    }

    private static Pattern pattern(Supplier<? extends Item> result, int count, int stitches, int thread) {
        Pattern pattern = new Pattern(result, count, stitches, thread);
        ALL.add(pattern);
        return pattern;
    }
}
