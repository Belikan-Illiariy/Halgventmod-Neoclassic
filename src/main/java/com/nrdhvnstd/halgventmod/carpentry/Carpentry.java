package com.nrdhvnstd.halgventmod.carpentry;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.quality.QualityDisplay;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Плотничество: числа, сообщения, качество деревянных вещей. Меняйте здесь.
 *
 * <pre>
 *   бревно ─┬─ колода для колки, топор ─────────────────────── дрова (и кора)
 *           └─ пильные козлы, пила: {@link #CUTS} пропила ──────── доски ─ верстак плотника: этапы изделия ─ изделия
 *                                     кривой пропил — горбыль на дрова      (рубанок, долото, коловорот, нож, молоток)
 * </pre>
 * Досок из бревна в ванильном верстаке больше нет (data/minecraft/recipe/*_planks.json выключены) — только пилой; с козел
 * выходят одни «Доски» (ModItems.BOARDS) на любое дерево, они в теге minecraft:planks и годятся в ванильные рецепты.
 * Качество изделия с верстака — от среднего счёта его этапов (BenchWorkScreen), 0–5, как у посуды: Посредственное — 2.
 * Хранится компонентом {@link ModComponents#WOOD_QUALITY}; у инструментов от него зависит прочность.
 */
public final class Carpentry {
    // Колода для колки (ChoppingBlockEntity)
    public static final int SPLIT_STRIKES = 3;          // ровных ударов топором на бревно
    public static final int FIREWOOD_PER_LOG = 3;       // ⚠️ дров с бревна
    public static final float BARK_CHANCE = 0.5F;       // ⚠️ с неокорённого бревна падает кора

    // Пильные козлы (SawhorseBlockEntity, SawingScreen)
    public static final int CUTS = 3;                   // пропилов на бревно: досок — на одну больше
    public static final int MIN_CUT_TICKS = 30;         // пропил не чаще (защита от подделанных пакетов)
    public static final int JAM_WEAR = 3;               // столько прочности пилы стоит каждый раз, когда её зажало
    public static final int CUT_EVEN = 0;               // итог пропила: ровный
    public static final int CUT_CROOKED = 1;            // увёл в сторону — доска ушла в горбыль

    // Верстак плотника (CarpenterBenchBlockEntity, BenchWorkScreen): счёт этапа 0–100, качество — от среднего
    public static final int BASE_QUALITY = 2;           // Посредственное
    public static final int MAX_QUALITY = 5;
    public static final int SCORE_MASTERWORK = 92;      // средний счёт не меньше: +3 (Шедевр)
    public static final int SCORE_EXCELLENT = 80;       // +2
    public static final int SCORE_FINE = 65;            // +1
    public static final int SCORE_MEDIOCRE = 45;        // 0
    public static final int SCORE_POOR = 25;            // −1, ниже — −2

    /** Прочность инструмента по качеству: Ужасное … Шедевр ⚠️ */
    private static final float[] DURABILITY = {0.6F, 0.8F, 1.0F, 1.2F, 1.4F, 1.7F};

    private static final Map<Item, Item> PLANKS = new HashMap<>();

    private Carpentry() {}

    /**
     * Какие доски выходят из бревна (или окорённого бревна, древесины, стебля): «oak_log» → «oak_planks»,
     * «conquest:alder_log» → «conquest:alder_wood_planks». Незнакомое бревно — дубовые доски. null — не бревно.
     */
    @Nullable
    public static Item planksOf(ItemStack log) {
        if (log.isEmpty() || !log.is(ItemTags.LOGS)) {
            return null;
        }
        return PLANKS.computeIfAbsent(log.getItem(), Carpentry::findPlanks);
    }

    private static Item findPlanks(Item log) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(log);
        String base = id.getPath();
        if (base.startsWith("stripped_")) {
            base = base.substring("stripped_".length());
        }
        for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae"}) {
            if (base.endsWith(suffix)) {
                base = base.substring(0, base.length() - suffix.length());
                break;
            }
        }
        for (String candidate : new String[] {base + "_planks", base + "_wood_planks"}) {
            ResourceLocation planks = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), candidate);
            if (BuiltInRegistries.ITEM.containsKey(planks)) {
                return BuiltInRegistries.ITEM.get(planks);
            }
        }
        return Items.OAK_PLANKS;
    }

    /** С бревна ещё не снята кора. */
    public static boolean hasBark(ItemStack log) {
        return !BuiltInRegistries.ITEM.getKey(log.getItem()).getPath().startsWith("stripped_");
    }

    /** Поправка качества изделия по среднему счёту этапов (0–100). */
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

    /**
     * Твёрдость дерева для пилы ⚠️: множитель того, насколько углубляет один толчок. Дуб, акация, джунгли — тверды,
     * ель, берёза, вишня — в меру, остальное (багровое, искажённое, мангр, незнакомое) — мягче.
     */
    public static float softness(ItemStack log) {
        String path = BuiltInRegistries.ITEM.getKey(log.getItem()).getPath();
        if (path.contains("oak") || path.contains("acacia") || path.contains("jungle") || path.contains("ash")) {
            return 0.8F;
        }
        if (path.contains("spruce") || path.contains("birch") || path.contains("cherry") || path.contains("pine")
                || path.contains("larch")) {
            return 1.0F;
        }
        return 1.15F;
    }

    // ---------------------------------------------------------------- качество деревянной вещи

    public static boolean hasQuality(ItemStack stack) {
        return stack.has(ModComponents.WOOD_QUALITY.get());
    }

    public static int quality(ItemStack stack) {
        Integer quality = stack.get(ModComponents.WOOD_QUALITY.get());
        return quality == null ? BASE_QUALITY : quality;
    }

    /** Задать качество; у инструмента — прочность по качеству (от исходной прочности предмета). */
    public static ItemStack setQuality(ItemStack stack, int quality) {
        int q = Math.clamp(quality, 0, MAX_QUALITY);
        stack.set(ModComponents.WOOD_QUALITY.get(), q);
        Integer base = stack.getPrototype().get(DataComponents.MAX_DAMAGE);
        if (base != null && base > 0) {
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(base * DURABILITY[q])));
        }
        QualityDisplay.restoreRarity(stack);
        return stack;
    }

    /** Множитель прочности по качеству. */
    public static float durabilityFactor(ItemStack stack) {
        return DURABILITY[Math.clamp(quality(stack), 0, MAX_QUALITY)];
    }

    public static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.carpentry." + key, args), true);
    }
}
