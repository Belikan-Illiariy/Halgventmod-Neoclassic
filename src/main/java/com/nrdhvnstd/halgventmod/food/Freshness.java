package com.nrdhvnstd.halgventmod.food;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.Dish;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Срок годности. Предмет запоминает игровые сутки, в которые появился в мире,
 * и портится с наступлением суток «появился + срок». Время внутри суток не важно:
 * хлеб, сделанный утром и вечером 20-х суток, испортится одновременно — в начале 27-х.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Freshness {
    // ===== Испорченная еда — как тухлятина. Меняйте здесь =====
    public static final float SPOILED_HUNGER_CHANCE = 0.8F; // шанс «Голода»
    public static final int SPOILED_HUNGER_SECONDS = 30;
    public static final float SPOILED_NAUSEA_CHANCE = 0.3F; // шанс «Тошноты»
    public static final int SPOILED_NAUSEA_SECONDS = 10;

    private static final int SCAN_INTERVAL = 20; // проверка инвентаря раз в секунду

    private Freshness() {}

    /** Номер текущего дня по календарю мода (не откатывается от /time set). */
    public static int currentDay(Level level) {
        return (int) Calendar.today(level);
    }

    /** Сколько суток осталось; 0 и меньше — испорчено; null — не портится или ещё не помечено. */
    @Nullable
    public static Integer daysLeft(ItemStack stack, Level level) {
        ModFoods.FoodInfo info = ModFoods.get(stack);
        Integer created = stack.get(ModComponents.CREATED_DAY.get());
        if (info == null || info.shelfLifeDays() <= 0 || created == null) {
            return null;
        }
        return created + info.shelfLifeDays() - currentDay(level);
    }

    public static boolean isSpoiled(ItemStack stack, Level level) {
        Integer left = daysLeft(stack, level);
        return left != null && left <= 0;
    }

    /** Поставить отметку о сутках появления, если её ещё нет. */
    public static void stamp(ItemStack stack, Level level) {
        if (!stack.isEmpty() && !stack.has(ModComponents.CREATED_DAY.get()) && ModFoods.perishable(stack)) {
            stack.set(ModComponents.CREATED_DAY.get(), currentDay(level));
        }
    }

    // Скрафтил, купил, поднял, взял из сундука — предмет попал в инвентарь и получает отметку
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % SCAN_INTERVAL != 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            stamp(inventory.getItem(i), player.level());
        }
        stamp(player.containerMenu.getCarried(), player.level());
    }

    // Выпало из моба, блока или сундука, выброшено — отметка ставится сразу, а не когда поднимут
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (ModFoods.perishable(stack) && !stack.has(ModComponents.CREATED_DAY.get())) {
            ItemStack stamped = stack.copy();
            stamp(stamped, event.getLevel());
            itemEntity.setItem(stamped);
        }
    }

    // Съел или выпил: жажда и последствия испорченного
    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack used = event.getItem(); // копия предмета до того, как его съели
        if (!used.has(DataComponents.FOOD)) {
            return;
        }

        // У блюда из котелка жажда своя (Dish), у остального — из описания еды
        ModFoods.FoodInfo info = ModFoods.get(used);
        Dish dish = used.get(ModComponents.DISH.get());
        int thirst = dish != null ? dish.thirst() : info != null ? info.thirst() : 0;
        if (thirst != 0) {
            Thirst.add(player, thirst);
        }

        if (isSpoiled(used, player.level())) {
            if (player.getRandom().nextFloat() < SPOILED_HUNGER_CHANCE) {
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, SPOILED_HUNGER_SECONDS * 20, 0));
            }
            if (player.getRandom().nextFloat() < SPOILED_NAUSEA_CHANCE) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, SPOILED_NAUSEA_SECONDS * 20, 0));
            }
        }
    }
}