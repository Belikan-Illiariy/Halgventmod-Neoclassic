package com.nrdhvnstd.halgventmod.smithing;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.WareItem;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Тигель с металлом ({@link Melt} на предмете). Как идёт дело:
 * <ol>
 *   <li>в окне кузнечного горна тигель ставят в горн, в ячейку шихты кладут слитки, золотой песок, галмей
 *       (и ком глины — крышку для латуни): всё ложится в тигель долями чистых металлов;</li>
 *   <li>«Плавить» — мини-игра плавки; выходит расплав: что за металл, решает состав ({@link #resolve});</li>
 *   <li>в горне расплав держится жидким, вынутый (клещами) — {@link #COOL_TICKS} тиков, потом застывает — его
 *       переплавляют снова;</li>
 *   <li>с жидким тиглем в инвентаре на литейном столе льют отливки и слитки — расплав убывает.</li>
 * </ol>
 * Смешивать можно только то, что даёт сплав: медь с оловом (бронза) или галмеем (латунь), олово со свинцом (пьютер),
 * остальное — по одному. Меняйте здесь ⚠️
 */
public final class Crucible {
    public static final int PER_INGOT = 10;          // долей в слитке
    public static final int CAPACITY = 100;          // долей в тигле — десять слитков
    public static final int COOL_TICKS = 1200;       // ⚠️ сколько вынутый расплав остаётся жидким (минута)
    public static final float ZINC_LOSS = 0.5F;      // без крышки галмей выгорает наполовину

    /** Что можно сплавлять вместе. */
    private static final List<Set<Metal>> FAMILIES = List.of(
            EnumSet.of(Metal.COPPER, Metal.TIN), EnumSet.of(Metal.COPPER, Metal.ZINC), EnumSet.of(Metal.TIN, Metal.LEAD),
            EnumSet.of(Metal.SILVER), EnumSet.of(Metal.GOLD), EnumSet.of(Metal.SILVER_LEAD));

    /** Что выйдет из расплава: металл, поправка к качеству за состав и чего сколько в сплаве (для окна). */
    public record Alloy(Metal metal, int delta, @Nullable Metal minor, float fraction, float ideal) {}

    private Crucible() {}

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof WareItem ware && ware.form() == VesselForm.CRUCIBLE;
    }

    @Nullable
    public static Melt melt(ItemStack stack) {
        return stack.get(ModComponents.CRUCIBLE_MELT.get());
    }

    public static Map<Metal, Integer> parts(@Nullable Melt melt) {
        Map<Metal, Integer> map = new EnumMap<>(Metal.class);
        if (melt != null) {
            melt.parts().forEach((id, count) -> {
                Metal metal = Metal.byId(id);
                if (metal != null && count > 0) {
                    map.put(metal, count);
                }
            });
        }
        return map;
    }

    public static int total(@Nullable Melt melt) {
        int total = 0;
        for (int count : parts(melt).values()) {
            total += count;
        }
        return total;
    }

    /** Среднее качество того, что в тигле. */
    public static int quality(@Nullable Melt melt) {
        int total = total(melt);
        return total <= 0 ? Smithing.BASE_QUALITY : Math.clamp(Math.round(melt.qualitySum() / (float) total), 0,
                Smithing.MAX_QUALITY);
    }

    private static Melt of(Map<Metal, Integer> parts, int qualitySum, boolean lid, boolean molten, long liquidUntil) {
        Map<String, Integer> ids = new HashMap<>();
        parts.forEach((metal, count) -> {
            if (count > 0) {
                ids.put(metal.id(), count);
            }
        });
        return new Melt(ids, qualitySum, lid, molten, liquidUntil);
    }

    private static void set(ItemStack crucible, @Nullable Melt melt) {
        if (melt == null || total(melt) <= 0 && !melt.lid()) {
            crucible.remove(ModComponents.CRUCIBLE_MELT.get());
        } else {
            crucible.set(ModComponents.CRUCIBLE_MELT.get(), melt);
        }
    }

    // ---------------------------------------------------------------- шихта

    /**
     * Положить в тигель одну штуку шихты (стопку не трогает): null — легло, иначе ключ сообщения, почему нет.
     * Ком глины — крышка. Свежая шихта на расплав — его снова надо плавить.
     */
    @Nullable
    public static String accept(ItemStack crucible, ItemStack charge) {
        if (!is(crucible)) {
            return "crucible_none";
        }
        if (Pottery.cracked(crucible)) {
            return "crucible_broken";
        }
        Melt melt = melt(crucible);
        Map<Metal, Integer> parts = parts(melt);
        int qualitySum = melt == null ? 0 : melt.qualitySum();
        boolean lid = melt != null && melt.lid();
        if (charge.is(Items.CLAY_BALL)) {
            if (lid) {
                return "crucible_lid_already";
            }
            set(crucible, of(parts, qualitySum, true, false, 0));
            return null;
        }
        if (Metal.iron(charge)) {
            return "crucible_iron";
        }
        Map<Metal, Integer> add = Metal.charge(charge);
        if (add == null) {
            return "crucible_not_metal";
        }
        int adding = 0;
        for (int count : add.values()) {
            adding += count;
        }
        if (total(melt) + adding > CAPACITY) {
            return "crucible_full";
        }
        Set<Metal> metals = EnumSet.noneOf(Metal.class);
        metals.addAll(parts.keySet());
        metals.addAll(add.keySet());
        if (!mixable(metals)) {
            return "crucible_mix";
        }
        add.forEach((metal, count) -> parts.merge(metal, count, Integer::sum));
        set(crucible, of(parts, qualitySum + Smithing.quality(charge) * adding, lid, false, 0));
        return null;
    }

    private static boolean mixable(Set<Metal> metals) {
        for (Set<Metal> family : FAMILIES) {
            if (family.containsAll(metals)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- что выйдет

    /** Что даст расплав этого состава; null — ничего (галмей без меди, пусто). */
    @Nullable
    public static Alloy resolve(@Nullable Melt melt) {
        Map<Metal, Integer> parts = parts(melt);
        int total = total(melt);
        if (total <= 0) {
            return null;
        }
        if (parts.size() == 1) {
            Metal only = parts.keySet().iterator().next();
            return only.castable() ? new Alloy(only, 0, null, 0, 0) : null;
        }
        float tin = parts.getOrDefault(Metal.TIN, 0) / (float) total;
        float zinc = parts.getOrDefault(Metal.ZINC, 0) / (float) total;
        float lead = parts.getOrDefault(Metal.LEAD, 0) / (float) total;
        if (parts.containsKey(Metal.COPPER) && parts.containsKey(Metal.TIN)) {
            // Бронза: олова десятая часть ⚠️
            return tin < 0.04F ? new Alloy(Metal.COPPER, -1, Metal.TIN, tin, 0.10F)
                    : new Alloy(Metal.BRONZE, off(tin, 0.10F, 0.04F), Metal.TIN, tin, 0.10F);
        }
        if (parts.containsKey(Metal.COPPER) && parts.containsKey(Metal.ZINC)) {
            // Латунь: цинка около трети
            return zinc < 0.10F ? new Alloy(Metal.COPPER, -1, Metal.ZINC, zinc, 0.30F)
                    : new Alloy(Metal.BRASS, off(zinc, 0.30F, 0.08F), Metal.ZINC, zinc, 0.30F);
        }
        if (parts.containsKey(Metal.TIN) && parts.containsKey(Metal.LEAD)) {
            // Пьютер: свинца пятая часть
            if (lead < 0.05F) {
                return new Alloy(Metal.TIN, -1, Metal.LEAD, lead, 0.20F);
            }
            return lead > 0.60F ? new Alloy(Metal.LEAD, -1, Metal.LEAD, lead, 0.20F)
                    : new Alloy(Metal.PEWTER, off(lead, 0.20F, 0.08F), Metal.LEAD, lead, 0.20F);
        }
        return null;
    }

    /** Поправка за отход от лучшего состава: −1 за каждую ступень step, не больше −3. */
    private static int off(float fraction, float ideal, float step) {
        return -Math.min(3, Math.round(Math.abs(fraction - ideal) / step));
    }

    /** Галмей в открытом тигле выгорает: что останется после плавки без крышки. */
    public static Melt burnZinc(Melt melt) {
        if (melt.lid()) {
            return melt;
        }
        Map<Metal, Integer> parts = parts(melt);
        Integer zinc = parts.get(Metal.ZINC);
        if (zinc == null) {
            return melt;
        }
        int total = total(melt);
        int left = Math.round(zinc * (1 - ZINC_LOSS));
        parts.put(Metal.ZINC, left);
        int newTotal = total - zinc + left;
        return of(parts, Math.round(melt.qualitySum() * newTotal / (float) Math.max(1, total)), false, melt.molten(),
                melt.liquidUntil());
    }

    /** Что будет на выходе плавки (с учётом выгорания галмея). */
    @Nullable
    public static Alloy willGive(@Nullable Melt melt) {
        return melt == null ? null : resolve(burnZinc(melt));
    }

    /** После плавки: состав (крышка ушла, галмей мог выгореть), новое качество, жидкий до until. */
    public static void molten(ItemStack crucible, int quality, long until) {
        Melt melt = melt(crucible);
        if (melt == null) {
            return;
        }
        Melt burnt = burnZinc(melt);
        set(crucible, of(parts(burnt), quality * total(burnt), false, true, until));
    }

    // ---------------------------------------------------------------- металл из сыродутного горна

    /** Можно ли слить в тигель столько долей металла: null — да, иначе ключ сообщения. */
    @Nullable
    public static String canPour(ItemStack crucible, Metal metal, int amount) {
        if (!is(crucible)) {
            return "bloomery_need_crucible";
        }
        if (Pottery.cracked(crucible)) {
            return "crucible_broken";
        }
        Melt melt = melt(crucible);
        if (total(melt) + amount > CAPACITY) {
            return "crucible_full";
        }
        Set<Metal> metals = EnumSet.of(metal);
        metals.addAll(parts(melt).keySet());
        return mixable(metals) ? null : "crucible_mix";
    }

    /**
     * Слить в тигель расплав из горна. В пустой тигель или к тому же жидкому металлу — сразу жидкий расплав
     * (до until); к другой шихте — ложится шихтой, сплавлять в кузнечном горне.
     */
    public static void pour(ItemStack crucible, Metal metal, int amount, int quality, long until, Level level) {
        Melt melt = melt(crucible);
        Map<Metal, Integer> parts = parts(melt);
        boolean same = parts.isEmpty() || parts.size() == 1 && parts.containsKey(metal) && liquid(crucible, level);
        parts.merge(metal, amount, Integer::sum);
        int qualitySum = (melt == null ? 0 : melt.qualitySum()) + quality * amount;
        boolean lid = melt != null && melt.lid();
        set(crucible, of(parts, qualitySum, lid, same, same ? until : 0));
    }

    // ---------------------------------------------------------------- жидкий ли

    public static boolean liquid(ItemStack crucible, Level level) {
        Melt melt = melt(crucible);
        return melt != null && melt.molten() && melt.liquidUntil() >= level.getGameTime();
    }

    /** Секунд, пока жидкий; 0 — застыл или не плавлен. */
    public static int liquidSeconds(ItemStack crucible, Level level) {
        Melt melt = melt(crucible);
        return melt == null || !melt.molten() ? 0 : (int) Math.max(0, (melt.liquidUntil() - level.getGameTime()) / 20);
    }

    /** Держать жидким до until (в горне, пока заливают). */
    public static void keepLiquid(ItemStack crucible, long until) {
        Melt melt = melt(crucible);
        if (melt != null && melt.molten() && melt.liquidUntil() < until) {
            set(crucible, new Melt(melt.parts(), melt.qualitySum(), melt.lid(), true, until));
        }
    }

    /** Металл жидкого расплава; null — нет такого. */
    @Nullable
    public static Metal liquidMetal(ItemStack crucible, Level level) {
        if (!liquid(crucible, level)) {
            return null;
        }
        Alloy alloy = resolve(melt(crucible));
        return alloy == null ? null : alloy.metal();
    }

    /** Отлить долей: убывает поровну по составу; пусто — тигель чист. */
    public static void take(ItemStack crucible, int amount) {
        Melt melt = melt(crucible);
        int total = total(melt);
        if (melt == null || total <= 0) {
            return;
        }
        int left = Math.max(0, total - amount);
        if (left == 0) {
            set(crucible, null);
            return;
        }
        Map<Metal, Integer> parts = parts(melt);
        Map<Metal, Integer> after = new EnumMap<>(Metal.class);
        int sum = 0;
        Metal biggest = null;
        for (Map.Entry<Metal, Integer> entry : parts.entrySet()) {
            int count = Math.round(entry.getValue() * left / (float) total);
            after.put(entry.getKey(), count);
            sum += count;
            if (biggest == null || entry.getValue() > parts.get(biggest)) {
                biggest = entry.getKey();
            }
        }
        after.merge(biggest, left - sum, Integer::sum);
        set(crucible, of(after, quality(melt) * left, false, melt.molten(), melt.liquidUntil()));
    }

    // ---------------------------------------------------------------- у игрока

    /** Ячейка с жидким тиглем этого металла, где расплава больше всего; −1 — нет. */
    public static int find(Player player, Metal metal) {
        Inventory inventory = player.getInventory();
        int best = -1;
        int bestParts = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (is(stack) && liquidMetal(stack, player.level()) == metal && total(melt(stack)) > bestParts) {
                best = i;
                bestParts = total(melt(stack));
            }
        }
        return best;
    }

    /** Лучший тигель с металлом при игроке (для окна стола): жидкий нужного металла, иначе любой с металлом. */
    public static ItemStack best(Player player, @Nullable Metal metal) {
        if (metal != null) {
            int slot = find(player, metal);
            if (slot >= 0) {
                return player.getInventory().getItem(slot);
            }
        }
        Inventory inventory = player.getInventory();
        ItemStack any = ItemStack.EMPTY;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (is(stack) && total(melt(stack)) > 0) {
                if (liquid(stack, player.level())) {
                    return stack;
                }
                if (any.isEmpty()) {
                    any = stack;
                }
            }
        }
        return any;
    }
}
