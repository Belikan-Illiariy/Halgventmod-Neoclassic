package com.nrdhvnstd.halgventmod.carpentry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Изделия верстака плотника. Меняйте здесь ⚠️
 *
 * <p>product(что выйдет, сколько, сучков на заготовке)
 *     .planks(n)                      — досок любого дерева
 *     .need(предмет, штук)            — что ещё (железо, сыромять)
 *     .step(операция)                 — этап: строгание
 *     .step(операция, форма)          — этап с разметкой: гнездо под долото, профиль под нож
 *     .step(операция, сколько)        — сверлить столько дыр, забить столько гвоздей
 * <p>Всё нужное кладут в ячейки верстака; инструменты этапов — из инвентаря. Этапы идут по порядку
 * (BenchWorkScreen), у каждого своя мини-игра; качество изделия — от среднего счёта этапов ({@link Carpentry#workDelta}).
 * Сучки — где рубанок и нож рвут волокна, если вести их быстро.
 */
public final class BenchProducts {
    /** Что нужно: проверка и что показать в окне. */
    public record Need(Predicate<ItemStack> test, Supplier<ItemStack> icon, int count) {
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && test.test(stack);
        }
    }

    /** Этап изделия: операция, разметка и сколько (дыр, гвоздей). */
    public record Step(Operation operation, WorkShape shape, int amount) {}

    /** Тема изделия — вкладка в окне верстака. */
    public enum Group {
        WEAVING("weaving"), HOUSEHOLD("household"), TOOLS("tools"), PARTS("parts");

        private final String id;

        Group(String id) {
            this.id = id;
        }

        public String langKey() {
            return "gui.halgventmod.carpenter_bench.group." + id;
        }
    }

    public static final class Product {
        private final Supplier<? extends Item> result;
        private final int count;
        private final int knots;
        private final List<Need> needs = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private boolean plain;
        private final Group group = currentGroup;

        private Product(Supplier<? extends Item> result, int count, int knots) {
            this.result = result;
            this.count = count;
            this.knots = knots;
        }

        private Product planks(int count) {
            needs.add(new Need(stack -> stack.is(ItemTags.PLANKS), () -> new ItemStack(ModItems.BOARDS.get()), count)); // доски мода в теге досок
            return this;
        }

        private Product need(Supplier<? extends Item> item, int count) {
            needs.add(new Need(stack -> stack.is(item.get()), () -> new ItemStack(item.get()), count));
            return this;
        }

        private Product step(Operation operation) {
            steps.add(new Step(operation, WorkShape.NONE, 1));
            return this;
        }

        private Product step(Operation operation, WorkShape shape) {
            steps.add(new Step(operation, shape, 1));
            return this;
        }

        private Product step(Operation operation, int amount) {
            steps.add(new Step(operation, WorkShape.NONE, amount));
            return this;
        }

        /** Без качества: расходная мелочь, что складывается в стопки (миски). */
        private Product plain() {
            plain = true;
            return this;
        }

        public ItemStack result() {
            return new ItemStack(result.get(), count);
        }

        /** Выходит ли изделие с качеством дерева. */
        public boolean hasQuality() {
            return !plain;
        }

        public Group group() {
            return group;
        }

        public int knots() {
            return knots;
        }

        public List<Need> needs() {
            return Collections.unmodifiableList(needs);
        }

        public List<Step> steps() {
            return Collections.unmodifiableList(steps);
        }

        /** Разные операции изделия, по порядку первого появления — какие инструменты нужны. */
        public List<Operation> operations() {
            List<Operation> list = new ArrayList<>();
            for (Step step : steps) {
                if (!list.contains(step.operation())) {
                    list.add(step.operation());
                }
            }
            return list;
        }

        /** Сколько тиков работы сервер ждёт самое меньшее. */
        public int minTicks() {
            int ticks = 0;
            for (Step step : steps) {
                ticks += step.operation().minTicks();
            }
            return ticks;
        }

        /** Досок на изделие: чем больше, тем длиннее заготовка под рубанок. */
        public int planks() {
            return needs.isEmpty() ? 1 : needs.get(0).count();
        }
    }

    private static final List<Product> ALL = new ArrayList<>();
    private static Group currentGroup = Group.HOUSEHOLD; // тема, в которую ложатся изделия ниже

    static {
        // Ткачество
        currentGroup = Group.WEAVING;
        product(ModItems.SHUTTLE, 1, 1).planks(1)
                .step(Operation.WHITTLE, WorkShape.SHUTTLE).step(Operation.CHISEL, WorkShape.CAVITY);
        product(ModItems.SPINDLE, 1, 0).planks(1)
                .step(Operation.WHITTLE, WorkShape.SPINDLE);
        product(ModItems.CARDING_COMB, 1, 1).planks(1).need(SmithingItems.NAILS, 4)
                .step(Operation.PLANE).step(Operation.DRILL, 4).step(Operation.NAIL, 4);
        // Кухня, кожевник, гончар
        currentGroup = Group.HOUSEHOLD;
        product(ModItems.LADLE, 1, 1).planks(1)
                .step(Operation.CHISEL, WorkShape.BOWL).step(Operation.WHITTLE, WorkShape.LADLE_HANDLE);
        product(() -> Items.BOWL, 2, 0).planks(1).plain()
                .step(Operation.CHISEL, WorkShape.DISH).step(Operation.WHITTLE, WorkShape.BOWL_SIDE);
        product(SmithingItems.GOLD_PAN, 1, 1).planks(2)
                .step(Operation.PLANE).step(Operation.CHISEL, WorkShape.DISH);
        product(ModItems.LEATHER_MALLET, 1, 1).planks(1).need(ModItems.RAWHIDE, 1)
                .step(Operation.WHITTLE, WorkShape.HANDLE).step(Operation.DRILL, 1).step(Operation.NAIL, 1);
        product(ModItems.BRICK_MOULD, 1, 2).planks(4).need(SmithingItems.NAILS, 4)
                .step(Operation.PLANE).step(Operation.NAIL, 4);
        // Инструмент плотника
        currentGroup = Group.TOOLS;
        product(ModItems.SAW, 1, 1).planks(1).need(SmithingItems.IRON_INGOT, 2)
                .step(Operation.WHITTLE, WorkShape.HANDLE).step(Operation.DRILL, 2).step(Operation.NAIL, 2);
        product(ModItems.PLANE, 1, 2).planks(2).need(SmithingItems.IRON_INGOT, 1)
                .step(Operation.PLANE).step(Operation.CHISEL, WorkShape.MOUTH).step(Operation.NAIL, 1);
        product(ModItems.CHISEL, 1, 0).planks(1).need(SmithingItems.IRON_INGOT, 1)
                .step(Operation.WHITTLE, WorkShape.HANDLE).step(Operation.NAIL, 1);
        product(ModItems.BRACE, 1, 1).planks(2).need(SmithingItems.IRON_INGOT, 1)
                .step(Operation.WHITTLE, WorkShape.HANDLE).step(Operation.DRILL, 1).step(Operation.NAIL, 1);
        product(ModItems.HAMMER, 1, 0).planks(1).need(SmithingItems.IRON_INGOT, 1)
                .step(Operation.WHITTLE, WorkShape.HANDLE).step(Operation.NAIL, 1);
        // Задел для кузни: деревянные части, на которые кузнец насадит железо (качество дерева пойдёт в вещь)
        currentGroup = Group.PARTS;
        product(ModItems.TOOL_HANDLE, 2, 0).planks(1)
                .step(Operation.WHITTLE, WorkShape.HANDLE);
        product(ModItems.AXE_HANDLE, 1, 1).planks(1)
                .step(Operation.PLANE).step(Operation.WHITTLE, WorkShape.AXE_HANDLE);
        product(ModItems.HAFT, 1, 1).planks(2)
                .step(Operation.PLANE).step(Operation.WHITTLE, WorkShape.HAFT);
        product(ModItems.SPEAR_SHAFT, 1, 2).planks(2)
                .step(Operation.PLANE).step(Operation.WHITTLE, WorkShape.POLE).step(Operation.DRILL, 1);
        product(ModItems.SWORD_GRIP, 1, 0).planks(1)
                .step(Operation.WHITTLE, WorkShape.GRIP).step(Operation.CHISEL, WorkShape.TANG);
    }

    private BenchProducts() {}

    public static List<Product> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Product get(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    private static Product product(Supplier<? extends Item> result, int count, int knots) {
        Product product = new Product(result, count, knots);
        ALL.add(product);
        return product;
    }
}
