package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HalgventMod.MODID);

    // Невидимый источник звуков места: id сцены и радиус
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoundEmitterBlockEntity>> SOUND_EMITTER =
            BLOCK_ENTITIES.register("sound_emitter", () -> BlockEntityType.Builder
                    .of(SoundEmitterBlockEntity::new, ModBlocks.SOUND_EMITTER.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
