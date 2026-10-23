package com.nrdhvnstd.halgventmod.farming;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Фермерство в режиме приключения:
 * серп и коса ломают растения (компонент can_break), семена, зерно и овощи сажаются ПКМ по пашне (can_place_on),
 * пашня не вытаптывается, если выключено правило farmlandTrampling.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class FarmingEvents {
    private FarmingEvents() {}

    public static void register(IEventBus modEventBus) {
        FarmingRules.init();
        modEventBus.addListener(FarmingEvents::setAdventureComponents);
    }

    private static void setAdventureComponents(ModifyDefaultComponentsEvent event) {
        for (var tool : List.of(ModItems.SICKLE, ModItems.SCYTHE)) {
            event.modify(tool, builder ->
                    builder.set(DataComponents.CAN_BREAK, FarmingRules.adventurePredicate(FarmingRules.HARVESTABLE)));
        }
        for (var seeds : FarmingRules.VANILLA_SEEDS) {
            event.modify(seeds, builder ->
                    builder.set(DataComponents.CAN_PLACE_ON, FarmingRules.adventurePredicate(FarmingRules.PLANTABLE_SOIL)));
        }
        for (var seeds : List.of(ModItems.RYE_SEEDS, ModItems.BARLEY, ModItems.OATS, ModItems.ONION, ModItems.GARLIC,
                ModItems.TURNIP, ModItems.BEANS, ModItems.CABBAGE, ModItems.FLAX_SEEDS)) {
            event.modify(seeds, builder ->
                    builder.set(DataComponents.CAN_PLACE_ON, FarmingRules.adventurePredicate(FarmingRules.PLANTABLE_SOIL)));
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof Level level && !FarmingRules.trampling(level)) {
            event.setCanceled(true);
        }
    }
}
