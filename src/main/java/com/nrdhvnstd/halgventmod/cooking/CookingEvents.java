package com.nrdhvnstd.halgventmod.cooking;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Готовка в мире:
 * <ul>
 *   <li>сырое мясо и рыба (тег halgventmod:raw_food) и недожаренное (признак «Сырое») — с шансом
 *       {@link #RAW_SICK_CHANCE} «Голод», с шансом {@link #RAW_NAUSEA_CHANCE} ещё и «Тошнота»;</li>
 *   <li>травы — с травы и папоротника, срезанных серпом, косой или ножом (шанс {@link #HERB_CHANCE}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class CookingEvents {
    // Меняйте здесь
    public static final float RAW_SICK_CHANCE = 0.4F;
    public static final int RAW_SICK_SECONDS = 30;
    public static final float RAW_NAUSEA_CHANCE = 0.2F;
    public static final int RAW_NAUSEA_SECONDS = 8;
    public static final float HERB_CHANCE = 0.25F;

    public static final TagKey<Item> RAW_FOOD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "raw_food"));

    private CookingEvents() {}

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack used = event.getItem();
        if (!used.is(RAW_FOOD) && !FoodQuality.isMarkedRaw(used)) {
            return;
        }
        if (player.getRandom().nextFloat() < RAW_SICK_CHANCE) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, RAW_SICK_SECONDS * 20, 0));
            player.displayClientMessage(Component.translatable("message.halgventmod.cooking.raw_sick"), true);
            if (player.getRandom().nextFloat() < RAW_NAUSEA_CHANCE / RAW_SICK_CHANCE) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, RAW_NAUSEA_SECONDS * 20, 0));
            }
        }
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
