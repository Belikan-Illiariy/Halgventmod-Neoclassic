package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.DryingRackBlock;
import com.nrdhvnstd.halgventmod.leather.FleshingBeamBlock;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlock;
import com.nrdhvnstd.halgventmod.leather.TanningVatBlock;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HalgventMod.MODID);

    // Невидимый источник звуков места: как блок света — ломается только в творческом, без дропа, сквозь него ходят
    public static final DeferredBlock<SoundEmitterBlock> SOUND_EMITTER = BLOCKS.register("sound_emitter",
            () -> new SoundEmitterBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.8F)
                    .noLootTable()
                    .noCollission()
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    // Станции кожевника (leather/*). Модели — из Conquest Reforged
    public static final DeferredBlock<FleshingBeamBlock> FLESHING_BEAM = BLOCKS.register("fleshing_beam",
            () -> new FleshingBeamBlock(wood()));
    public static final DeferredBlock<TanningVatBlock> TANNING_VAT = BLOCKS.register("tanning_vat",
            () -> new TanningVatBlock(wood()));
    public static final DeferredBlock<DryingRackBlock> DRYING_RACK = BLOCKS.register("drying_rack",
            () -> new DryingRackBlock(wood()));
    public static final DeferredBlock<LeatherTableBlock> LEATHER_TABLE = BLOCKS.register("leather_table",
            () -> new LeatherTableBlock(wood()));

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .noOcclusion()
                .ignitedByLava();
    }

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
