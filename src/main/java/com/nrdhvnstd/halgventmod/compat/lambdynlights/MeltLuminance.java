package com.nrdhvnstd.halgventmod.compat.lambdynlights;

import com.nrdhvnstd.halgventmod.smithing.Crucible;

import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Тигель с ещё жидким расплавом светит раскалённым металлом — в руке у игрока и брошенный на землю.
 * Свет зависит от времени (расплав застывает), поэтому это свет сущности, а не предмета: предикатом
 * предмета время не проверить. Под водой не гаснет — металл остывает не сразу.
 */
public final class MeltLuminance implements EntityLuminance {
    public static final MeltLuminance INSTANCE = new MeltLuminance();
    private static final int MELT_LIGHT = 12; // ⚠️

    private MeltLuminance() {}

    @Override
    public Type type() {
        return GearDynamicLights.MELT;
    }

    @Override
    public int getLuminance(ItemLightSourceManager itemLightSourceManager, Entity entity) {
        if (entity instanceof ItemEntity item) {
            return liquid(item.getItem(), entity) ? MELT_LIGHT : 0;
        }
        if (entity instanceof LivingEntity living
                && (liquid(living.getMainHandItem(), entity) || liquid(living.getOffhandItem(), entity))) {
            return MELT_LIGHT;
        }
        return 0;
    }

    private static boolean liquid(ItemStack stack, Entity entity) {
        return Crucible.is(stack) && Crucible.liquid(stack, entity.level());
    }
}
