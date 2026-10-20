package com.nrdhvnstd.halgventmod.quality;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.sound.SoundTags;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Качество снаряжения — признак вроде редкости, но про то, как вещь сделана: Ужасный, Плохой, Посредственный,
 * Добротный, Отменный, Шедевр. Склоняется по роду предмета («Добротная кираса», «Добротный меч»), «Шедевр» —
 * нет. Хранится компонентом halgventmod:gear_quality (0–5); вещь без него считается Посредственной.
 *
 * <p>Только у оружия и брони. Что меняет — таблица {@link Grade}: урон оружия, защиту брони, пробитие (доля брони
 * цели, которую игнорирует удар этим оружием — своего типа урона, {@link DamageKind}) и стойкость брони к типам урона.
 *
 * <p>Откуда берётся: при крафте — бросок по весам {@link Grade#weight}; со стола кожевника — из качества кожи и шитья;
 * остальное (добыча, творческий, чужие сундуки) при попадании в инвентарь — Посредственное. Администратор: /quality.
 */
public final class GearQuality {
    /** Ступени качества. Меняйте здесь ⚠️ */
    public enum Grade {
        //            признак               урон   защита  пробитие  стойкость  вес броска
        TERRIBLE  (ModTraits.TERRIBLE,   0.70F, 0.70F,  0.00F,    0.50F,     5),
        POOR      (ModTraits.POOR,       0.85F, 0.85F,  0.00F,    0.75F,     20),
        MEDIOCRE  (ModTraits.MEDIOCRE,   1.00F, 1.00F,  0.05F,    1.00F,     45),
        FINE      (ModTraits.FINE,       1.10F, 1.10F,  0.10F,    1.15F,     20),
        EXCELLENT (ModTraits.EXCELLENT,  1.20F, 1.20F,  0.15F,    1.30F,     8),
        MASTERWORK(ModTraits.MASTERWORK, 1.35F, 1.35F,  0.25F,    1.50F,     2);

        private final ModTraits.Trait trait;
        private final float damage;
        private final float protection;
        private final float penetration;
        private final float resistance;
        private final int weight;

        Grade(ModTraits.Trait trait, float damage, float protection, float penetration, float resistance, int weight) {
            this.trait = trait;
            this.damage = damage;
            this.protection = protection;
            this.penetration = penetration;
            this.resistance = resistance;
            this.weight = weight;
        }

        public ModTraits.Trait trait() {
            return trait;
        }

        /** Множитель урона оружия. */
        public float damage() {
            return damage;
        }

        /** Множитель защиты (броня и твёрдость) вещи брони. */
        public float protection() {
            return protection;
        }

        /** Доля брони цели, которую игнорирует удар оружием своего типа урона. */
        public float penetration() {
            return penetration;
        }

        /** Множитель стойкости брони к типам урона (DamageKind.resistance). */
        public float resistance() {
            return resistance;
        }

        public int weight() {
            return weight;
        }

        public String id() {
            return trait.id();
        }

        @Nullable
        public static Grade byId(String id) {
            for (Grade grade : values()) {
                if (grade.id().equals(id)) {
                    return grade;
                }
            }
            return null;
        }
    }

    private static final List<String> QUALITY_TRAITS = new ArrayList<>();
    static {
        for (Grade grade : Grade.values()) {
            QUALITY_TRAITS.add(grade.id());
        }
    }

    private GearQuality() {}

    /** Оружие: вещь мода из «Оружия» или ванильное и чужое оружие (меч, топор с тегом, трезубец, булава, лук, арбалет). */
    public static boolean isWeapon(ItemStack stack) {
        ModItems.Definition def = ModItems.definition(stack.getItem());
        if (def != null && def.category() == Category.WEAPONS) {
            return true;
        }
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof MaceItem || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem || stack.is(SoundTags.BLADE) || stack.is(SoundTags.POINT);
    }

    /** Броня: вещь брони мода (ModArmor) или ванильная броня. */
    public static boolean isArmor(ItemStack stack) {
        return ModArmor.get(stack) != null || stack.getItem() instanceof ArmorItem;
    }

    /** Бывает ли у вещи качество. */
    public static boolean applies(ItemStack stack) {
        return !stack.isEmpty() && (isWeapon(stack) || isArmor(stack));
    }

    public static boolean has(ItemStack stack) {
        return stack.has(ModComponents.GEAR_QUALITY.get());
    }

    /** Качество вещи; без компонента — Посредственное. */
    public static Grade get(ItemStack stack) {
        Integer value = stack.get(ModComponents.GEAR_QUALITY.get());
        return value == null ? Grade.MEDIOCRE : Grade.values()[Math.clamp(value, 0, Grade.values().length - 1)];
    }

    /** Задать качество: компонент и признак (прочие признаки, например редкость, остаются). */
    public static ItemStack set(ItemStack stack, Grade grade) {
        stack.set(ModComponents.GEAR_QUALITY.get(), grade.ordinal());
        List<String> traits = new ArrayList<>(stack.getOrDefault(ModComponents.TRAITS.get(), List.of()));
        traits.removeIf(QUALITY_TRAITS::contains);
        traits.add(grade.id());
        stack.set(ModComponents.TRAITS.get(), List.copyOf(traits));
        return stack;
    }

    /** Бросок качества по весам (крафт). */
    public static Grade roll(RandomSource random) {
        int total = 0;
        for (Grade grade : Grade.values()) {
            total += grade.weight();
        }
        int pick = random.nextInt(total);
        for (Grade grade : Grade.values()) {
            pick -= grade.weight();
            if (pick < 0) {
                return grade;
            }
        }
        return Grade.MEDIOCRE;
    }

    /** Качество кожаной вещи со стола кожевника: качество кожи 0–4 (с поправкой за шитьё) → Плохой…Шедевр. */
    public static Grade fromLeather(int leatherQuality) {
        return Grade.values()[Math.clamp(leatherQuality + 1, 1, Grade.values().length - 1)];
    }
}
