package com.nrdhvnstd.halgventmod.needs;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Боль 0–100 (ТЗ боя, 8). Шкалы на экране нет: виньетка, покачивание камеры и сердцебиение (client/PainEffects),
 * значение — в окне персонажа (P). Стадии накапливаются — выше стадия, всё, что было ниже, тоже действует:
 * <ul>
 *   <li>25–49 «Ноющая» — восстановление выносливости −15 %;</li>
 *   <li>50–69 «Сильная» — макс. выносливость −15, восстановление −30 %, удар медленнее на 10 %;</li>
 *   <li>70–89 «Мучительная» — бег нельзя, ходьба −10 %, макс. выносливость −30, виньетка;</li>
 *   <li>90–99 «Предел» — макс. выносливость −50, урон −20 %, размытие экрана;</li>
 *   <li>100 «Болевой шок» — 80 тиков движение −70 %, ни удара, ни блока; потом боль 85 и иммунитет к шоку 1200 тиков.</li>
 * </ul>
 * Растёт: урон по телу в бою (по типу) и отдача через металл (Combat.wound), падения, голод и жажда на нуле.
 * Спадает: вне боя 0.2/с, в бою 0.05/с (в бою — бил или по тебе били за последние IN_COMBAT тиков), во сне ×3.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Pain {
    // Меняйте здесь ⚠️
    public static final float MAX = 100;
    public static final float ACHING = 25, SEVERE = 50, AGONIZING = 70, LIMIT = 90;
    public static final int SHOCK_TICKS = 80;
    public static final float AFTER_SHOCK = 85;
    public static final int SHOCK_IMMUNITY = 1200;
    public static final int IN_COMBAT = 200;
    public static final float DECAY = 0.2F / 20;           // в тик, вне боя
    public static final float DECAY_IN_COMBAT = 0.05F / 20;
    public static final float SLEEP_DECAY = 3.0F;
    public static final float STARVING = 0.05F / 20;       // за голод и за жажду на нуле, в тик
    public static final float FALL_PAIN = 1.0F;            // за единицу урона от падения
    public static final double AGONY_MOVE = -0.1;
    public static final double SHOCK_MOVE = -0.7;

    private static final ResourceLocation MOVE_ID = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "pain_move");

    private Pain() {}

    public static float get(Player player) {
        return player.getData(ModAttachments.PAIN).level();
    }

    public static boolean shocked(Player player) {
        return player.getData(ModAttachments.PAIN).shockTicks() > 0;
    }

    /** Множитель восстановления выносливости. */
    public static float regenFactor(Player player) {
        float pain = get(player);
        return pain >= SEVERE ? 0.7F : pain >= ACHING ? 0.85F : 1.0F;
    }

    /** На сколько меньше помещается выносливости. */
    public static float staminaPenalty(Player player) {
        float pain = get(player);
        return pain >= LIMIT ? 50 : pain >= AGONIZING ? 30 : pain >= SEVERE ? 15 : 0;
    }

    /** Множитель длительности фаз удара. */
    public static float attackSlow(Player player) {
        return get(player) >= SEVERE ? 1.10F : 1.0F;
    }

    /** Множитель урона удара. */
    public static float damageFactor(Player player) {
        return get(player) >= LIMIT ? 0.8F : 1.0F;
    }

    public static boolean noSprint(Player player) {
        return get(player) >= AGONIZING || shocked(player);
    }

    /** Прибавить боль (только сервер). Дошла до 100 без иммунитета — болевой шок. */
    public static void add(Player player, float amount) {
        if (player.level().isClientSide || amount <= 0 || player.getAbilities().invulnerable) {
            return;
        }
        PainData data = player.getData(ModAttachments.PAIN);
        data.level = Math.min(MAX, data.level + amount);
        if (data.level >= MAX && data.shockTicks <= 0 && player.level().getGameTime() >= data.immuneUntil) {
            data.shockTicks = SHOCK_TICKS;
            player.setSprinting(false);
            Combat.interrupt(player);
            Combat.debug(player, Component.translatable("combat.halgventmod.debug.shock"));
        }
    }

    /** Был в бою: сам бил или получил удар — боль спадает медленнее. */
    public static void markCombat(Player player) {
        player.getData(ModAttachments.PAIN).lastCombat = player.level().getGameTime();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PainData data = player.getData(ModAttachments.PAIN);
        long now = player.level().getGameTime();
        if (data.shockTicks > 0 && --data.shockTicks == 0) {
            data.level = AFTER_SHOCK;
            data.immuneUntil = now + SHOCK_IMMUNITY;
        }
        if (Hunger.get(player) <= 0) {
            data.level = Math.min(MAX, data.level + STARVING);
        }
        if (Thirst.get(player) <= 0) {
            data.level = Math.min(MAX, data.level + STARVING);
        }
        if (data.shockTicks <= 0 && data.level > 0) {
            float decay = now - data.lastCombat < IN_COMBAT ? DECAY_IN_COMBAT : DECAY;
            data.level = Math.max(0, data.level - decay * (player.isSleeping() ? SLEEP_DECAY : 1.0F));
        }
        if (noSprint(player)) {
            player.setSprinting(false);
        }
        int penalty = (int) Math.round(-100 * (data.shockTicks > 0 ? SHOCK_MOVE : data.level >= AGONIZING ? AGONY_MOVE : 0));
        if (penalty != data.movePenalty) {
            data.movePenalty = penalty;
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                if (penalty == 0) {
                    speed.removeModifier(MOVE_ID);
                } else {
                    speed.addOrUpdateTransientModifier(new AttributeModifier(MOVE_ID, -penalty / 100.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }
    }

    // Падение — больно
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().is(DamageTypeTags.IS_FALL)) {
            add(player, event.getNewDamage() * FALL_PAIN);
        }
    }
}
