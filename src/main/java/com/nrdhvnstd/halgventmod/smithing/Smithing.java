package com.nrdhvnstd.halgventmod.smithing;

import java.util.List;

import com.nrdhvnstd.halgventmod.quality.QualityDisplay;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Кузнечество: общие числа, качество металла, сообщения. Меняйте здесь.
 *
 * <pre>
 *   жилы, ил, россыпь ─ руда ─┬─ гончарный горн: обжиг (болотная, медная; киноварь — в ртуть)
 *   угольная куча ─ уголь     └─ сыродутный горн (руда + древесный уголь + флюс, мехи) ─┬─ железная крица
 *                                                                                       ├─ медь, олово, серебро, золото
 *                                                                                       └─ свинец с серебром ─ купель
 *   кузнечный горн + наковальня ─ крица → железный слиток → цементационный ящик → стальной слиток
 *                                 изделия: этапы ковки (SmithProducts), закалка в бочке, точило
 *   литейный стол + тигель ─ бронза, латунь, пьютер, литьё
 * </pre>
 * Качество металла 0–5 (Посредственное — 2) хранится компонентом {@link ModComponents#METAL_QUALITY}: у крицы и
 * слитков — от руды и плавки, у кованых инструментов — от металла и работы, от него прочность. Оружие и броня
 * выходят с качеством снаряжения (GearQuality).
 */
public final class Smithing {
    public static final int BASE_QUALITY = 2;
    public static final int MAX_QUALITY = 5;

    /** Прочность кованого инструмента по качеству: Ужасное … Шедевр ⚠️ */
    private static final float[] DURABILITY = {0.6F, 0.8F, 1.0F, 1.2F, 1.45F, 1.75F};

    // Работа: счёт этапа 0–100 → поправка качества (как у плотника)
    public static final int SCORE_MASTERWORK = 92;
    public static final int SCORE_EXCELLENT = 80;
    public static final int SCORE_FINE = 65;
    public static final int SCORE_MEDIOCRE = 45;
    public static final int SCORE_POOR = 25;

    private Smithing() {}

    /** Поправка качества по счёту работы (0–100): от −2 до +3. */
    public static int workDelta(int score) {
        if (score >= SCORE_MASTERWORK) {
            return 3;
        }
        if (score >= SCORE_EXCELLENT) {
            return 2;
        }
        if (score >= SCORE_FINE) {
            return 1;
        }
        if (score >= SCORE_MEDIOCRE) {
            return 0;
        }
        return score >= SCORE_POOR ? -1 : -2;
    }

    // ---------------------------------------------------------------- качество металла

    public static boolean hasQuality(ItemStack stack) {
        return stack.has(ModComponents.METAL_QUALITY.get());
    }

    public static int quality(ItemStack stack) {
        Integer quality = stack.get(ModComponents.METAL_QUALITY.get());
        return quality == null ? BASE_QUALITY : quality;
    }

    /** Задать качество; у инструмента — прочность по качеству (от исходной прочности предмета). */
    public static ItemStack setQuality(ItemStack stack, int quality) {
        int q = Math.clamp(quality, 0, MAX_QUALITY);
        stack.set(ModComponents.METAL_QUALITY.get(), q);
        Integer base = stack.getPrototype().get(DataComponents.MAX_DAMAGE);
        if (base != null && base > 0) {
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(base * DURABILITY[q])));
        }
        QualityDisplay.restoreRarity(stack);
        return stack;
    }

    public static float durabilityFactor(ItemStack stack) {
        return DURABILITY[Math.clamp(quality(stack), 0, MAX_QUALITY)];
    }

    /** Среднее качество металла стопок (по штукам), округлённое; пусто — Посредственное. */
    public static int averageQuality(List<ItemStack> stacks) {
        int total = 0;
        int count = 0;
        for (ItemStack stack : stacks) {
            total += quality(stack) * stack.getCount();
            count += stack.getCount();
        }
        return count == 0 ? BASE_QUALITY : Math.round(total / (float) count);
    }

    /** Время мира в единицах суток (24000 на сутки) — по нему тлеет угольная куча и томится сталь. */
    public static long now(net.minecraft.world.level.Level level) {
        return level.getServer() != null ? level.getServer().overworld().getDayTime() : level.getDayTime();
    }

    /** Сколько суток прошло с такого-то времени. */
    public static double daysSince(net.minecraft.world.level.Level level, long since) {
        return Math.max(0, now(level) - since) / (double) com.nrdhvnstd.halgventmod.world.Calendar.TIME_PER_DAY;
    }

    /** Реальные минуты на столько суток игры. */
    public static long realMinutes(double days) {
        return Math.max(1, Math.round(days * com.nrdhvnstd.halgventmod.world.DayLength.DAY_LENGTH_TICKS / 1200.0));
    }

    public static boolean roasted(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModComponents.ROASTED.get()));
    }

    /** Обжигают в гончарном горне: болотная и медная руда (ещё не обожжённые), киноварь — в ртуть. */
    public static boolean roastable(ItemStack stack) {
        return (stack.is(SmithingItems.BOG_IRON_ORE.get()) || stack.is(SmithingItems.COPPER_ORE.get())) && !roasted(stack)
                || stack.is(SmithingItems.CINNABAR.get());
    }

    /** Руды в горне за раз в одной ячейке. */
    public static final int ROAST_STACK = 8;

    /** Что выходит из горна: обожжённая руда или ртуть из киновари; недожог — какой положили. */
    public static ItemStack roast(ItemStack raw, boolean done) {
        if (!done) {
            return raw;
        }
        if (raw.is(SmithingItems.CINNABAR.get())) {
            return new ItemStack(SmithingItems.MERCURY.get(), raw.getCount());
        }
        ItemStack roasted = raw.copy();
        roasted.set(ModComponents.ROASTED.get(), true);
        return roasted;
    }

    public static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.smithing." + key, args), true);
    }

    public static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.smithing." + key, args), false);
    }
}
