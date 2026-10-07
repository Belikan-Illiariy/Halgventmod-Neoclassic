package com.nrdhvnstd.halgventmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.datagen.DataGenerators;
import com.nrdhvnstd.halgventmod.network.ModNetwork;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModAttributes;
import com.nrdhvnstd.halgventmod.registry.ModBlocks;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModCreativeTabs;
import com.nrdhvnstd.halgventmod.registry.ModEffects;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.registry.ModMenus;
import com.nrdhvnstd.halgventmod.registry.ModPaintings;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(HalgventMod.MODID)
public class HalgventMod {
    public static final String MODID = "halgventmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HalgventMod(IEventBus modEventBus) {
        ModAttachments.register(modEventBus);
        ModAttributes.register(modEventBus);
        ModEffects.register(modEventBus);
        ModComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModPaintings.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModNetwork.register(modEventBus);

        // Генерация JSON-файлов (задача runData)
        modEventBus.addListener(DataGenerators::gatherData);
    }
}