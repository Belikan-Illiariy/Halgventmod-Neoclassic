package com.nrdhvnstd.halgventmod.client.glass;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.glass.GlassItems;
import com.nrdhvnstd.halgventmod.glass.Glassmaking;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Стеклянная посуда на клиенте: иконка по цвету стекла — свойство {@link Glassmaking#TINT} (номер цвета), модели
 * берут textures/item/glass/&lt;предмет&gt;_&lt;цвет&gt; (рисует GlassIcons).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class GlassClient {
    private GlassClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Item item : new Item[] {GlassItems.GLASS_GOBLET.get(), GlassItems.GLASS_FLASK.get()}) {
                ItemProperties.register(item, Glassmaking.TINT, (stack, level, entity, seed) ->
                        Glassmaking.Tint.byId(stack.get(ModComponents.GLASS_TINT.get())).ordinal());
            }
        });
    }
}
