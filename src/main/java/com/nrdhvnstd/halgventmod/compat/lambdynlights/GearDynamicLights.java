package com.nrdhvnstd.halgventmod.compat.lambdynlights;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.WareItem;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSource;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import dev.lambdaurora.lambdynlights.api.item.ItemLuminance;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * Совместимость с LambDynamicLights (необязательный клиентский мод, концепт инвентаря, этап 7).
 * Точка входа прописана в neoforge.mods.toml (modproperties → yumi:entrypoints); без мода класс не загружается.
 *
 * Всё, что светит, освещает мир вокруг себя динамическим светом — в руке у игрока и у любого существа,
 * выброшенное на землю, в рамке:
 * - предметы-блоки (факел, фонарь, светокамень и т. п., в том числе из Conquest) — LambDynamicLights сам,
 *   по яркости блока в том виде, в каком он лежит в руке;
 * - горящий глиняный светильник — источник света предмета по компоненту «зажжён» (под водой гаснет);
 * - тигель с жидким расплавом — в руке у игрока и на земле (MeltLuminance: свет зависит от времени);
 * - игрок с фонарём на поясе (GearLanternLuminance).
 * Настройка «все / свой / выкл.» — в меню LambDynamicLights.
 */
public final class GearDynamicLights implements DynamicLightsInitializer {
    static final EntityLuminance.Type GEAR_LANTERN = EntityLuminance.Type.registerSimple(
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "gear_lantern"), GearLanternLuminance.INSTANCE);
    static final EntityLuminance.Type MELT = EntityLuminance.Type.registerSimple(
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "melt"), MeltLuminance.INSTANCE);
    private static final int LAMP_LIGHT = 11; // ⚠️ огонёк фитиля

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        HalgventMod.LOGGER.info("LambDynamicLights: поясной фонарь, светильник и тигель светят");
        context.entityLightSourceManager().onRegisterEvent()
                .register(registry -> {
                    registry.register(EntityType.PLAYER, GearLanternLuminance.INSTANCE, MeltLuminance.INSTANCE);
                    registry.register(EntityType.ITEM, MeltLuminance.INSTANCE);
                });
        // Светильники (все предметы-светильники) — только зажжённые
        context.itemLightSourceManager().onRegisterEvent().register(registry -> {
            List<Item> lamps = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof WareItem ware && ware.form() == VesselForm.LAMP) {
                    lamps.add(item);
                }
            }
            if (!lamps.isEmpty()) {
                ItemPredicate lit = ItemPredicate.Builder.item().of(lamps.toArray(Item[]::new))
                        .hasComponents(DataComponentPredicate.builder().expect(ModComponents.LAMP_LIT.get(), true).build())
                        .build();
                registry.register(new ItemLightSource(lit, ItemLuminance.of(LAMP_LIGHT), true));
            }
        });
    }

    // Устаревший вход старого API; LambDynamicLights 4.x зовёт вариант с контекстом
    @SuppressWarnings("removal")
    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {
    }
}
