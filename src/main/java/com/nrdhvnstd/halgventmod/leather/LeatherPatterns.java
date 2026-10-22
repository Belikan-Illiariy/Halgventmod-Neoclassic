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
 * Выкройки шитейного стола: кожаные и из ткани в одной книге. Меняйте здесь.
 *
 * <p>leather(что выйдет, сколько, стежков в шитье, ниток, контур) — из кожи, кроится ножом;
 * cloth(…) — из ткани, кроится ножницами
 *     .leather(кожа или ткань, кусков) — уходит при раскрое; качество вещи — худшее из кусков
 *     .extra(фурнитура, штук) — уходит при шитье: железо, клей, ремешки, пакля
 * <p>Всё нужное кладут в ячейки стола. Стежков 0 — вещь готова сразу после раскроя (ремешки).
 * Нитки — «Нитки» мода или простая нить, берутся из инвентаря.
 */
public final class LeatherPatterns {
    public record Need(Supplier<? extends Item> item, int count) {
        public boolean matches(ItemStack stack) {
            return stack.is(item.get());
        }
    }

    /** Из чего вещь: кожу кроят ножом, ткань — ножницами. */
    public enum Material {
        LEATHER, CLOTH
    }

    public static final class Pattern {
        private final Supplier<? extends Item> result;
        private final int resultCount;
        private final int stitches;
        private final int thread;
        private final Material material;
        private final CutShape shape;
        private final List<Need> leather = new ArrayList<>();
        private final List<Need> extras = new ArrayList<>();

        private Pattern(Supplier<? extends Item> result, int resultCount, int stitches, int thread, Material material,
                        CutShape shape) {
            this.result = result;
            this.resultCount = resultCount;
            this.stitches = stitches;
            this.thread = thread;
            this.material = material;
            this.shape = shape;
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

        public Material material() {
            return material;
        }

        public CutShape shape() {
            return shape;
        }

        /** Что кроят (кожа, ткань). */
        public List<Need> leather() {
            return Collections.unmodifiableList(leather);
        }

        /** Что идёт в шитьё (фурнитура, набивка). */
        public List<Need> extras() {
            return Collections.unmodifiableList(extras);
        }
    }

    private static final List<Pattern> ALL = new ArrayList<>();

