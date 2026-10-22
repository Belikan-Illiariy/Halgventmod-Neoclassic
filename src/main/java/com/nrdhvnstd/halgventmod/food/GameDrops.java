package com.nrdhvnstd.halgventmod.food;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Дичь: горный козёл даёт оленину (горящий — сразу жареную). Гусь — свой зверь (GooseEntity), добыча — его таблица. */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class GameDrops {
    // Меняйте здесь
    public static final int VENISON_MIN = 1;
    public static final int VENISON_MAX = 2;

    private GameDrops() {}

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Goat goat) || goat.isBaby() || entity.level().isClientSide) {
            return;
        }
        int count = VENISON_MIN + entity.getRandom().nextInt(VENISON_MAX - VENISON_MIN + 1);
        ItemStack meat = new ItemStack(entity.isOnFire() ? ModItems.COOKED_VENISON.get() : ModItems.VENISON.get(), count);
        event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(), meat));
    }
}
