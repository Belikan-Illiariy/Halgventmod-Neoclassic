package com.nrdhvnstd.halgventmod.compat.lambdynlights;

import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.pottery.WareItem;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Яркость игрока от снаряжения: фонарь на поясе или горящий глиняный светильник в руке. Снаряжение синхронизируется всем, кто видит игрока
 * (GearInventory.SYNC), поэтому чужой фонарь тоже светит. Под водой фонарь гаснет.
 */
public final class GearLanternLuminance implements EntityLuminance {
    public static final GearLanternLuminance INSTANCE = new GearLanternLuminance();
    private static final int LANTERN_LIGHT = 14; // чуть тусклее фонаря в руке (15) ⚠️
    private static final int LAMP_LIGHT = 11;    // глиняный светильник — огонёк фитиля ⚠️

    private GearLanternLuminance() {}

    @Override
    public Type type() {
        return GearDynamicLights.GEAR_LANTERN;
    }

    @Override
    public int getLuminance(ItemLightSourceManager itemLightSourceManager, Entity entity) {
        if (entity.isUnderWater()) {
            return 0;
        }
        // Горящий глиняный светильник в руке
        if (entity instanceof LivingEntity living
                && (WareItem.lit(living.getMainHandItem()) || WareItem.lit(living.getOffhandItem()))) {
            return LAMP_LIGHT;
        }
        GearInventory gear = entity.getExistingDataOrNull(ModAttachments.GEAR);
        if (gear == null || gear.get(GearSlot.LANTERN).isEmpty()) {
            return 0;
        }
        return LANTERN_LIGHT;
    }
}
