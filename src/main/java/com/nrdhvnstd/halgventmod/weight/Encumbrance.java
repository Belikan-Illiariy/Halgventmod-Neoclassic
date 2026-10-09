package com.nrdhvnstd.halgventmod.weight;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModAttributes;
import com.nrdhvnstd.halgventmod.registry.ModEffects;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Перегруз: ступени, штрафы и их применение к игроку. */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Encumbrance {
    // ===== Правила — меняйте здесь =====
    public static final double BASE_CAPACITY_KG = 40;   // базовая грузоподъёмность игрока

    // Пороги ступеней в долях от грузоподъёмности: при 40 кг это 40, 60 и 80 кг
    public static final double ENCUMBERED_RATIO = 1.5;
    public static final double OVERLOADED_RATIO = 2.0;

    // Штраф к скорости, урону и голоду за каждый полный кг сверх грузоподъёмности
    public static final float RATE_BURDEN = 0.015F;     // 40–60 кг: +1,5% за кг
    public static final float RATE_ENCUMBERED = 0.02F;  // 60–80 кг: +2%
    public static final float RATE_OVERLOADED = 0.025F; // после 80 кг: +2,5% (при 92 кг игрок встаёт)

    private static final int CHECK_INTERVAL = 10; // пересчёт раз в полсекунды
    private static final ResourceLocation SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "overweight");

    private Encumbrance() {}

    public enum Tier { NONE, BURDEN, ENCUMBERED, OVERLOADED }

    public static Tier tier(double kg, double capacity) {
        if (kg >= capacity * OVERLOADED_RATIO) {
            return Tier.OVERLOADED;
        }
        if (kg >= capacity * ENCUMBERED_RATIO) {
            return Tier.ENCUMBERED;
        }
        if (kg > capacity) {
            return Tier.BURDEN;
        }
        return Tier.NONE;
    }

    /** Штраф в долях: 0.3 = 30%. */
    public static float penalty(double kg, double capacity) {
        double over = Math.floor(kg - capacity); // полные кг сверх грузоподъёмности
        if (over <= 0) {
            return 0.0F;
        }
        double band1 = capacity * (ENCUMBERED_RATIO - 1);                // 20 кг при 40
        double band2 = capacity * (OVERLOADED_RATIO - ENCUMBERED_RATIO); // 20 кг при 40
        double first = Math.min(over, band1);
        double second = Math.min(Math.max(over - band1, 0), band2);
        double third = Math.max(over - band1 - band2, 0);
        return (float) (first * RATE_BURDEN + second * RATE_ENCUMBERED + third * RATE_OVERLOADED);
    }

    public static double capacity(Player player) {
        return player.getAttributeValue(ModAttributes.CARRY_CAPACITY);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        Tier tier = Tier.NONE;
        float penalty = 0.0F;
        if (!player.isCreative() && !player.isSpectator()) {
            double kg = ModWeights.inventoryGrams(player) / 1000.0;
            double capacity = capacity(player);
            tier = tier(kg, capacity);
            penalty = penalty(kg, capacity);
        }

        player.setData(ModAttachments.OVERWEIGHT_PENALTY, penalty); // для урона и голода
        updateSpeed(player, penalty);
        setEffect(player, ModEffects.BURDEN, tier == Tier.BURDEN);
        setEffect(player, ModEffects.ENCUMBERED, tier == Tier.ENCUMBERED);
        setEffect(player, ModEffects.OVERLOADED, tier == Tier.OVERLOADED);
    }

    // Теги сменились (вход в мир, /reload) — веса по тегам могли стать другими
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ModWeights.clearCache();
    }

    // Урон игрока — и в ближнем бою, и стрелами — уменьшается на штраф
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker) {
            float penalty = attacker.getData(ModAttachments.OVERWEIGHT_PENALTY);
            if (penalty > 0) {
                event.setAmount(event.getAmount() * (1.0F - Math.min(penalty, 1.0F)));
            }
        }
    }

    private static void updateSpeed(Player player, float penalty) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double amount = -Math.min(penalty, 1.0F);
        AttributeModifier current = speed.getModifier(SPEED_MODIFIER);
        if (current != null && current.amount() == amount) {
            return; // не изменилось — не трогаем, чтобы не гонять синхронизацию
        }
        speed.removeModifier(SPEED_MODIFIER);
        if (amount < 0) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void setEffect(Player player, Holder<MobEffect> effect, boolean active) {
        boolean has = player.hasEffect(effect);
        if (active && !has) {
            // Бесконечный, без частиц, со значком
            player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        } else if (!active && has) {
            player.removeEffect(effect);
        }
    }
}