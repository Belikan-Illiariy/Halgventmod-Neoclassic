package com.nrdhvnstd.halgventmod.alchemy;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.herbs.Herb;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModEffects;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.smithing.Smithing;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Правила алхимии в одном месте. Меняйте здесь ⚠️
 *
 * <pre>
 * ступа           — измельчение:  сушёные травы → травяной порошок, кость → костная мука
 * гончарный горн  — прокаливание: свинцовый слиток → свинцовый пепел, кость → костяная зола
 * реторта         — возгонка:     медная руда (колчедан) → сера (+ обожжённая руда), сера → серный цвет,
 *                                 кость → нашатырь (+ костяная зола), ртуть + купорос → сулема
 * бродильная бочка — сгущение, настаивание, осаждение:
 *                   уксус + свинцовый пепел → белая краска, уксус + медь → ярь-медянка,
 *                   вода + обожжённая медная руда → купорос, вода + зола → поташ (для стекла),
 *                   крепкое + ярь-медянка → ранозаживляющий бальзам, крепкое + серный цвет → серный бальзам,
 *                   крепкое + мёд + костяная зола → териак, крепкое + травяной порошок → эссенция травы
 * </pre>
 * Нашатырь нюхают — трезвеет и проходит дурнота; сулемой мажут клинок — несколько ударов отравляют.
 */
public final class Alchemy {
    // Реторта: сколько единиц жара на одну возгонку (жар 1–3 за тик, как у перегонного куба)
    public static final int SUBLIME_TIME = 600;
    public static final float OVERHEAT_LOSS = 0.25F;   // жар 3 — быстрее, но часть уходит дымом
    // Сулема на клинке
    public static final int COAT_HITS = 4;
    public static final int COAT_POISON_TICKS = 8 * 20;
    // Нашатырь: на сколько трезвеет (шкала опьянения 0–100)
    public static final float SNIFF_SOBER = 30;
    // Снадобья: сколько длится действие, тиков
    private static final int SHORT = 10 * 20;
    private static final int MEDIUM = 30 * 20;
    private static final int LONG = 3 * 60 * 20;
    /** Эссенция травы действует во столько раз дольше отвара. */
    public static final int ESSENCE_FACTOR = 3;

    private Alchemy() {}

    // ---------------------------------------------------------------- прокаливание (гончарный горн)

    /** Что прокаливают в гончарном горне (вместе с обжигом руды, Smithing.roastable). */
    public static boolean calcinable(ItemStack stack) {
        return stack.is(SmithingItems.LEAD_INGOT.get()) || stack.is(Items.BONE);
    }

    /** Прокалённое: свинцовый слиток — два свинцового пепла, кость — костяная зола. */
    public static ItemStack calcine(ItemStack raw) {
        if (raw.is(SmithingItems.LEAD_INGOT.get())) {
            return new ItemStack(AlchemyItems.LEAD_ASH.get(), raw.getCount() * 2);
        }
        return new ItemStack(AlchemyItems.BONE_ASH.get(), raw.getCount());
    }

    // ---------------------------------------------------------------- возгонка (реторта)

    /**
     * Возгонка: что кладут в колбу (и что добавляют), что уходит в приёмник, что остаётся в колбе.
     * @param additive вторая составляющая (по одной на каждую), null — не нужна
     */
    public record Sublimation(String id, Predicate<ItemStack> input, @Nullable Predicate<ItemStack> additive,
                              Function<ItemStack, ItemStack> product, Function<ItemStack, ItemStack> residue, ItemStack icon) {
        public boolean needsAdditive() {
            return additive != null;
        }
    }

    private static List<Sublimation> sublimations;

