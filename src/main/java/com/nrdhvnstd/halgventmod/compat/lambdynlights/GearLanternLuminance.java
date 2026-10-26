package com.nrdhvnstd.halgventmod.compat.lambdynlights;

import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.world.entity.Entity;

/**
 * Яркость игрока от снаряжения: фонарь на поясе. Снаряжение синхронизируется всем, кто видит игрока
 * (GearInventory.SYNC), поэтому чужой фонарь тоже светит. Под водой фонарь гаснет.
 * Предметы в руках (в том числе горящий светильник) светят сами — источниками света предметов (GearDynamicLights).
 */
public final class GearLanternLuminance implements EntityLuminance {
    public static final GearLanternLuminance INSTANCE = new GearLanternLuminance();
    private static final int LANTERN_LIGHT = 14; // чуть тусклее фонаря в руке (15) ⚠️

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
        GearInventory gear = entity.getExistingDataOrNull(ModAttachments.GEAR);
        if (gear == null || gear.get(GearSlot.LANTERN).isEmpty()) {
            return 0;
        }
        return LANTERN_LIGHT;
    }
}
