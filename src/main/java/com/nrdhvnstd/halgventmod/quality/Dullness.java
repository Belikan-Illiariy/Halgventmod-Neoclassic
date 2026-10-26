package com.nrdhvnstd.halgventmod.quality;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Затупление оружия ближнего боя. Каждые MIN_HITS–MAX_HITS ударов (сколько именно — бросок, свой у каждого раза)
 * оружие получает признак «Затуплен» (ModTraits.DULL): урон ×DAMAGE_FACTOR (QualityEvents — в атрибутах, видно
 * в подсказке). Пока затуплено, удары не считаются. Заточить: на верстаке оружие + точильный камень (SharpenRecipe) —
 * оружие теряет SHARPEN_COST от наибольшей прочности; подточка на точиле (SharpeningWheelBlock) тоже снимает затупление.
 * Счётчик — компонент halgventmod:hits_to_dull: сколько ударов осталось; нет его — бросок при первом ударе.
 * Луки и арбалеты не тупятся.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Dullness {
    // Меняйте здесь ⚠️
    public static final int MIN_HITS = 100;
    public static final int MAX_HITS = 150;
    public static final float DAMAGE_FACTOR = 0.75F;
    public static final float SHARPEN_COST = 0.05F; // доля наибольшей прочности за заточку

    private Dullness() {}

    /** Тупится ли эта вещь: оружие ближнего боя с прочностью. */
    public static boolean applies(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && GearQuality.isWeapon(stack)
                && !(stack.getItem() instanceof BowItem) && !(stack.getItem() instanceof CrossbowItem);
    }

    public static boolean isDull(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TRAITS.get(), List.of()).contains(ModTraits.DULL.id());
    }

    /** Множитель урона: затупленное — DAMAGE_FACTOR, иначе 1. */
    public static float damageFactor(ItemStack stack) {
        return applies(stack) && isDull(stack) ? DAMAGE_FACTOR : 1.0F;
    }

    /** Прочность, которую снимает заточка. */
    public static int sharpenCost(ItemStack stack) {
        return Math.max(1, Mth.ceil(stack.getMaxDamage() * SHARPEN_COST));
    }

    /** Снять затупление; счётчик — заново при следующем ударе. Прочность не трогает. */
    public static void sharpen(ItemStack stack) {
        List<String> traits = stack.get(ModComponents.TRAITS.get());
        if (traits != null && traits.contains(ModTraits.DULL.id())) {
            List<String> cleaned = new ArrayList<>(traits);
            cleaned.remove(ModTraits.DULL.id());
            stack.set(ModComponents.TRAITS.get(), List.copyOf(cleaned));
        }
        stack.remove(ModComponents.HITS_TO_DULL.get());
    }

    private static void makeDull(ItemStack stack) {
        List<String> traits = new ArrayList<>(stack.getOrDefault(ModComponents.TRAITS.get(), List.of()));
        if (!traits.contains(ModTraits.DULL.id())) {
            traits.add(ModTraits.DULL.id());
        }
        stack.set(ModComponents.TRAITS.get(), List.copyOf(traits));
        stack.remove(ModComponents.HITS_TO_DULL.get());
    }

    private static int roll(RandomSource random) {
        return Mth.nextInt(random, MIN_HITS, MAX_HITS);
    }

    // Удар оружием в руке (не стрелой) по живому, прошедший: минус один до затупления
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (event.getEntity().level().isClientSide || event.getNewDamage() <= 0
                || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!applies(weapon) || isDull(weapon)) {
            return;
        }
        Integer left = weapon.get(ModComponents.HITS_TO_DULL.get());
        int remaining = (left == null ? roll(attacker.getRandom()) : left) - 1;
        if (remaining <= 0) {
            makeDull(weapon);
        } else {
            weapon.set(ModComponents.HITS_TO_DULL.get(), remaining);
        }
    }
}