    public static List<Sublimation> sublimations() {
        if (sublimations == null) {
            sublimations = List.of(
                    new Sublimation("pyrite", stack -> stack.is(SmithingItems.COPPER_ORE.get()) && !Smithing.roasted(stack), null,
                            raw -> new ItemStack(AlchemyItems.SULFUR.get()), raw -> Smithing.roast(raw.copyWithCount(1), true),
                            new ItemStack(SmithingItems.COPPER_ORE.get())),
                    new Sublimation("sulfur", stack -> stack.is(AlchemyItems.SULFUR.get()), null,
                            raw -> new ItemStack(AlchemyItems.SULFUR_FLOWERS.get()), raw -> ItemStack.EMPTY,
                            new ItemStack(AlchemyItems.SULFUR.get())),
                    new Sublimation("bone", stack -> stack.is(Items.BONE), null,
                            raw -> new ItemStack(AlchemyItems.SAL_AMMONIAC.get()), raw -> new ItemStack(AlchemyItems.BONE_ASH.get()),
                            new ItemStack(Items.BONE)),
                    new Sublimation("sublimate", stack -> stack.is(SmithingItems.MERCURY.get()),
                            stack -> stack.is(AlchemyItems.VITRIOL.get()),
                            raw -> new ItemStack(AlchemyItems.SUBLIMATE.get()), raw -> new ItemStack(Items.GLASS_BOTTLE),
                            new ItemStack(SmithingItems.MERCURY.get())));
        }
        return sublimations;
    }

    /** Возгонка для того, что в колбе; null — это не возгоняют. */
    @Nullable
    public static Sublimation sublimation(ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        for (Sublimation s : sublimations()) {
            if (s.input().test(input)) {
                return s;
            }
        }
        return null;
    }

    /** Подходит ли как добавка хоть к одной возгонке. */
    public static boolean sublimationAdditive(ItemStack stack) {
        return sublimations().stream().anyMatch(s -> s.additive() != null && s.additive().test(stack));
    }

    // ---------------------------------------------------------------- бочка: сгущение, настаивание, осаждение

    /** Основа в бродильной бочке для алхимии. */
    public enum Base { SPIRIT, VINEGAR, WATER }

    /**
     * Настой в бродильной бочке: основа + реагент (по одному на склянку основы) [+ добавка из обычных: мёд, травы, ягоды]
     * → что выходит с каждой склянки и через сколько суток. Твёрдое (solid) выгребают руками, жидкое — разливают.
     */
    public record Steep(String id, Base base, Predicate<ItemStack> reagent, @Nullable String flavour,
                        Function<ItemStack, ItemStack> result, double days, boolean solid, ItemStack icon) {}

    private static List<Steep> steeps;

    public static List<Steep> steeps() {
        if (steeps == null) {
            steeps = List.of(
                    new Steep("white_lead", Base.VINEGAR, stack -> stack.is(AlchemyItems.LEAD_ASH.get()), null,
                            reagent -> new ItemStack(Items.WHITE_DYE, 2), 2, true, new ItemStack(Items.WHITE_DYE)),
                    new Steep("verdigris", Base.VINEGAR, stack -> stack.is(SmithingItems.COPPER_INGOT.get()), null,
                            reagent -> new ItemStack(AlchemyItems.VERDIGRIS.get(), 2), 4, true,
                            new ItemStack(AlchemyItems.VERDIGRIS.get())),
                    new Steep("vitriol", Base.WATER, stack -> stack.is(SmithingItems.COPPER_ORE.get()) && Smithing.roasted(stack), null,
                            reagent -> new ItemStack(AlchemyItems.VITRIOL.get()), 3, true, new ItemStack(AlchemyItems.VITRIOL.get())),
                    new Steep("potash", Base.WATER, stack -> stack.is(com.nrdhvnstd.halgventmod.registry.ModItems.ASH.get()), null,
                            reagent -> new ItemStack(com.nrdhvnstd.halgventmod.glass.GlassItems.POTASH.get()), 2, true,
                            new ItemStack(com.nrdhvnstd.halgventmod.glass.GlassItems.POTASH.get())),
                    new Steep("wound_balm", Base.SPIRIT, stack -> stack.is(AlchemyItems.VERDIGRIS.get()), null,
                            reagent -> new ItemStack(AlchemyItems.WOUND_BALM.get()), 3, false,
                            new ItemStack(AlchemyItems.WOUND_BALM.get())),
                    new Steep("sulfur_balm", Base.SPIRIT, stack -> stack.is(AlchemyItems.SULFUR_FLOWERS.get()), null,
                            reagent -> new ItemStack(AlchemyItems.SULFUR_BALM.get()), 3, false,
                            new ItemStack(AlchemyItems.SULFUR_BALM.get())),
                    new Steep("theriac", Base.SPIRIT, stack -> stack.is(AlchemyItems.BONE_ASH.get()), "honey",
                            reagent -> new ItemStack(AlchemyItems.THERIAC.get()), 5, false, new ItemStack(AlchemyItems.THERIAC.get())),
                    new Steep("essence", Base.SPIRIT, stack -> stack.is(AlchemyItems.HERB_POWDER.get()), null,
                            reagent -> {
                                Herb herb = Herb.of(reagent);
                                ItemStack essence = new ItemStack(AlchemyItems.HERB_ESSENCE.get());
                                return herb == null ? essence : herb.mark(essence);
                            }, 3, false, new ItemStack(AlchemyItems.HERB_ESSENCE.get())));
        }
        return steeps;
    }

