package com.nrdhvnstd.halgventmod.carpentry;

import java.util.function.Predicate;

import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.item.ItemStack;

/**
 * Операции верстака плотника — этапы изделия, у каждой своя мини-игра (client/carpentry/*Step) и свой инструмент
 * из инвентаря. Изделие проходит свои этапы по порядку (BenchProducts), качество — от среднего счёта этапов.
 */
public enum Operation {
    //       ключ        тиков не меньше (защита от подделанных пакетов)
    PLANE   ("plane",    40),   // строгание рубанком: снять бугры до разметки
    CHISEL  ("chisel",   40),   // выборка долотом: вынуть гнездо по разметке, не сколов край
    DRILL   ("drill",    40),   // сверление коловоротом: крутить в меру, к концу — тише
    WHITTLE ("whittle",  40),   // обстругивание ножом: вывести профиль по разметке
    NAIL    ("nail",     30);   // сборка молотком: забить гвозди, заклёпки, клин

    private final String key;
    private final int minTicks;

    Operation(String key, int minTicks) {
        this.key = key;
        this.minTicks = minTicks;
    }

    public String key() {
        return key;
    }

    public int minTicks() {
        return minTicks;
    }

    /** Чем делают: рубанок, долото, коловорот, нож (мода или охотничий Conquest), молоток. */
    public Predicate<ItemStack> tool() {
        return switch (this) {
            case PLANE -> stack -> stack.is(ModItems.PLANE.get());
            case CHISEL -> stack -> stack.is(ModItems.CHISEL.get());
            case DRILL -> stack -> stack.is(ModItems.BRACE.get());
            case WHITTLE -> stack -> stack.is(LeatherTableBlockEntity.KNIVES);
            case NAIL -> stack -> stack.is(ModItems.HAMMER.get());
        };
    }

    /** Инструмент для показа в окне верстака. */
    public ItemStack icon() {
        return new ItemStack(switch (this) {
            case PLANE -> ModItems.PLANE.get();
            case CHISEL -> ModItems.CHISEL.get();
            case DRILL -> ModItems.BRACE.get();
            case WHITTLE -> ModItems.KNIFE.get();
            case NAIL -> ModItems.HAMMER.get();
        });
    }

    /** Название этапа: gui.halgventmod.bench_work.op.plane … */
    public String langKey() {
        return "gui.halgventmod.bench_work.op." + key;
    }
}
