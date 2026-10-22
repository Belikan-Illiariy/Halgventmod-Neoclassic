package com.nrdhvnstd.halgventmod.cooking;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlocks;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Готовка в мире:
 * <ul>
 *   <li>сырое мясо и рыба (тег halgventmod:raw_food) — «Отравление» (food/FoodEffects);</li>
 *   <li>травы — с травы и папоротника, срезанных серпом, косой или ножом (шанс {@link #HERB_CHANCE});</li>
 *   <li>поставленный ванильный костёр становится костром мода (FirePitBlock): незажжённым, на дровах и огниве.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class CookingEvents {
    // Меняйте здесь
    public static final float HERB_CHANCE = 0.25F;

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

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) {
            return;
        }
        boolean grass = event.getState().is(Blocks.SHORT_GRASS) || event.getState().is(Blocks.FERN)
                || event.getState().is(Blocks.TALL_GRASS) || event.getState().is(Blocks.LARGE_FERN);
        ItemStack tool = player.getMainHandItem();
        boolean cutting = tool.is(ModItems.SICKLE.get()) || tool.is(ModItems.SCYTHE.get())
                || tool.is(LeatherTableBlockEntity.KNIVES);
        ServerLevel level = event.getLevel();
        if (grass && cutting && level.random.nextFloat() < HERB_CHANCE) {
            BlockPos pos = event.getPos();
            event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    new ItemStack(ModItems.HERBS.get())));
        }
    }
}
