package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HalgventMod.MODID);

    // Здесь будут блоки: чан, колода, сушилка...

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}