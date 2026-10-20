package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.beekeeping.SkepBlock;
import com.nrdhvnstd.halgventmod.brewing.AgingCaskBlock;
import com.nrdhvnstd.halgventmod.brewing.FermentingBarrelBlock;
import com.nrdhvnstd.halgventmod.brewing.StillBlock;
import com.nrdhvnstd.halgventmod.brewing.WinePressBlock;
import com.nrdhvnstd.halgventmod.farming.RyeCropBlock;
import com.nrdhvnstd.halgventmod.cooking.BrineBarrelBlock;
import com.nrdhvnstd.halgventmod.cooking.HearthBlock;
import com.nrdhvnstd.halgventmod.cooking.HookBlock;
import com.nrdhvnstd.halgventmod.cooking.KitchenTableBlock;
import com.nrdhvnstd.halgventmod.cooking.OvenBlock;
import com.nrdhvnstd.halgventmod.cooking.QuernBlock;
import com.nrdhvnstd.halgventmod.leather.DryingRackBlock;
import com.nrdhvnstd.halgventmod.leather.FleshingBeamBlock;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlock;
import com.nrdhvnstd.halgventmod.leather.TanningVatBlock;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlock;

import net.minecraft.world.level.block.Blocks;
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

    // Рожь — посев на пашне, как пшеница
    public static final DeferredBlock<RyeCropBlock> RYE = BLOCKS.register("rye",
            () -> new RyeCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)));

    // Алкоголь (brewing/*): давильня, бродильная бочка, перегонный куб, бочка для выдержки
    public static final DeferredBlock<WinePressBlock> WINE_PRESS = BLOCKS.register("wine_press", () -> new WinePressBlock(wood()));
    public static final DeferredBlock<FermentingBarrelBlock> FERMENTING_BARREL = BLOCKS.register("fermenting_barrel",
            () -> new FermentingBarrelBlock(wood()));
    public static final DeferredBlock<StillBlock> STILL = BLOCKS.register("still", () -> new StillBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredBlock<AgingCaskBlock> AGING_CASK = BLOCKS.register("aging_cask", () -> new AgingCaskBlock(wood()));

    // Пчеловодство: плетёный улей Conquest Reforged
    public static final DeferredBlock<SkepBlock> SKEP = BLOCKS.register("skep",
            () -> new SkepBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.8F)
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .ignitedByLava()));

    // Готовка (cooking/*). Модели — из Conquest Reforged, у бочки — ванильная
    public static final DeferredBlock<HearthBlock> HEARTH = BLOCKS.register("hearth",
            () -> new HearthBlock(stone().lightLevel(state -> state.getValue(HearthBlock.LIT) ? 13 : 0)));
    public static final DeferredBlock<OvenBlock> BAKING_OVEN = BLOCKS.register("baking_oven",
            () -> new OvenBlock(stone().lightLevel(state -> state.getValue(OvenBlock.LIT) ? 12 : 0)));
    public static final DeferredBlock<QuernBlock> QUERN = BLOCKS.register("quern",
            () -> new QuernBlock(stone()));
    public static final DeferredBlock<KitchenTableBlock> KITCHEN_TABLE = BLOCKS.register("kitchen_table",
            () -> new KitchenTableBlock(wood()));
    public static final DeferredBlock<BrineBarrelBlock> BRINE_BARREL = BLOCKS.register("brine_barrel",
            () -> new BrineBarrelBlock(wood()));
    public static final DeferredBlock<HookBlock> SMOKING_HOOK = BLOCKS.register("smoking_hook",
            () -> new HookBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F)
                    .sound(SoundType.CHAIN)
                    .noCollission()
                    .noOcclusion()));

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.5F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }

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