    static {
        leather(ModItems.LEATHER_STRAP, 2, 0, 0, CutShape.STRAP).leather(ModItems.LEATHER_SCRAPS, 2);
        leather(ModItems.LEATHER_STRAP, 4, 0, 0, CutShape.STRAP).leather(ModItems.TANNED_LEATHER, 1);
        leather(ModItems.LEATHER_BELT, 1, 6, 1, CutShape.BELT).leather(ModItems.TANNED_LEATHER, 2)
                .extra(() -> Items.IRON_NUGGET, 1);
        leather(ModItems.SMALL_POUCH, 1, 6, 1, CutShape.POUCH).leather(ModItems.TANNED_LEATHER, 1)
                .extra(ModItems.LEATHER_STRAP, 1);
        leather(ModItems.POUCH, 1, 8, 1, CutShape.POUCH).leather(ModItems.TANNED_LEATHER, 2).extra(ModItems.LEATHER_STRAP, 1);
        leather(ModItems.SHOULDER_BAG, 1, 10, 2, CutShape.BAG).leather(ModItems.TANNED_LEATHER, 3)
                .extra(ModItems.LEATHER_STRAP, 2);
        leather(ModItems.QUIVER, 1, 8, 1, CutShape.QUIVER).leather(ModItems.TANNED_LEATHER, 3).extra(ModItems.LEATHER_STRAP, 1);
        leather(ModItems.RAWHIDE_MITTENS, 1, 6, 1, CutShape.MITTEN).leather(ModItems.RAWHIDE, 2)
                .extra(ModItems.LEATHER_STRAP, 1);
        leather(ModItems.ARCHER_GLOVES, 1, 8, 1, CutShape.MITTEN).leather(ModItems.SOFT_LEATHER, 2);
        leather(ModItems.LEATHER_GAUNTLETS, 1, 10, 2, CutShape.MITTEN).leather(ModItems.BOILED_LEATHER, 2)
                .extra(ModItems.HIDE_GLUE, 1).extra(() -> Items.IRON_NUGGET, 4);
        leather(ModItems.LEATHER_BRIGANDINE, 1, 14, 3, CutShape.BODY).leather(ModItems.BOILED_LEATHER, 4)
                .extra(ModItems.HIDE_GLUE, 2).extra(() -> Items.IRON_INGOT, 2).extra(() -> Items.IRON_NUGGET, 8)
                .extra(ModItems.LEATHER_STRAP, 2);
        leather(ModItems.FUR_CLOAK, 1, 10, 2, CutShape.CLOAK).leather(ModItems.DRESSED_FUR, 3).extra(ModItems.LEATHER_STRAP, 1);

        // Из ткани: шерстяное сукно — плащи, капюшоны, варежки; льняное полотно — сюрко и поддоспех с паклей
        cloth(ModItems.HOOD, 1, 6, 1, CutShape.HOOD).leather(ModItems.WOOLEN_CLOTH, 1);
        cloth(ModItems.CHAPERON, 1, 8, 1, CutShape.HOOD).leather(ModItems.WOOLEN_CLOTH, 2);
        cloth(ModItems.WOOLEN_MITTENS, 1, 6, 1, CutShape.MITTEN).leather(ModItems.WOOLEN_CLOTH, 1);
        cloth(ModItems.CLOTH_GAUNTLETS, 1, 6, 1, CutShape.MITTEN).leather(ModItems.LINEN_CLOTH, 1);
        cloth(ModItems.PADDED_MITTENS, 1, 8, 1, CutShape.MITTEN).leather(ModItems.LINEN_CLOTH, 1).extra(ModItems.TOW, 2);
        cloth(ModItems.WOOLEN_CLOAK, 1, 8, 1, CutShape.CLOAK).leather(ModItems.WOOLEN_CLOTH, 3).extra(() -> Items.IRON_NUGGET, 1);
        cloth(ModItems.FINE_CLOAK, 1, 12, 2, CutShape.CLOAK).leather(ModItems.WOOLEN_CLOTH, 3).leather(ModItems.LINEN_CLOTH, 1)
                .extra(() -> Items.IRON_NUGGET, 1);
        cloth(ModItems.SIMPLE_SURCOAT, 1, 8, 1, CutShape.VEST).leather(ModItems.LINEN_CLOTH, 2);
        cloth(ModItems.SURCOAT, 1, 12, 2, CutShape.VEST).leather(ModItems.WOOLEN_CLOTH, 2).leather(ModItems.LINEN_CLOTH, 1);
        cloth(ModItems.SIMPLE_AKETON, 1, 14, 2, CutShape.BODY).leather(ModItems.LINEN_CLOTH, 3).extra(ModItems.TOW, 4);
        cloth(ModItems.QUILTED_AKETON, 1, 18, 3, CutShape.BODY).leather(ModItems.LINEN_CLOTH, 4).extra(ModItems.TOW, 6);
        cloth(ModItems.PADDED_JACKET, 1, 16, 3, CutShape.BODY).leather(ModItems.LINEN_CLOTH, 3).leather(ModItems.WOOLEN_CLOTH, 1)
                .extra(ModItems.TOW, 4);
        cloth(ModItems.GAMBESON, 1, 22, 4, CutShape.BODY).leather(ModItems.LINEN_CLOTH, 5).leather(ModItems.WOOLEN_CLOTH, 1)
                .extra(ModItems.TOW, 8);
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
            if (pattern.material == Material.LEATHER && pattern.stitches > 0 && stack.is(pattern.result.get())) {
                return true;
            }
        }
        return false;
    }

    private static Pattern leather(Supplier<? extends Item> result, int count, int stitches, int thread, CutShape shape) {
        Pattern pattern = new Pattern(result, count, stitches, thread, Material.LEATHER, shape);
        ALL.add(pattern);
        return pattern;
    }

    private static Pattern cloth(Supplier<? extends Item> result, int count, int stitches, int thread, CutShape shape) {
        Pattern pattern = new Pattern(result, count, stitches, thread, Material.CLOTH, shape);
        ALL.add(pattern);
        return pattern;
    }
}
