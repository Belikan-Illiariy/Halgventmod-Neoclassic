package com.nrdhvnstd.halgventmod.food;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.Diet;
import com.nrdhvnstd.halgventmod.cooking.Dish;
import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.world.DayLength;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Голод 0–100 вместо ванильного 0–20. Ванильный FoodData отключён (FoodDataMixin):
 * трата, лечение и урон от голода считаются здесь.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Hunger {
    // ===== Правила — меняйте здесь =====
    public static final int MAX = HungerData.MAX;

    // Ванильная еда: сытость и насыщение умножаются на это число (хлеб 5 → 25 из 100)
    public static final int VANILLA_SCALE = 5;

    // Пассивная трата, даже когда игрок стоит: единиц за игровые сутки. Длина суток — в DayLength.
    public static final float PASSIVE_PER_DAY = 40;

    // 0,8 единицы усталости = 1 единица голода: так голод тратится с той же скоростью, что в ванилле
    public static final float EXHAUSTION_PER_POINT = 0.8F;
    public static final int NO_SPRINT_AT = 30;          // при 30 и ниже бегать нельзя

    public static final int REGEN_AT = 90;              // с 90 и выше здоровье понемногу восстанавливается
    public static final int REGEN_INTERVAL = 80;        // +1 здоровья раз в 4 секунды
    public static final float REGEN_EXHAUSTION = 6.0F;  // и это стоит 7,5 единицы голода
    public static final int FAST_REGEN_INTERVAL = 10;   // при полном голоде и насыщении — быстро, как в ванилле

    public static final int DAMAGE_INTERVAL = 80;       // при пустом голоде урон раз в 4 секунды
    public static final float DAMAGE = 1.0F;

    private Hunger() {}

    public static int get(Player player) {
        return player.getData(ModAttachments.HUNGER).level();
    }

    public static float saturation(Player player) {
        return player.getData(ModAttachments.HUNGER).saturation();
    }

    public static boolean needs(Player player) {
        return get(player) < MAX;
    }

    /** Накормить: сытость и насыщение в единицах 0–100. Только на сервере. */
    public static void add(Player player, int nutrition, float saturation) {
        if (player.level().isClientSide) {
            return;
        }
        HungerData data = player.getData(ModAttachments.HUNGER);
        data.level = Mth.clamp(data.level + nutrition, 0, MAX);
        data.saturation = Mth.clamp(data.saturation + saturation, 0.0F, data.level);
    }

    /**
     * Съеден предмет. Свои значения из ModItems/ModFoods важнее ванильных, у блюда из котелка — свои (Dish).
     * Сверху — качество еды (FoodQuality) и разнообразие стола (Diet; питьё не считается).
     */
    public static void eat(Player player, ItemStack stack, FoodProperties food) {
        int nutrition;
        float saturation;
        Dish dish = stack.get(ModComponents.DISH.get());
        ModFoods.FoodInfo info = ModFoods.get(stack);
        if (dish != null) {
            nutrition = dish.nutrition();
            saturation = dish.saturation();
        } else if (info != null && info.nutrition() >= 0) {
            nutrition = info.nutrition();
            saturation = info.saturation();
        } else {
            nutrition = food.nutrition() * VANILLA_SCALE;
            saturation = food.saturation() * VANILLA_SCALE;
        }
        float quality = FoodQuality.factor(stack);
        nutrition = Math.round(nutrition * quality);
        saturation *= quality;
        if (!(stack.getItem() instanceof DrinkItem) && !player.level().isClientSide) {
            Diet.Effect diet = Diet.eat(player, stack);
            nutrition = Math.round(nutrition * diet.nutrition());
            saturation *= diet.saturation();
        }
        add(player, nutrition, saturation);
    }

    /** Вызывается из PlayerMixin на каждую трату голода (бег, прыжки, удары, урон). */
    public static void addExhaustion(Player player, float amount) {
        HungerData data = player.getData(ModAttachments.HUNGER);
        data.exhaustion = Math.min(data.exhaustion + amount, 40.0F);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        HungerData data = player.getData(ModAttachments.HUNGER);
        Difficulty difficulty = player.level().getDifficulty();
        boolean naturalRegen = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);

        // Пассивная трата раз в секунду, перегруз её увеличивает
        if (player.tickCount % 20 == 0 && !player.getAbilities().invulnerable) {
            float overweight = 1.0F + player.getData(ModAttachments.OVERWEIGHT_PENALTY);
            addExhaustion(player, perSecond(PASSIVE_PER_DAY) * overweight);
        }

        // Усталость съедает сначала насыщение, потом сытость
        if (data.exhaustion >= EXHAUSTION_PER_POINT) {
            data.exhaustion -= EXHAUSTION_PER_POINT;
            if (data.saturation > 0) {
                data.saturation = Math.max(data.saturation - 1.0F, 0.0F);
            } else if (difficulty != Difficulty.PEACEFUL) {
                data.level = Math.max(data.level - 1, 0);
            }
        }

        // На мирной сложности голод восстанавливается сам
        if (difficulty == Difficulty.PEACEFUL && naturalRegen && player.tickCount % 10 == 0 && data.level < MAX) {
            data.level = Math.min(data.level + VANILLA_SCALE, MAX);
        }

        // Лечение и урон — по ванильным правилам FoodData.tick
        if (naturalRegen && data.saturation > 0 && player.isHurt() && data.level >= MAX) {
            if (++data.timer >= FAST_REGEN_INTERVAL) {
                float amount = Math.min(data.saturation / VANILLA_SCALE, 6.0F);
                player.heal(amount / 6.0F);
                addExhaustion(player, amount);
                data.timer = 0;
            }
        } else if (naturalRegen && data.level >= REGEN_AT && player.isHurt()) {
            if (++data.timer >= REGEN_INTERVAL) {
                player.heal(1.0F);
                addExhaustion(player, REGEN_EXHAUSTION);
                data.timer = 0;
            }
        } else if (data.level <= 0) {
            if (++data.timer >= DAMAGE_INTERVAL) {
                if (canStarve(player)) {
                    player.hurt(player.damageSources().starve(), DAMAGE);
                }
                data.timer = 0;
            }
        } else {
            data.timer = 0;
        }
    }

    /**
     * Можно ли ранить голодом или жаждой: на лёгкой — до половины здоровья,
     * на нормальной — до 1, на сложной — насмерть. Как в ванилле.
     */
    static boolean canStarve(Player player) {
        Difficulty difficulty = player.level().getDifficulty();
        return player.getHealth() > player.getMaxHealth() / 2 || difficulty == Difficulty.HARD
                || (player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL);
    }

    // Единицы за сутки → усталость за секунду: 40 × 0,8 = 32 усталости на 10 800 секунд
    static float perSecond(float pointsPerDay) {
        return pointsPerDay * EXHAUSTION_PER_POINT / (DayLength.DAY_LENGTH_TICKS / 20.0F);
    }
}
