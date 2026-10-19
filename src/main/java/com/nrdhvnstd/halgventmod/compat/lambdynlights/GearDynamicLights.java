package com.nrdhvnstd.halgventmod.compat.lambdynlights;

import com.nrdhvnstd.halgventmod.HalgventMod;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Совместимость с LambDynamicLights (необязательный клиентский мод, концепт инвентаря, этап 7).
 * Точка входа прописана в neoforge.mods.toml (modproperties → yumi:entrypoints); без мода класс не загружается.
 *
 * Игрок светит, пока на поясе висит фонарь (GearLanternLuminance). Факел и фонарь в руке
 * LambDynamicLights подсвечивает сам — по яркости блока. Настройка «все / свой / выкл.» — в его меню.
 */
public final class GearDynamicLights implements DynamicLightsInitializer {
    static final EntityLuminance.Type GEAR_LANTERN = EntityLuminance.Type.registerSimple(
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "gear_lantern"), GearLanternLuminance.INSTANCE);

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        HalgventMod.LOGGER.info("LambDynamicLights: поясной фонарь светит");
        context.entityLightSourceManager().onRegisterEvent()
                .register(registry -> registry.register(EntityType.PLAYER, GearLanternLuminance.INSTANCE));
    }

    // Устаревший вход старого API; LambDynamicLights 4.x зовёт вариант с контекстом
    @SuppressWarnings("removal")
    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {
    }
}
