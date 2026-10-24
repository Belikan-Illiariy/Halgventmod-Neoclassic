package com.nrdhvnstd.halgventmod.registry;

import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.beekeeping.SkepBlock;
import com.nrdhvnstd.halgventmod.brewing.AgingCaskBlock;
import com.nrdhvnstd.halgventmod.brewing.FermentingBarrelBlock;
import com.nrdhvnstd.halgventmod.brewing.StillBlock;
import com.nrdhvnstd.halgventmod.brewing.WinePressBlock;
import com.nrdhvnstd.halgventmod.carpentry.CarpenterBenchBlock;
import com.nrdhvnstd.halgventmod.carpentry.ChoppingBlock;
import com.nrdhvnstd.halgventmod.carpentry.SawhorseBlock;
import com.nrdhvnstd.halgventmod.farming.BerryBushBlock;
import com.nrdhvnstd.halgventmod.farming.FieldCropBlock;
import com.nrdhvnstd.halgventmod.farming.FruitLeavesBlock;
import com.nrdhvnstd.halgventmod.farming.RyeCropBlock;
import com.nrdhvnstd.halgventmod.cooking.BrineBarrelBlock;
import com.nrdhvnstd.halgventmod.cooking.FirePitBlock;
import com.nrdhvnstd.halgventmod.cooking.HearthBlock;
import com.nrdhvnstd.halgventmod.cooking.HookBlock;
import com.nrdhvnstd.halgventmod.cooking.KitchenTableBlock;
import com.nrdhvnstd.halgventmod.cooking.OvenBlock;
import com.nrdhvnstd.halgventmod.cooking.QuernBlock;
import com.nrdhvnstd.halgventmod.herbs.Herb;
import com.nrdhvnstd.halgventmod.herbs.HerbPlantBlock;
import com.nrdhvnstd.halgventmod.leather.DryingRackBlock;
import com.nrdhvnstd.halgventmod.leather.FleshingBeamBlock;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlock;
import com.nrdhvnstd.halgventmod.leather.TanningVatBlock;
import com.nrdhvnstd.halgventmod.pottery.ClayBarrelBlock;
import com.nrdhvnstd.halgventmod.pottery.DryingShelfBlock;
import com.nrdhvnstd.halgventmod.pottery.KilnBlock;
import com.nrdhvnstd.halgventmod.pottery.PotteryWheelBlock;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlock;
import com.nrdhvnstd.halgventmod.weaving.LoomBlock;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
    // Костёр: очаг на земле (ставится ванильным костром, CookingEvents)
    public static final DeferredBlock<FirePitBlock> FIRE_PIT = BLOCKS.register("fire_pit",
            () -> new FirePitBlock(wood().lightLevel(state -> state.getValue(HearthBlock.LIT) ? 14 : 0)));
    public static final DeferredBlock<OvenBlock> BAKING_OVEN = BLOCKS.register("baking_oven",
            () -> new OvenBlock(stone().lightLevel(state -> state.getValue(OvenBlock.LIT)
                    && state.getValue(OvenBlock.PART) == 0 ? 12 : 0)));
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

    // Грядки (farming/FieldCropBlock): сажают сам урожай. Модели — Conquest Reforged, у лука и чеснока — свои
    public static final DeferredBlock<FieldCropBlock> BARLEY_CROP = crop("barley_crop", () -> ModItems.BARLEY.get());
    public static final DeferredBlock<FieldCropBlock> OATS_CROP = crop("oats_crop", () -> ModItems.OATS.get());
    public static final DeferredBlock<FieldCropBlock> ONION_CROP = crop("onion_crop", () -> ModItems.ONION.get());
    public static final DeferredBlock<FieldCropBlock> GARLIC_CROP = crop("garlic_crop", () -> ModItems.GARLIC.get());
    public static final DeferredBlock<FieldCropBlock> TURNIP_CROP = crop("turnip_crop", () -> ModItems.TURNIP.get());
    public static final DeferredBlock<FieldCropBlock> BEANS_CROP = crop("beans_crop", () -> ModItems.BEANS.get());
    public static final DeferredBlock<FieldCropBlock> CABBAGE_CROP = crop("cabbage_crop", () -> ModItems.CABBAGE.get());
    public static final DeferredBlock<FieldCropBlock> FLAX_CROP = crop("flax_crop", () -> ModItems.FLAX_SEEDS.get());
    // Ягодные кусты (farming/BerryBushBlock) — растут в лесах и на болотах, ягоды собирают на E
    public static final DeferredBlock<BerryBushBlock> BLUEBERRY_BUSH = bush("blueberry_bush", () -> ModItems.BLUEBERRY.get());
    public static final DeferredBlock<BerryBushBlock> LINGONBERRY_BUSH = bush("lingonberry_bush", () -> ModItems.LINGONBERRY.get());
    public static final DeferredBlock<BerryBushBlock> CRANBERRY_BUSH = bush("cranberry_bush", () -> ModItems.CRANBERRY.get());
    // Плодовая листва (farming/FruitLeavesBlock) — сады строят строители, плоды собирают на E
    public static final DeferredBlock<FruitLeavesBlock> CHERRY_LEAVES = fruitLeaves("cherry_leaves", () -> ModItems.CHERRY.get());
    public static final DeferredBlock<FruitLeavesBlock> PLUM_LEAVES = fruitLeaves("plum_leaves", () -> ModItems.PLUM.get());
    public static final DeferredBlock<FruitLeavesBlock> PEAR_LEAVES = fruitLeaves("pear_leaves", () -> ModItems.PEAR.get());
    public static final DeferredBlock<FruitLeavesBlock> ORANGE_LEAVES = fruitLeaves("orange_leaves", () -> ModItems.ORANGE.get());
    public static final DeferredBlock<FruitLeavesBlock> OLIVE_LEAVES = fruitLeaves("olive_leaves", () -> ModItems.OLIVES.get());
    public static final DeferredBlock<FruitLeavesBlock> GRAPE_VINE = fruitLeaves("grape_vine", () -> ModItems.GRAPE.get());
    public static final DeferredBlock<FruitLeavesBlock> APPLE_LEAVES = fruitLeaves("apple_leaves", () -> Items.APPLE);

    private static DeferredBlock<FieldCropBlock> crop(String id, Supplier<Item> seed) {
        return BLOCKS.register(id, () -> new FieldCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT), seed));
    }

    // Кустики трав (herbs/HerbPlantBlock) — ставят строители, травы срезают ножом, серпом или косой на E
    public static final DeferredBlock<HerbPlantBlock> CHAMOMILE_PLANT = herbPlant("chamomile_plant", () -> Herb.CHAMOMILE);
    public static final DeferredBlock<HerbPlantBlock> MINT_PLANT = herbPlant("mint_plant", () -> Herb.MINT);
    public static final DeferredBlock<HerbPlantBlock> ST_JOHNS_WORT_PLANT = herbPlant("st_johns_wort_plant", () -> Herb.ST_JOHNS_WORT);
    public static final DeferredBlock<HerbPlantBlock> YARROW_PLANT = herbPlant("yarrow_plant", () -> Herb.YARROW);
    public static final DeferredBlock<HerbPlantBlock> WORMWOOD_PLANT = herbPlant("wormwood_plant", () -> Herb.WORMWOOD);
    public static final DeferredBlock<HerbPlantBlock> NETTLE_PLANT = herbPlant("nettle_plant", () -> Herb.NETTLE);
    public static final DeferredBlock<HerbPlantBlock> THYME_PLANT = herbPlant("thyme_plant", () -> Herb.THYME);
    public static final DeferredBlock<HerbPlantBlock> LAVENDER_PLANT = herbPlant("lavender_plant", () -> Herb.LAVENDER);

    // Вид травы — через поставщика: Herb ссылается на ModItems, а ModItems — на эти блоки
    private static DeferredBlock<HerbPlantBlock> herbPlant(String id, Supplier<Herb> herb) {
        return BLOCKS.register(id, () -> new HerbPlantBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT).randomTicks().noCollission().instabreak()
                .sound(SoundType.GRASS).noOcclusion().offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY).ignitedByLava(), herb.get()));
    }

    private static DeferredBlock<BerryBushBlock> bush(String id, Supplier<Item> berry) {
        return BLOCKS.register(id, () -> new BerryBushBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT).randomTicks().noCollission().strength(0.2F)
                .sound(SoundType.SWEET_BERRY_BUSH).noOcclusion().pushReaction(PushReaction.DESTROY).ignitedByLava(), berry));
    }

    private static DeferredBlock<FruitLeavesBlock> fruitLeaves(String id, Supplier<Item> fruit) {
        return BLOCKS.register(id, () -> new FruitLeavesBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT).randomTicks().strength(0.2F).sound(SoundType.GRASS).noOcclusion()
                .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
                .isValidSpawn((state, level, pos, type) -> false).ignitedByLava(), fruit));
    }

    // Гончарное дело (pottery/*). Модели — из Conquest Reforged
    public static final DeferredBlock<ClayBarrelBlock> CLAY_BARREL = BLOCKS.register("clay_barrel",
            () -> new ClayBarrelBlock(wood()));
    public static final DeferredBlock<PotteryWheelBlock> POTTERY_WHEEL = BLOCKS.register("pottery_wheel",
            () -> new PotteryWheelBlock(wood()));
    public static final DeferredBlock<DryingShelfBlock> DRYING_SHELF = BLOCKS.register("drying_shelf",
            () -> new DryingShelfBlock(wood()));
    public static final DeferredBlock<com.nrdhvnstd.halgventmod.pottery.DomeKilnBlock> DOME_KILN = BLOCKS.register("dome_kiln",
            () -> new com.nrdhvnstd.halgventmod.pottery.DomeKilnBlock(stone().lightLevel(state -> state.getValue(KilnBlock.LIT)
                    && state.getValue(com.nrdhvnstd.halgventmod.pottery.DomeKilnBlock.PART) == 0 ? 13 : 0)));

    // Ткачество (weaving/*). Модели — из Conquest Reforged
    public static final DeferredBlock<LoomBlock> LOOM = BLOCKS.register("loom", () -> new LoomBlock(wood()));

    // Плотничество (carpentry/*): колода — пень Conquest Reforged, козлы и верстак — свои модели из ванильного дерева
    public static final DeferredBlock<ChoppingBlock> CHOPPING_BLOCK = BLOCKS.register("chopping_block",
            () -> new ChoppingBlock(wood()));
    public static final DeferredBlock<SawhorseBlock> SAWHORSE = BLOCKS.register("sawhorse", () -> new SawhorseBlock(wood()));
    public static final DeferredBlock<CarpenterBenchBlock> CARPENTER_BENCH = BLOCKS.register("carpenter_bench",
            () -> new CarpenterBenchBlock(wood()));

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
        com.nrdhvnstd.halgventmod.smithing.SmithingBlocks.init();
        com.nrdhvnstd.halgventmod.alchemy.AlchemyBlocks.init();
        com.nrdhvnstd.halgventmod.glass.GlassBlocks.init();
        BLOCKS.register(modEventBus);
    }
}
