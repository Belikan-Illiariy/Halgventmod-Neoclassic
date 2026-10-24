package com.nrdhvnstd.halgventmod.smithing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Изделия наковальни ({@link #anvil()}) и литейного стола ({@link #casting()}). Меняйте здесь ⚠️
 *
 * <p>product(вкладка, что выйдет, сколько)        — или product(вкладка, "conquest_armory:longsword") для оружия
 *     .iron(n) .steel(n) .copper(n) …           — металл: его качество идёт в вещь
 *     .wood(деталь, n)                          — деталь с верстака плотника: её качество — четверть итога
 *     .need(предмет, n)                         — прочее (ремешки, заклёпки, ткань)
 *     .step(операция[, разметка | сколько])      — этап: вытяжка по профилю, пробить 4 дыры…
 *     .plain() / .gear()                        — без качества (гвозди, монета) / качество снаряжения (оружие, броня)
 * <p>Всё нужное кладут в ячейки станции; инструменты этапов — из инвентаря, горн, бочка и точило — рядом
 * ({@link ForgeOp}). Качество: металл (и дерево) + поправка за средний счёт этапов ({@link Smithing#workDelta}).
 * Оружие conquest_armory с качеством Отменное и выше выходит в «изысканном» виде, если он есть.
 */
public final class SmithProducts {
    /** Вкладки окна. */
    public enum Tab {
        METAL("metal"), TOOLS("tools"), WEAPONS("weapons"), ARMOR("armor"), ALLOYS("alloys"), CASTINGS("castings");

        private final String id;

        Tab(String id) {
            this.id = id;
        }

        public String langKey() {
            return "gui.halgventmod.workshop.tab." + id;
        }
    }

    /** Как выходит вещь. INGOTS — слитки из тигля: сколько целых слитков расплава, столько и выйдет. */
    public enum Result { PLAIN, QUALITY, GEAR, REPAIR, INGOTS }

    /** Роль нужного: металл, расплав и дерево дают качество. */
    public enum Role { METAL, WOOD, OTHER, REPAIRED, MELT }

    /** Нужное. MELT — расплав металла metal в жидком тигле при себе (count — слитков), в ячейки его не кладут. */
    public record Need(Predicate<ItemStack> test, Supplier<ItemStack> icon, int count, Role role, @javax.annotation.Nullable Metal metal) {
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && test.test(stack);
        }
    }

    public record Step(ForgeOp op, ForgeShape shape, int amount) {}

    public static final class Product {
        private final Tab tab;
        private final Supplier<Item> result;
        private final int count;
        private final List<Need> needs = new ArrayList<>();
        private final List<Step> steps = new ArrayList<>();
        private Result kind = Result.QUALITY;
        private ResourceLocation exquisite;
        private Supplier<ItemStack> icon;

        private Product(Tab tab, Supplier<Item> result, int count) {
            this.tab = tab;
            this.result = result;
            this.count = count;
        }

        private Product metal(Supplier<? extends Item> item, int count) {
            needs.add(new Need(stack -> stack.is(item.get()), () -> new ItemStack(item.get()), count, Role.METAL, null));
            return this;
        }

        private Product iron(int count) {
            return metal(SmithingItems.IRON_INGOT, count);
        }

        private Product steel(int count) {
            return metal(SmithingItems.STEEL_INGOT, count);
        }

        private Product copper(int count) {
            return metal(SmithingItems.COPPER_INGOT, count);
        }

        private Product silver(int count) {
            return metal(SmithingItems.SILVER_INGOT, count);
        }

        private Product gold(int count) {
            return metal(SmithingItems.GOLD_INGOT, count);
        }

        private Product wood(Supplier<? extends Item> item, int count) {
            needs.add(new Need(stack -> stack.is(item.get()), () -> new ItemStack(item.get()), count, Role.WOOD, null));
            return this;
        }

        private Product need(Supplier<? extends Item> item, int count) {
            needs.add(new Need(stack -> stack.is(item.get()), () -> new ItemStack(item.get()), count, Role.OTHER, null));
            return this;
        }

        /** Расплав в жидком тигле при себе: count слитков металла (значок — слиток с подписью «Расплав: …»). */
        private Product melt(Metal metal, int count) {
            needs.add(new Need(stack -> false, () -> meltIcon(metal), count, Role.MELT, metal));
            return this;
        }

        /** Слитки из тигля: сколько целых слитков расплава — столько и выйдет. */
        private Product ingots() {
            kind = Result.INGOTS;
            return this;
        }

        /** Позолота: золото и ртуть, и этап золочения. */
        private Product gilded() {
            need(SmithingItems.GOLD_INGOT, 1).need(SmithingItems.MERCURY, 1);
            return step(ForgeOp.GILD);
        }

        private Product repairs(Predicate<ItemStack> what, Supplier<ItemStack> icon) {
            needs.add(0, new Need(what, icon, 1, Role.REPAIRED, null));
            kind = Result.REPAIR;
            this.icon = icon;
            return this;
        }

        private Product step(ForgeOp op) {
            steps.add(new Step(op, ForgeShape.NONE, 1));
            return this;
        }

        private Product step(ForgeOp op, ForgeShape shape) {
            steps.add(new Step(op, shape, 1));
            return this;
        }

        private Product step(ForgeOp op, int amount) {
            steps.add(new Step(op, ForgeShape.NONE, amount));
            return this;
        }

        private Product plain() {
            kind = Result.PLAIN;
            return this;
        }

        private Product gear() {
            kind = Result.GEAR;
            return this;
        }

        public Tab tab() {
            return tab;
        }

        public Result kind() {
            return kind;
        }

        /** Есть ли такая вещь в игре (оружие conquest_armory — только с модом). */
        public boolean available() {
            return result.get() != Items.AIR;
        }

        public ItemStack result() {
            if (icon != null) {
                return icon.get();
            }
            Item item = result.get();
            return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item, count);
        }

        /** Вещь с качеством снаряжения: Отменное и Шедевр — «изысканный» вид, если он есть. */
        public ItemStack result(GearQuality.Grade grade) {
            if (exquisite != null && grade.ordinal() >= GearQuality.Grade.EXCELLENT.ordinal()
                    && BuiltInRegistries.ITEM.containsKey(exquisite)) {
                return new ItemStack(BuiltInRegistries.ITEM.get(exquisite), count);
            }
            return result();
        }

        public List<Need> needs() {
            return Collections.unmodifiableList(needs);
        }

        public List<Step> steps() {
            return Collections.unmodifiableList(steps);
        }

        /** Расплав, который нужен (литейный стол); null — изделие не из тигля. */
        @javax.annotation.Nullable
        public Need melt() {
            for (Need need : needs) {
                if (need.role() == Role.MELT) {
                    return need;
                }
            }
            return null;
        }

        public boolean has(ForgeOp op) {
            for (Step step : steps) {
                if (step.op() == op) {
                    return true;
                }
            }
            return false;
        }

        /** Разные операции, по порядку первого появления. */
        public List<ForgeOp> operations() {
            List<ForgeOp> list = new ArrayList<>();
            for (Step step : steps) {
                if (!list.contains(step.op())) {
                    list.add(step.op());
                }
            }
            return list;
        }

        public int minTicks() {
            int ticks = 0;
            for (Step step : steps) {
                ticks += step.op().minTicks();
            }
            return ticks;
        }

        /** Тяжёлая работа: молотобоец помогает (вытяжка, обжим, выколотка, осадка). */
        public boolean heavy() {
            int metal = 0;
            for (Need need : needs) {
                if (need.role() == Role.METAL) {
                    metal += need.count();
                }
            }
            return metal >= 3;
        }
    }

    private static final List<Product> ANVIL = new ArrayList<>();
    private static final List<Product> CASTING = new ArrayList<>();

    static {
        // ================================================================ наковальня: металл
        anvil(Tab.METAL, SmithingItems.IRON_INGOT, 2).metal(SmithingItems.IRON_BLOOM, 1)
                .step(ForgeOp.CONSOLIDATE).step(ForgeOp.DRAW, ForgeShape.BAR);
        anvil(Tab.METAL, SmithingItems.NAILS, 12).iron(1).plain()
                .step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.CUT, 3);
        anvil(Tab.METAL, SmithingItems.RIVETS, 16).iron(1).plain()
                .step(ForgeOp.DRAW, ForgeShape.ROD).step(ForgeOp.CUT, 3).step(ForgeOp.UPSET);
        anvil(Tab.METAL, SmithingItems.IRON_WIRE, 4).iron(1).plain()
                .step(ForgeOp.DRAW, ForgeShape.ROD).step(ForgeOp.WIRE, 4);
        anvil(Tab.METAL, SmithingItems.CHAIN, 1).iron(2).plain()
                .step(ForgeOp.DRAW, ForgeShape.ROD).step(ForgeOp.BEND, ForgeShape.RING).step(ForgeOp.WELD);
        anvil(Tab.METAL, SmithingItems.GOLD_THREAD, 8).gold(1).plain()
                .step(ForgeOp.WIRE, 6);
        anvil(Tab.METAL, ModItems.COPPER_RING, 2).copper(1).plain()
                .step(ForgeOp.DRAW, ForgeShape.RING_STOCK).step(ForgeOp.BEND, ForgeShape.RING).step(ForgeOp.GRIND);
        anvil(Tab.METAL, ModItems.COPPER_PENDANT, 2).copper(1).plain()
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.PUNCH, 1).step(ForgeOp.GRIND);
        anvil(Tab.METAL, ModItems.COPPER_BRACELET, 1).copper(1).plain()
                .step(ForgeOp.DRAW, ForgeShape.ROD).step(ForgeOp.BEND, ForgeShape.RING).step(ForgeOp.GRIND);
        anvil(Tab.METAL, SmithingItems.SILVER_RING, 4).silver(1).plain()
                .step(ForgeOp.DRAW, ForgeShape.RING_STOCK).step(ForgeOp.BEND, ForgeShape.RING).step(ForgeOp.GRIND);
        anvil(Tab.METAL, SmithingItems.GOLD_RING, 4).gold(1).plain()
                .step(ForgeOp.DRAW, ForgeShape.RING_STOCK).step(ForgeOp.BEND, ForgeShape.RING).step(ForgeOp.GRIND);
        anvil(Tab.METAL, ModItems.COIN_TALER, 10).silver(1).plain().step(ForgeOp.CUT, 4).step(ForgeOp.MINT, 10);
        anvil(Tab.METAL, ModItems.COIN_MARKA, 10).silver(1).plain().step(ForgeOp.CUT, 4).step(ForgeOp.MINT, 10);
        anvil(Tab.METAL, ModItems.COIN_GULDEN, 5).gold(1).plain().step(ForgeOp.CUT, 3).step(ForgeOp.MINT, 5);
        anvil(Tab.METAL, ModItems.COIN_CRONA, 5).gold(1).plain().step(ForgeOp.CUT, 3).step(ForgeOp.MINT, 5);
        // Починка: та же вещь, полностью, но каждый раз чуть меньше прочности
        anvil(Tab.METAL, SmithingItems.SMITH_HAMMER, 1).repairs(SmithProducts::reforgeable, SmithProducts::reforgeIcon).iron(1)
                .step(ForgeOp.DRAW, ForgeShape.BAR).step(ForgeOp.QUENCH).step(ForgeOp.GRIND);

        // ================================================================ наковальня: инструменты
        anvil(Tab.TOOLS, SmithingItems.TONGS, 1).iron(2)
                .step(ForgeOp.DRAW, ForgeShape.ROD).step(ForgeOp.PUNCH, 1).step(ForgeOp.RIVET, 1);
        anvil(Tab.TOOLS, SmithingItems.SMITH_HAMMER, 1).iron(2).wood(ModItems.TOOL_HANDLE, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH).step(ForgeOp.RIVET, 1);
        anvil(Tab.TOOLS, SmithingItems.SLEDGEHAMMER, 1).iron(3).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH).step(ForgeOp.RIVET, 1);
        anvil(Tab.TOOLS, SmithingItems.DRAW_PLATE, 1).steel(1)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.PUNCH, 5).step(ForgeOp.QUENCH);
        // Стеклодувная трубка: железный пруток, свёрнутый и сваренный в трубку
        anvil(Tab.TOOLS, com.nrdhvnstd.halgventmod.glass.GlassItems.BLOWPIPE, 1).iron(2)
                .step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.WELD);
        // Клеймо: стальной пруток, знак мастера выбивают пробойником (Hallmark.bind — при выдаче)
        anvil(Tab.TOOLS, SmithingItems.SMITH_STAMP, 1).steel(1)
                .step(ForgeOp.DRAW, ForgeShape.ROD).step(ForgeOp.PUNCH, 3).step(ForgeOp.QUENCH);
        anvil(Tab.TOOLS, ModItems.KNIFE, 1).iron(1).steel(1).wood(ModItems.TOOL_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.KNIFE).step(ForgeOp.WELD).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 1)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        anvil(Tab.TOOLS, ModItems.SICKLE, 1).iron(1).steel(1).wood(ModItems.TOOL_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.CURVED).step(ForgeOp.BEND, ForgeShape.SICKLE).step(ForgeOp.QUENCH)
                .step(ForgeOp.TEMPER, 2).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        anvil(Tab.TOOLS, ModItems.SCYTHE, 1).iron(2).steel(1).wood(ModItems.HAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.CURVED).step(ForgeOp.WELD).step(ForgeOp.BEND, ForgeShape.SICKLE)
                .step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        anvil(Tab.TOOLS, () -> Items.IRON_AXE, 1).gear().iron(2).steel(1).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.AXE).step(ForgeOp.BEND, ForgeShape.EYE).step(ForgeOp.WELD)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND);
        anvil(Tab.TOOLS, () -> Items.IRON_PICKAXE, 1).iron(2).steel(1).wood(ModItems.HAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.TOOL_HEAD).step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH).step(ForgeOp.GRIND);
        anvil(Tab.TOOLS, () -> Items.IRON_SHOVEL, 1).iron(2).wood(ModItems.HAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.RIVET, 2);
        anvil(Tab.TOOLS, () -> Items.IRON_HOE, 1).iron(1).wood(ModItems.HAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.PUNCH, 1).step(ForgeOp.RIVET, 1);
        anvil(Tab.TOOLS, () -> Items.SHEARS, 1).iron(1).steel(1)
                .step(ForgeOp.DRAW, ForgeShape.KNIFE).step(ForgeOp.BEND, ForgeShape.RING).step(ForgeOp.QUENCH)
                .step(ForgeOp.TEMPER, 3).step(ForgeOp.GRIND);
        anvil(Tab.TOOLS, ModItems.NEEDLE, 1).need(SmithingItems.IRON_WIRE, 1)
                .step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.PUNCH, 1).step(ForgeOp.GRIND);
        anvil(Tab.TOOLS, ModItems.FLESHING_KNIFE, 1).iron(1).steel(1).wood(ModItems.TOOL_HANDLE, 2)
                .step(ForgeOp.DRAW, ForgeShape.CURVED).step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        anvil(Tab.TOOLS, ModItems.SCRAPER, 1).iron(1).wood(ModItems.TOOL_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.BEND, ForgeShape.HOOK).step(ForgeOp.GRIND)
                .step(ForgeOp.RIVET, 1);

        // ================================================================ наковальня: оружие (conquest_armory)
        // Клинки
        weapon("quillon_dagger").steel(1).iron(1).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.SHORT_BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("hunting_knife").steel(1).wood(ModItems.TOOL_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.KNIFE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2).step(ForgeOp.GRIND)
                .step(ForgeOp.RIVET, 2);
        weapon("shortsword").steel(2).iron(1).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.SHORT_BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("shortsword_with_gilded_hilt").steel(2).iron(1).wood(ModItems.SWORD_GRIP, 1)
                .need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.SHORT_BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1).gilded();
        weapon("viking_sword").steel(2).iron(2).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.WELD).step(ForgeOp.DRAW, ForgeShape.BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("crusader_sword").steel(3).iron(1).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("scimitar").steel(3).iron(1).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.CURVED).step(ForgeOp.BEND, ForgeShape.SABRE).step(ForgeOp.QUENCH)
                .step(ForgeOp.TEMPER, 2).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("longsword").steel(3).iron(1).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.LONG_BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("bastard_sword").steel(4).iron(1).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.LONG_BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("large_viking_sword").steel(3).iron(2).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.WELD).step(ForgeOp.DRAW, ForgeShape.LONG_BLADE).step(ForgeOp.QUENCH)
                .step(ForgeOp.TEMPER, 2).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("leafblade_claymore").steel(4).iron(1).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.LEAF_BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("flammenschwert").steel(5).iron(1).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 2)
                .step(ForgeOp.DRAW, ForgeShape.LONG_BLADE).step(ForgeOp.BEND, ForgeShape.WAVE).step(ForgeOp.QUENCH)
                .step(ForgeOp.TEMPER, 2).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("zweihander").steel(5).iron(2).wood(ModItems.SWORD_GRIP, 1).need(ModItems.LEATHER_STRAP, 2)
                .step(ForgeOp.DRAW, ForgeShape.LONG_BLADE).step(ForgeOp.QUENCH).step(ForgeOp.TEMPER, 2)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        // Топоры
        weapon("bearded_axe").iron(2).steel(1).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.AXE).step(ForgeOp.BEND, ForgeShape.EYE).step(ForgeOp.WELD)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND);
        weapon("viking_bearded_axe").iron(2).steel(1).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.AXE).step(ForgeOp.BEND, ForgeShape.EYE).step(ForgeOp.WELD)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND);
        weapon("horsemans_axe").iron(1).steel(1).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.AXE).step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH).step(ForgeOp.GRIND);
        weapon("crowbeak_hatchet").iron(1).steel(1).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.TOOL_HEAD).step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH)
                .step(ForgeOp.GRIND);
        weapon("broadaxe").iron(3).steel(1).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.DRAW, ForgeShape.BROAD_AXE).step(ForgeOp.BEND, ForgeShape.EYE).step(ForgeOp.WELD)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND);
        weapon("studded_battle_axe").iron(2).steel(1).wood(ModItems.AXE_HANDLE, 1).need(SmithingItems.RIVETS, 4)
                .step(ForgeOp.DRAW, ForgeShape.AXE).step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH).step(ForgeOp.GRIND)
                .step(ForgeOp.RIVET, 4);
        weapon("bearded_great_axe").iron(3).steel(1).wood(ModItems.HAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.BROAD_AXE).step(ForgeOp.BEND, ForgeShape.EYE).step(ForgeOp.WELD)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND);
        // Булавы и кистени
        weapon("flanged_mace").iron(3).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.CUT, 6).step(ForgeOp.PUNCH, 1).step(ForgeOp.RIVET, 1);
        weapon("spiked_mace").iron(3).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.PUNCH, 1).step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.RIVET, 1);
        weapon("shestopyor").steel(2).iron(2).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.CUT, 6).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("flail").iron(2).need(SmithingItems.CHAIN, 1).wood(ModItems.TOOL_HANDLE, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.PUNCH, 1).step(ForgeOp.RIVET, 2);
        weapon("spiked_flail").iron(3).need(SmithingItems.CHAIN, 1).wood(ModItems.TOOL_HANDLE, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.PUNCH, 1).step(ForgeOp.RIVET, 2);
        weapon("double_flail").iron(4).need(SmithingItems.CHAIN, 2).wood(ModItems.AXE_HANDLE, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.PUNCH, 2).step(ForgeOp.RIVET, 3);
        weapon("pole_flail").iron(3).need(SmithingItems.CHAIN, 1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.PUNCH, 1).step(ForgeOp.RIVET, 2);
        // Древковое
        weapon("winged_spear").steel(1).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.SPEAR).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.WELD)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("winged_leaf_shaped_spear").steel(1).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.LEAF_SPEAR).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.WELD)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("ranseur").steel(2).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.SPEAR).step(ForgeOp.CUT, 2).step(ForgeOp.BEND, ForgeShape.SOCKET)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 1);
        weapon("glaive").steel(2).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.POLE_BLADE).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.QUENCH)
                .step(ForgeOp.TEMPER, 2).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("voulge").steel(2).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.POLE_BLADE).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.QUENCH)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("heavy_voulge").steel(3).iron(2).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.BROAD_AXE).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.QUENCH)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("bardiche").steel(3).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.BROAD_AXE).step(ForgeOp.BEND, ForgeShape.EYE).step(ForgeOp.QUENCH)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("bill").steel(2).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.POLE_BLADE).step(ForgeOp.BEND, ForgeShape.HOOK)
                .step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("bill_hook").steel(2).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.CURVED).step(ForgeOp.BEND, ForgeShape.HOOK)
                .step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("simple_halberd").steel(2).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.POLE_BLADE).step(ForgeOp.PUNCH, 1).step(ForgeOp.BEND, ForgeShape.SOCKET)
                .step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("heavy_halberd").steel(3).iron(2).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.BROAD_AXE).step(ForgeOp.WELD).step(ForgeOp.PUNCH, 1)
                .step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("swiss_halberd").steel(3).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.POLE_BLADE).step(ForgeOp.WELD).step(ForgeOp.PUNCH, 1)
                .step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.QUENCH).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("poleaxe").steel(2).iron(2).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.AXE).step(ForgeOp.UPSET).step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("polehammer").steel(1).iron(3).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH)
                .step(ForgeOp.RIVET, 2);
        weapon("large_bec_de_corbin").steel(2).iron(2).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.UPSET).step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.BEND, ForgeShape.HOOK)
                .step(ForgeOp.PUNCH, 1).step(ForgeOp.QUENCH).step(ForgeOp.RIVET, 2);
        weapon("war_scythe").steel(2).iron(1).wood(ModItems.SPEAR_SHAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.CURVED).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.QUENCH)
                .step(ForgeOp.TEMPER, 2).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 2);
        weapon("pitchfork").iron(2).wood(ModItems.HAFT, 1)
                .step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.CUT, 1).step(ForgeOp.BEND, ForgeShape.SOCKET)
                .step(ForgeOp.RIVET, 1);
        weapon("jousting_lance").iron(1).wood(ModItems.SPEAR_SHAFT, 2)
                .step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.BEND, ForgeShape.SOCKET).step(ForgeOp.RIVET, 2);
        // Стрелы с бодкинами
        anvil(Tab.WEAPONS, () -> Items.ARROW, 8).steel(1).need(() -> Items.STICK, 8).need(() -> Items.FEATHER, 8)
                .plain().step(ForgeOp.DRAW, ForgeShape.POINT).step(ForgeOp.CUT, 4).step(ForgeOp.QUENCH);

        // ================================================================ наковальня: доспехи (ModItems)
        // Шлемы
        armor(ModItems.CERVELLIERE).steel(2).need(SmithingItems.RIVETS, 4).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.SKULLCAP).step(ForgeOp.RIVET, 4);
        armor(ModItems.CERVELLIERE_SCALED).steel(1).need(SmithingItems.RIVETS, 12).need(ModItems.BOILED_LEATHER, 1)
                .step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.CUT, 6).step(ForgeOp.PUNCH, 6).step(ForgeOp.RIVET, 6);
        armor(ModItems.BASCINET_HALF).steel(2).need(SmithingItems.RIVETS, 4).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.SKULLCAP).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 4);
        armor(ModItems.BASCINET).steel(3).need(SmithingItems.RIVETS, 6).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.BASCINET).step(ForgeOp.PUNCH, 4).step(ForgeOp.RIVET, 4);
        armor(ModItems.BARBUTE).steel(3).need(SmithingItems.RIVETS, 4).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.BARBUTE).step(ForgeOp.CUT, 1).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 4);
        armor(ModItems.GILDED_BARBUTE).steel(3).need(SmithingItems.RIVETS, 4).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.BARBUTE).step(ForgeOp.CUT, 1).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 4)
                .gilded();
        armor(ModItems.GREAT_BASCINET).steel(4).need(SmithingItems.RIVETS, 8).need(ModItems.LEATHER_STRAP, 2)
                .step(ForgeOp.RAISE, ForgeShape.BASCINET).step(ForgeOp.RAISE, ForgeShape.BEVOR).step(ForgeOp.PUNCH, 4)
                .step(ForgeOp.RIVET, 6);
        armor(ModItems.HUNDSGUGEL_BASCINET).steel(4).need(SmithingItems.RIVETS, 8).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.BASCINET).step(ForgeOp.RAISE, ForgeShape.VISOR).step(ForgeOp.PUNCH, 6)
                .step(ForgeOp.RIVET, 6);
        armor(ModItems.SCHWEINSGUGEL_BASCINET).steel(4).need(SmithingItems.RIVETS, 8).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.BASCINET).step(ForgeOp.RAISE, ForgeShape.VISOR).step(ForgeOp.PUNCH, 6)
                .step(ForgeOp.RIVET, 6);
        armor(ModItems.KLAPPVISOR_BASCINET).steel(4).need(SmithingItems.RIVETS, 8).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.BASCINET).step(ForgeOp.RAISE, ForgeShape.VISOR).step(ForgeOp.PUNCH, 4)
                .step(ForgeOp.RIVET, 6);
        armor(ModItems.ARMET).steel(5).need(SmithingItems.RIVETS, 10).need(ModItems.LEATHER_STRAP, 2)
                .step(ForgeOp.RAISE, ForgeShape.BASCINET).step(ForgeOp.RAISE, ForgeShape.VISOR)
                .step(ForgeOp.RAISE, ForgeShape.BEVOR).step(ForgeOp.PUNCH, 4).step(ForgeOp.RIVET, 8);
        armor(ModItems.INFANTRY_SALLET).steel(3).need(SmithingItems.RIVETS, 4).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.SALLET).step(ForgeOp.RIVET, 4);
        armor(ModItems.KNIGHT_SALLET).steel(4).need(SmithingItems.RIVETS, 6).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.SALLET).step(ForgeOp.PUNCH, 2).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 4);
        armor(ModItems.LIGHT_TOPFHELM).steel(3).need(SmithingItems.RIVETS, 8).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.RAISE, ForgeShape.GREAT_HELM).step(ForgeOp.PUNCH, 6)
                .step(ForgeOp.RIVET, 6);
        armor(ModItems.HEAVY_TOPFHELM).steel(4).need(SmithingItems.RIVETS, 10).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.RAISE, ForgeShape.GREAT_HELM).step(ForgeOp.PUNCH, 8)
                .step(ForgeOp.RIVET, 8);
        armor(ModItems.KUBELHELM).steel(5).need(SmithingItems.RIVETS, 12).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.RAISE, ForgeShape.GREAT_HELM).step(ForgeOp.PUNCH, 8)
                .step(ForgeOp.RIVET, 8);
        armor(ModItems.HALGVENT_HELMET).steel(3).need(SmithingItems.RIVETS, 6).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.BASCINET).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 4);
        armor(ModItems.ALLENDORIC_HELMET).steel(3).need(SmithingItems.RIVETS, 6).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.SKULLCAP).step(ForgeOp.RAISE, ForgeShape.BEVOR).step(ForgeOp.RIVET, 4);
        armor(ModItems.WENDISH_HELMET).steel(3).need(SmithingItems.RIVETS, 6).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.BASCINET).step(ForgeOp.PUNCH, 2).step(ForgeOp.RIVET, 4);
        armor(ModItems.KETTLE_HAT).steel(3).need(SmithingItems.RIVETS, 4).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.RAISE, ForgeShape.KETTLE).step(ForgeOp.RIVET, 4);
        armor(ModItems.KETTLE_HAT_BEVOR).steel(4).need(SmithingItems.RIVETS, 6).need(ModItems.LEATHER_STRAP, 2)
                .step(ForgeOp.RAISE, ForgeShape.KETTLE).step(ForgeOp.RAISE, ForgeShape.BEVOR).step(ForgeOp.RIVET, 6);
        // Кольчужное
        armor(ModItems.MAIL_COIF).need(SmithingItems.IRON_WIRE, 6).step(ForgeOp.WEAVE, 16);
        armor(ModItems.VENTAIL_COIF).need(SmithingItems.IRON_WIRE, 8).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.WEAVE, 20);
        armor(ModItems.VENTAIL_COIF_NASAL).need(SmithingItems.IRON_WIRE, 8).steel(1).need(ModItems.LEATHER_STRAP, 1)
                .step(ForgeOp.WEAVE, 20).step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.RIVET, 2);
        armor(ModItems.MAIL_VEST).need(SmithingItems.IRON_WIRE, 10).step(ForgeOp.WEAVE, 24);
        armor(ModItems.LIGHT_MAIL).need(SmithingItems.IRON_WIRE, 14).step(ForgeOp.WEAVE, 28);
        armor(ModItems.HAUBERGEON).need(SmithingItems.IRON_WIRE, 18).step(ForgeOp.WEAVE, 32);
        armor(ModItems.HAUBERK).need(SmithingItems.IRON_WIRE, 24).step(ForgeOp.WEAVE, 40);
        armor(ModItems.SALVAR_MAIL).need(SmithingItems.IRON_WIRE, 20).need(SmithingItems.BRASS_INGOT, 1)
                .step(ForgeOp.WEAVE, 34);
        armor(ModItems.PLATED_MAIL).need(SmithingItems.IRON_WIRE, 16).steel(2).need(SmithingItems.RIVETS, 8)
                .step(ForgeOp.WEAVE, 28).step(ForgeOp.RAISE, ForgeShape.CUIRASS).step(ForgeOp.RIVET, 8);
        armor(ModItems.MAIL_GLOVES).need(SmithingItems.IRON_WIRE, 4).step(ForgeOp.WEAVE, 12);
        armor(ModItems.MUFFLERS).need(SmithingItems.IRON_WIRE, 5).need(ModItems.TANNED_LEATHER, 1)
                .step(ForgeOp.WEAVE, 14);
        // Бригантины, ламеллярь, кирасы
        armor(ModItems.LIGHT_BRIGANDINE).steel(2).need(SmithingItems.RIVETS, 16).need(ModItems.LINEN_CLOTH, 2)
                .step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.CUT, 8).step(ForgeOp.PUNCH, 8).step(ForgeOp.RIVET, 8);
        armor(ModItems.HEAVY_BRIGANDINE).steel(4).need(SmithingItems.RIVETS, 24).need(ModItems.WOOLEN_CLOTH, 2)
                .need(ModItems.TANNED_LEATHER, 1)
                .step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.CUT, 10).step(ForgeOp.PUNCH, 10)
                .step(ForgeOp.RIVET, 10);
        armor(ModItems.NOBLE_BRIGANDINE).steel(4).need(SmithingItems.RIVETS, 24).need(ModItems.WOOLEN_CLOTH, 2)
                .need(SmithingItems.BRASS_FITTINGS, 1)
                .step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.CUT, 10).step(ForgeOp.PUNCH, 10)
                .step(ForgeOp.RIVET, 10).step(ForgeOp.GRIND);
        armor(ModItems.LAMELLAR_ARMOR).steel(3).need(ModItems.LEATHER_STRAP, 6)
                .step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.CUT, 10).step(ForgeOp.PUNCH, 10).step(ForgeOp.RIVET, 6);
        armor(ModItems.SIMPLE_CUIRASS).steel(4).need(ModItems.LEATHER_STRAP, 2).need(SmithingItems.BRASS_BUCKLE, 2)
                .need(SmithingItems.RIVETS, 4)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.RAISE, ForgeShape.CUIRASS).step(ForgeOp.GRIND)
                .step(ForgeOp.RIVET, 4);
        armor(ModItems.GWALDEN_CUIRASS).steel(5).need(ModItems.LEATHER_STRAP, 2).need(SmithingItems.BRASS_BUCKLE, 2)
                .need(SmithingItems.RIVETS, 6)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.RAISE, ForgeShape.CUIRASS).step(ForgeOp.PUNCH, 4)
                .step(ForgeOp.GRIND).step(ForgeOp.RIVET, 6);
        armor(ModItems.MARINIKIAN_CUIRASS).steel(5).need(ModItems.LEATHER_STRAP, 2).need(SmithingItems.BRASS_BUCKLE, 2)
                .need(SmithingItems.RIVETS, 6)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.RAISE, ForgeShape.CUIRASS).step(ForgeOp.GRIND)
                .step(ForgeOp.RIVET, 6);
        armor(ModItems.KASTENBRUST).steel(6).need(ModItems.LEATHER_STRAP, 2).need(SmithingItems.BRASS_BUCKLE, 2)
                .need(SmithingItems.RIVETS, 8)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.RAISE, ForgeShape.CUIRASS)
                .step(ForgeOp.RAISE, ForgeShape.CUIRASS).step(ForgeOp.GRIND).step(ForgeOp.RIVET, 8);
        armor(ModItems.PARADE_CUIRASS).steel(5).need(ModItems.LEATHER_STRAP, 2).need(SmithingItems.BRASS_FITTINGS, 1)
                .need(SmithingItems.RIVETS, 6)
                .step(ForgeOp.DRAW, ForgeShape.PLATE).step(ForgeOp.RAISE, ForgeShape.CUIRASS).step(ForgeOp.GRIND)
                .step(ForgeOp.RIVET, 6).gilded();
        // Руки
        armor(ModItems.VAMBRACES).steel(2).need(ModItems.LEATHER_STRAP, 2).need(SmithingItems.RIVETS, 4)
                .step(ForgeOp.RAISE, ForgeShape.GAUNTLET).step(ForgeOp.RIVET, 4);
        armor(ModItems.LAMELLAR_GAUNTLETS).steel(1).need(ModItems.LEATHER_STRAP, 2).need(ModItems.TANNED_LEATHER, 1)
                .step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.CUT, 6).step(ForgeOp.PUNCH, 6).step(ForgeOp.RIVET, 4);
        armor(ModItems.SPLINTED_GAUNTLETS).steel(2).need(SmithingItems.RIVETS, 12).need(ModItems.TANNED_LEATHER, 1)
                .step(ForgeOp.DRAW, ForgeShape.STRIP).step(ForgeOp.CUT, 5).step(ForgeOp.RIVET, 6);
        armor(ModItems.SIMPLE_PLATE_GAUNTLETS).steel(2).need(SmithingItems.RIVETS, 10).need(ModItems.TANNED_LEATHER, 1)
                .step(ForgeOp.RAISE, ForgeShape.GAUNTLET).step(ForgeOp.PUNCH, 6).step(ForgeOp.RIVET, 6);
        armor(ModItems.GWALDEN_GAUNTLETS).steel(3).need(SmithingItems.RIVETS, 12).need(ModItems.TANNED_LEATHER, 1)
                .step(ForgeOp.RAISE, ForgeShape.GAUNTLET).step(ForgeOp.PUNCH, 6).step(ForgeOp.GRIND)
                .step(ForgeOp.RIVET, 8);
        armor(ModItems.MARINIKIAN_GAUNTLETS).steel(3).need(SmithingItems.RIVETS, 12).need(ModItems.TANNED_LEATHER, 1)
                .step(ForgeOp.RAISE, ForgeShape.GAUNTLET).step(ForgeOp.PUNCH, 6).step(ForgeOp.GRIND)
                .step(ForgeOp.RIVET, 8);

        // ================================================================ литейный стол: слитки из тигля
        // Сплавы составляют в тигле на кузнечном горне (Crucible), здесь расплав разливают по изложницам
        for (Metal metal : new Metal[] {Metal.BRONZE, Metal.BRASS, Metal.PEWTER, Metal.GOLD, Metal.COPPER, Metal.TIN,
                Metal.LEAD, Metal.SILVER, Metal.SILVER_LEAD}) {
            cast(Tab.ALLOYS, () -> metal.ingot().getItem(), 1).melt(metal, 1).ingots().step(ForgeOp.POUR);
        }
        // ================================================================ литейный стол: отливки
        cast(Tab.CASTINGS, () -> Items.BELL, 1).melt(Metal.BRONZE, 6).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.MORTAR, 1).melt(Metal.BRONZE, 4).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, () -> Items.CAULDRON, 1).melt(Metal.BRONZE, 5).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.BRONZE_CLASP, 4).melt(Metal.BRONZE, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.BRASS_BUCKLE, 4).melt(Metal.BRASS, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.BRASS_FITTINGS, 2).melt(Metal.BRASS, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.PEWTER_TANKARD, 1).melt(Metal.PEWTER, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.PEWTER_PLATE, 2).melt(Metal.PEWTER, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.PEWTER_SPOON, 6).melt(Metal.PEWTER, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.PILGRIM_BADGE, 12).melt(Metal.PEWTER, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.LEAD_SEAL, 6).melt(Metal.LEAD, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, ModItems.LOOM_WEIGHT, 4).melt(Metal.LEAD, 1).plain().step(ForgeOp.POUR);
        cast(Tab.CASTINGS, SmithingItems.SILVER_GOBLET, 1).melt(Metal.SILVER, 1).plain().step(ForgeOp.POUR);
    }

    private SmithProducts() {}

    public static List<Product> anvil() {
        return Collections.unmodifiableList(ANVIL);
    }

    public static List<Product> casting() {
        return Collections.unmodifiableList(CASTING);
    }

    private static Product anvil(Tab tab, Supplier<? extends Item> result, int count) {
        Product product = new Product(tab, result::get, count);
        ANVIL.add(product);
        return product;
    }

    private static Product cast(Tab tab, Supplier<? extends Item> result, int count) {
        Product product = new Product(tab, result::get, count);
        CASTING.add(product);
        return product;
    }

    /** Оружие conquest_armory по имени: без мода — недоступно; изысканный вид — exquisite_<имя>. */
    private static Product weapon(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("conquest_armory", name);
        Product product = new Product(Tab.WEAPONS, () -> BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.get(id) : Items.AIR, 1).gear();
        product.exquisite = ResourceLocation.fromNamespaceAndPath("conquest_armory", "exquisite_" + name);
        ANVIL.add(product);
        return product;
    }

    private static Product armor(Supplier<? extends Item> item) {
        Product product = new Product(Tab.ARMOR, item::get, 1).gear();
        ANVIL.add(product);
        return product;
    }

    // ---------------------------------------------------------------- что чинят

    /** Значок расплава: слиток с подписью «Расплав: бронза». */
    private static ItemStack meltIcon(Metal metal) {
        ItemStack icon = metal.ingot();
        icon.set(net.minecraft.core.component.DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.translatable(
                "gui.halgventmod.workshop.melt_need", net.minecraft.network.chat.Component.translatable(metal.langKey())));
        return icon;
    }

    /** Значок перековки: молот с подписью «Перековка». */
    private static ItemStack reforgeIcon() {
        ItemStack icon = new ItemStack(SmithingItems.SMITH_HAMMER.get());
        icon.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                net.minecraft.network.chat.Component.translatable("gui.halgventmod.workshop.reforge"));
        return icon;
    }

    /** Кованое оружие и инструмент (не броня): клинки, топоры, ножи, серпы, кирки. */
    public static boolean reforgeable(ItemStack stack) {
        return stack.isDamaged() && !GearQuality.isArmor(stack) && (GearQuality.isWeapon(stack)
                || Smithing.hasQuality(stack) || stack.getItem() instanceof net.minecraft.world.item.TieredItem
                || stack.getItem() instanceof net.minecraft.world.item.ShearsItem);
    }

}