    /** Какая алхимическая основа это (склянка, сосуд с ней — смотрят по содержимому); null — не основа. */
    @Nullable
    public static Base base(ItemStack stack) {
        if (stack.is(ModItems.SPIRIT.get())) {
            return Base.SPIRIT;
        }
        if (stack.is(ModItems.VINEGAR.get())) {
            return Base.VINEGAR;
        }
        return water(stack) ? Base.WATER : null;
    }

    /** Склянка воды (ванильная бутылка с водой). */
    public static boolean water(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(Potions.WATER);
    }

    /** Склянка воды — так вода лежит в бочке. */
    public static ItemStack waterBottle() {
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
    }

    /** Реагент хоть к одному настою. */
    public static boolean reagent(ItemStack stack) {
        return !stack.isEmpty() && steeps().stream().anyMatch(s -> s.reagent().test(stack));
    }

    /** Настой для этой основы, реагента и добавок; null — так не выйдет. */
    @Nullable
    public static Steep steep(@Nullable Base base, ItemStack reagent, List<String> flavours) {
        if (base == null || reagent.isEmpty()) {
            return null;
        }
        for (Steep s : steeps()) {
            if (s.base() == base && s.reagent().test(reagent) && (s.flavour() == null || flavours.contains(s.flavour()))) {
                return s;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- снадобья

    /** Снадобья: что делает глоток. */
    public enum Remedy {
        WOUND_BALM, SULFUR_BALM, THERIAC, HERB_ESSENCE;

        public void apply(LivingEntity drinker, ItemStack stack) {
            switch (this) {
                case WOUND_BALM -> {
                    drinker.heal(4);
                    drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEDIUM, 0));
                }
                case SULFUR_BALM -> drinker.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LONG, 0));
                case THERIAC -> {
                    drinker.removeEffect(MobEffects.POISON);
                    drinker.removeEffect(MobEffects.WITHER);
                    drinker.removeEffect(MobEffects.CONFUSION);
                    drinker.removeEffect(MobEffects.HUNGER);
                    drinker.removeEffect(ModEffects.POISONING);
                    drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SHORT, 0));
                }
                case HERB_ESSENCE -> {
                    Herb herb = Herb.of(stack);
                    if (herb != null) {
                        herb.steep(drinker, ESSENCE_FACTOR);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- сулема на клинке

    /** Чем бьют: вещь по одной с уроном в ближнем бою (мечи, топоры, кинжалы, булавы…). */
    public static boolean blade(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() == 1 && stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
                .anyMatch(entry -> entry.attribute().value() == Attributes.ATTACK_DAMAGE.value());
    }

    public static int coat(ItemStack stack) {
        Integer hits = stack.get(ModComponents.POISON_COAT.get());
        return hits == null ? 0 : hits;
    }

    public static void setCoat(ItemStack stack, int hits) {
        if (hits <= 0) {
            stack.remove(ModComponents.POISON_COAT.get());
        } else {
            stack.set(ModComponents.POISON_COAT.get(), hits);
        }
    }

    // ---------------------------------------------------------------- сообщения

    public static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.alchemy." + key, args), true);
    }
}
