package com.nrdhvnstd.halgventmod.cooking;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Готовка в мире:
 * <ul>
 *   <li>сырое мясо и рыба (тег halgventmod:raw_food) — «Отравление» (food/FoodEffects);</li>
 *   <li>травы больше не падают с травы — их срезают, ничего не ломая (herbs/HerbGathering);</li>
 *   <li>поставленный ванильный костёр становится костром мода (FirePitBlock): незажжённым, на дровах и огниве.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class CookingEvents {
    public static final TagKey<Item> RAW_FOOD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "raw_food"));

    private CookingEvents() {}

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(Blocks.CAMPFIRE) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        Direction facing = event.getPlacedBlock().getValue(CampfireBlock.FACING);
        // На следующем шаге: сейчас ещё идёт постановка ванильного блока
        level.getServer().execute(() -> {
            if (level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
                level.setBlock(pos, ModBlocks.FIRE_PIT.get().defaultBlockState().setValue(FirePitBlock.FACING, facing), 3);
            }
        });
    }
}
