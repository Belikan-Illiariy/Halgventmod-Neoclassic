package com.nrdhvnstd.halgventmod.smithing;

import java.util.function.Predicate;

import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.WareItem;

import net.minecraft.world.item.ItemStack;

/**
 * Этап работы у наковальни или литейного стола — своя мини-игра (client/smithing). Меняйте здесь ⚠️
 * <ul>
 *   <li>tool — что нужно в инвентаре (молот, клещи, волочильная доска, штемпель, тигель);</li>
 *   <li>forge — нужен горящий кузнечный горн рядом; tub — чан с водой, рассолом или жиром; wheel — точило;</li>
 *   <li>hot — работают по раскалённому: заготовка остывает, её греют в горне (пробел);</li>
 *   <li>minTicks — сервер не верит этапу быстрее (защита от подделанных пакетов).</li>
 * </ul>
 */
public enum ForgeOp {
    //           id             инструмент   горн   бочка  точило  горячо  тиков
    CONSOLIDATE ("consolidate", Tool.HAMMER, true,  false, false,  true,   120),
    DRAW        ("draw",        Tool.HAMMER, true,  false, false,  true,   80),
    UPSET       ("upset",       Tool.HAMMER, true,  false, false,  true,   60),
    BEND        ("bend",        Tool.HAMMER, true,  false, false,  true,   40),
    PUNCH       ("punch",       Tool.HAMMER, true,  false, false,  true,   40),
    CUT         ("cut",         Tool.HAMMER, true,  false, false,  true,   40),
    WELD        ("weld",        Tool.HAMMER, true,  false, false,  true,   60),
    RAISE       ("raise",       Tool.HAMMER, true,  false, false,  true,   100),
    QUENCH      ("quench",      Tool.TONGS,  true,  true,  false,  true,   40),
    TEMPER      ("temper",      Tool.TONGS,  true,  false, false,  false,  60),
    GRIND       ("grind",       Tool.NONE,   false, false, true,   false,  60),
    WIRE        ("wire",        Tool.DRAW_PLATE, true, false, false, false, 60),
    WEAVE       ("weave",       Tool.TONGS,  false, false, false,  false,  100),
    RIVET       ("rivet",       Tool.HAMMER, false, false, false,  false,  40),
    GILD        ("gild",        Tool.NONE,   true,  false, false,  false,  60),
    MINT        ("mint",        Tool.COIN_DIE, false, false, false, false, 60),
    MELT        ("melt",        Tool.CRUCIBLE, true, false, false, false,  80),
    POUR        ("pour",        Tool.CRUCIBLE, false, false, false, false, 40);

    /** Что нужно в инвентаре для этапа. Клещи нужны всем горячим этапам в придачу. */
    public enum Tool {
        NONE(stack -> true, null),
        HAMMER(stack -> stack.is(SmithingItems.SMITH_HAMMER.get()), SmithingItems.SMITH_HAMMER::get),
        TONGS(stack -> stack.is(SmithingItems.TONGS.get()), SmithingItems.TONGS::get),
        DRAW_PLATE(stack -> stack.is(SmithingItems.DRAW_PLATE.get()), SmithingItems.DRAW_PLATE::get),
        COIN_DIE(stack -> stack.is(SmithingItems.COIN_DIE.get()), SmithingItems.COIN_DIE::get),
        CRUCIBLE(stack -> stack.getItem() instanceof WareItem ware && ware.form() == VesselForm.CRUCIBLE,
                () -> com.nrdhvnstd.halgventmod.registry.ModItems.CLAY_CRUCIBLE.get());

        private final Predicate<ItemStack> test;
        private final java.util.function.Supplier<net.minecraft.world.item.Item> icon;

        Tool(Predicate<ItemStack> test, java.util.function.Supplier<net.minecraft.world.item.Item> icon) {
            this.test = test;
            this.icon = icon;
        }

        public boolean test(ItemStack stack) {
            return !stack.isEmpty() && test.test(stack);
        }

        public ItemStack icon() {
            return icon == null ? ItemStack.EMPTY : new ItemStack(icon.get());
        }
    }

    private final String id;
    private final Tool tool;
    private final boolean forge;
    private final boolean tub;
    private final boolean wheel;
    private final boolean hot;
    private final int minTicks;

    ForgeOp(String id, Tool tool, boolean forge, boolean tub, boolean wheel, boolean hot, int minTicks) {
        this.id = id;
        this.tool = tool;
        this.forge = forge;
        this.tub = tub;
        this.wheel = wheel;
        this.hot = hot;
        this.minTicks = minTicks;
    }

    public String id() {
        return id;
    }

    public Tool tool() {
        return tool;
    }

    public boolean needsForge() {
        return forge;
    }

    public boolean needsTub() {
        return tub;
    }

    public boolean needsWheel() {
        return wheel;
    }

    /** Работают по раскалённому: нужны клещи, заготовку греют в горне. */
    public boolean hot() {
        return hot;
    }

    public int minTicks() {
        return minTicks;
    }

    public String langKey() {
        return "gui.halgventmod.smith_work.op." + id;
    }
}
