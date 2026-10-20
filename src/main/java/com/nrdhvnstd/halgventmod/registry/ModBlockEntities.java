package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.BrineBarrelBlockEntity;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.cooking.HookBlockEntity;
import com.nrdhvnstd.halgventmod.cooking.OvenBlockEntity;
import com.nrdhvnstd.halgventmod.cooking.QuernBlockEntity;
import com.nrdhvnstd.halgventmod.leather.DryingRackBlockEntity;
import com.nrdhvnstd.halgventmod.leather.FleshingBeamBlockEntity;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.leather.TanningVatBlockEntity;
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

    // Станции кожевника (leather/*)
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FleshingBeamBlockEntity>> FLESHING_BEAM =
            BLOCK_ENTITIES.register("fleshing_beam", () -> BlockEntityType.Builder
                    .of(FleshingBeamBlockEntity::new, ModBlocks.FLESHING_BEAM.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TanningVatBlockEntity>> TANNING_VAT =
            BLOCK_ENTITIES.register("tanning_vat", () -> BlockEntityType.Builder
                    .of(TanningVatBlockEntity::new, ModBlocks.TANNING_VAT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DryingRackBlockEntity>> DRYING_RACK =
            BLOCK_ENTITIES.register("drying_rack", () -> BlockEntityType.Builder
                    .of(DryingRackBlockEntity::new, ModBlocks.DRYING_RACK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LeatherTableBlockEntity>> LEATHER_TABLE =
            BLOCK_ENTITIES.register("leather_table", () -> BlockEntityType.Builder
                    .of(LeatherTableBlockEntity::new, ModBlocks.LEATHER_TABLE.get()).build(null));

    // Готовка (cooking/*)
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HearthBlockEntity>> HEARTH =
            BLOCK_ENTITIES.register("hearth", () -> BlockEntityType.Builder
                    .of(HearthBlockEntity::new, ModBlocks.HEARTH.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OvenBlockEntity>> OVEN =
            BLOCK_ENTITIES.register("baking_oven", () -> BlockEntityType.Builder
                    .of(OvenBlockEntity::new, ModBlocks.BAKING_OVEN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuernBlockEntity>> QUERN =
            BLOCK_ENTITIES.register("quern", () -> BlockEntityType.Builder
                    .of(QuernBlockEntity::new, ModBlocks.QUERN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrineBarrelBlockEntity>> BRINE_BARREL =
            BLOCK_ENTITIES.register("brine_barrel", () -> BlockEntityType.Builder
                    .of(BrineBarrelBlockEntity::new, ModBlocks.BRINE_BARREL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HookBlockEntity>> SMOKING_HOOK =
            BLOCK_ENTITIES.register("smoking_hook", () -> BlockEntityType.Builder
                    .of(HookBlockEntity::new, ModBlocks.SMOKING_HOOK.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
