package com.nrdhvnstd.halgventmod.farming;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Фермерство в режиме приключения:
 * серп и коса ломают растения (компонент can_break), семена, зерно и овощи сажаются ПКМ по пашне (can_place_on),
 * пашня не вытаптывается, если выключено правило farmlandTrampling.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class FarmingEvents {
    // Меняйте здесь
    public static final float RYE_SEED_CHANCE = 0.08F;
    public static final float WILD_CROP_CHANCE = 0.06F; // дикий ячмень, овёс, лук, чеснок, репа, фасоль, капуста

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
                ModItems.TURNIP, ModItems.BEANS, ModItems.CABBAGE)) {
            event.modify(seeds, builder ->
                    builder.set(DataComponents.CAN_PLACE_ON, FarmingRules.adventurePredicate(FarmingRules.PLANTABLE_SOIL)));
        }
    }


    // Семена ржи — с травы, как ванильные семена пшеницы (любым способом, кроме взрыва); там же реже — дикие
    // ячмень и овёс, лук, чеснок, репа, фасоль, капуста (их сажают самих)
    @SubscribeEvent
    public static void onGrassDrops(BlockDropsEvent event) {
        boolean grass = event.getState().is(Blocks.SHORT_GRASS) || event.getState().is(Blocks.TALL_GRASS)
                || event.getState().is(Blocks.FERN);
        if (!grass || !(event.getBreaker() instanceof Player)) {
            return;
        }
        RandomSource random = event.getLevel().random;
        ItemStack found = ItemStack.EMPTY;
        if (random.nextFloat() < RYE_SEED_CHANCE) {
            found = new ItemStack(ModItems.RYE_SEEDS.get());
        } else if (random.nextFloat() < WILD_CROP_CHANCE) {
            List<? extends net.neoforged.neoforge.registries.DeferredItem<?>> wild = List.of(ModItems.BARLEY, ModItems.OATS,
                    ModItems.ONION, ModItems.GARLIC, ModItems.TURNIP, ModItems.BEANS, ModItems.CABBAGE);
            found = new ItemStack(wild.get(random.nextInt(wild.size())).get());
        }
        if (!found.isEmpty()) {
            BlockPos pos = event.getPos();
            event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, found));
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof Level level && !FarmingRules.trampling(level)) {
            event.setCanceled(true);
        }
    }
}
