package com.nrdhvnstd.halgventmod.food;

import java.util.HashMap;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.CookingEvents;
import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.registry.ModEffects;
import com.nrdhvnstd.halgventmod.world.DayLength;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Эффекты еды (таблица баланса):
 * <ul>
 *   <li><b>Сытость</b> — жареное мясо и рыба, каши: голод не убывает, здоровье восстанавливается ×{@link #FED_REGEN};</li>
 *   <li><b>Улучшенное насыщение</b> — горячие сложные блюда (похлёбки, блюда с травами, ягодный пирог): как «Сытость»,
 *       плюс выносливость восстанавливается ×{@link #WELL_FED_STAMINA_REGEN} и её на {@link #WELL_FED_STAMINA_MAX} больше.
 *       Заменяет «Сытость»; «Сытость» его не сбивает;</li>
 *   <li><b>Отравление</b> — сырое мясо и рыба (тег halgventmod:raw_food, недожаренное) и испорченное: с шансом
 *       {@link #POISON_CHANCE} на полсуток, еда даёт вдвое меньше сытости.</li>
 * </ul>
 * Какое блюдо что даёт — {@link #satiety}, {@link #wellFed} ниже; для своих предметов — .satiety(...) и .wellFed(...)
 * в ModItems.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class FoodEffects {
    // Меняйте здесь
    public static final float FED_REGEN = 1.5F;
    public static final float WELL_FED_STAMINA_REGEN = 1.15F;
    public static final int WELL_FED_STAMINA_MAX = 10;
    public static final float POISON_CHANCE = 0.5F;
    public static final float POISON_NUTRITION = 0.5F;
    public static final int POISON_TICKS = DayLength.DAY_LENGTH_TICKS / 2; // полсуток

    /** Сколько секунд «Сытости» или «Улучшенного насыщения» даёт еда; отрицательное — улучшенное. */
    private static final Map<ResourceLocation, Integer> BY_ID = new HashMap<>();

    static {
        // Ванильное жареное — «Сытость» на минуту
        satiety(Items.COOKED_PORKCHOP, 60);
        satiety(Items.COOKED_BEEF, 60);
        satiety(Items.COOKED_CHICKEN, 60);
        satiety(Items.COOKED_RABBIT, 60);
        satiety(Items.COOKED_MUTTON, 60);
        satiety(Items.COOKED_SALMON, 60);
    }

    private FoodEffects() {}

    public static void satiety(ItemLike item, int seconds) {
        put(BuiltInRegistries.ITEM.getKey(item.asItem()), seconds);
    }

    /** Для ModItems: «Сытость» (seconds > 0) или «Улучшенное насыщение» (wellFed). */
    public static void put(ResourceLocation id, int seconds) {
        BY_ID.put(id, seconds);
    }

    public static void wellFed(ResourceLocation id, int seconds) {
        BY_ID.put(id, -seconds);
    }

    public static boolean fed(LivingEntity entity) {
        return entity.hasEffect(ModEffects.SATIETY) || entity.hasEffect(ModEffects.WELL_FED);
    }

    public static boolean wellFed(LivingEntity entity) {
        return entity.hasEffect(ModEffects.WELL_FED);
    }

    public static boolean poisoned(LivingEntity entity) {
        return entity.hasEffect(ModEffects.POISONING);
    }

    /** Секунды эффекта у еды: > 0 — «Сытость», < 0 — «Улучшенное насыщение», 0 — ничего. */
    public static int of(ItemStack stack) {
        return BY_ID.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), 0);
    }

    /** С шансом — «Отравление» (сырое, недожаренное, испорченное). */
    public static void maybePoison(Player player) {
        if (player.getRandom().nextFloat() < POISON_CHANCE) {
            player.addEffect(new MobEffectInstance(ModEffects.POISONING, POISON_TICKS, 0));
            player.displayClientMessage(Component.translatable("message.halgventmod.cooking.poisoned"), true);
        }
    }

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack used = event.getItem();
        if (used.is(CookingEvents.RAW_FOOD) || FoodQuality.isMarkedRaw(used) || Freshness.isSpoiled(used, player.level())) {
            maybePoison(player);
        }
        int seconds = of(used);
        if (seconds > 0 && !wellFed(player)) {
            give(player, ModEffects.SATIETY, seconds);
        } else if (seconds < 0) {
            player.removeEffect(ModEffects.SATIETY);
            give(player, ModEffects.WELL_FED, -seconds);
        }
    }

    // Не сокращать: съел второе такое же блюдо раньше — время берётся большее
    private static void give(Player player, Holder<MobEffect> effect, int seconds) {
        MobEffectInstance current = player.getEffect(effect);
        int ticks = seconds * 20;
        if (current == null || current.getDuration() < ticks) {
            player.addEffect(new MobEffectInstance(effect, ticks, 0, false, true, true));
        }
    }
}
