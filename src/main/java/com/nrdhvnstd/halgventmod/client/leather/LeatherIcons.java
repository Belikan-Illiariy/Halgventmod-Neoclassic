package com.nrdhvnstd.halgventmod.client.leather;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.Animals;
import com.nrdhvnstd.halgventmod.leather.Hide;
import com.nrdhvnstd.halgventmod.leather.HideItem;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * У шкуры и меха своя иконка для каждого зверя: свойство {@code halgventmod:animal} — номер зверя в
 * {@link Animals#index} (0 — общая иконка). Модели с overrides пишет датаген (ModItemModelProvider).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class LeatherIcons {
    private LeatherIcons() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Item item : new Item[] {ModItems.RAW_HIDE.get(), ModItems.SALTED_HIDE.get(), ModItems.DRESSED_FUR.get()}) {
                ItemProperties.register(item, HideItem.ANIMAL, (stack, level, entity, seed) -> {
                    Hide hide = stack.get(ModComponents.HIDE.get());
                    return hide == null ? 0 : Animals.index(hide.animal());
                });
            }
        });
    }
}
