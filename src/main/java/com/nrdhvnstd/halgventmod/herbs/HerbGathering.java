package com.nrdhvnstd.halgventmod.herbs;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Травы: срезают только со своих кустиков (HerbPlantBlock) — ножом, серпом или косой, ничего не ломая. С обычной
 * травы и цветов ничего не срезается и не падает. Здесь же: выпитый отвар травы даёт её действие (Herb#steep).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class HerbGathering {
    /** Свежие травы — все виды (data/halgventmod/tags/item/herbs.json). */
    public static final TagKey<Item> HERBS = ItemTags.create(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "herbs"));

    private HerbGathering() {}

    /** Чем срезают травы: серп, коса, нож (мода или охотничий Conquest). */
    public static boolean isCutter(ItemStack stack) {
        return stack.is(ModItems.SICKLE.get()) || stack.is(ModItems.SCYTHE.get()) || stack.is(LeatherTableBlockEntity.KNIVES);
    }

    // Отвар травы: её действие
    @SubscribeEvent
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        ItemStack drink = event.getItem();
        if (event.getEntity().level().isClientSide || !drink.is(ModItems.HERBAL_TEA.get())) {
            return;
        }
        Herb herb = Herb.of(drink);
        if (herb != null) {
            herb.steep(event.getEntity());
        }
    }
}
