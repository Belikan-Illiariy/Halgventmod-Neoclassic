package com.nrdhvnstd.halgventmod.smithing;

import com.nrdhvnstd.halgventmod.menu.WorkshopMenu;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModBlocks;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Блоки, блок-сущности и окно кузнечества. Регистрируются в общих реестрах мода (ModBlocks, ModBlockEntities,
 * ModMenus): их register() зовёт {@link #init()} и прочие — класс загружается до регистрации.
 */
public final class SmithingBlocks {
    // ----- Места добычи (ставят строители; не ломаются в приключении)
    public static final DeferredBlock<OreVeinBlock> IRON_VEIN = vein("iron_vein", OreKind.IRON);
    public static final DeferredBlock<OreVeinBlock> COPPER_VEIN = vein("copper_vein", OreKind.COPPER);
    public static final DeferredBlock<OreVeinBlock> TIN_VEIN = vein("tin_vein", OreKind.TIN);
    public static final DeferredBlock<OreVeinBlock> LEAD_VEIN = vein("lead_vein", OreKind.LEAD);
    public static final DeferredBlock<OreVeinBlock> SILVER_VEIN = vein("silver_vein", OreKind.SILVER);
    public static final DeferredBlock<OreVeinBlock> GOLD_VEIN = vein("gold_vein", OreKind.GOLD);
    public static final DeferredBlock<OreVeinBlock> ZINC_VEIN = vein("zinc_vein", OreKind.ZINC);
    public static final DeferredBlock<OreVeinBlock> CINNABAR_VEIN = vein("cinnabar_vein", OreKind.CINNABAR);
    public static final DeferredBlock<OreVeinBlock> LIMESTONE_VEIN = vein("limestone_vein", OreKind.LIMESTONE);
    public static final DeferredBlock<OreVeinBlock> BOG_SILT = ModBlocks.BLOCKS.register("bog_silt",
            () -> new OreVeinBlock(OreKind.BOG, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN)
                    .strength(2.0F).sound(SoundType.MUD).requiresCorrectToolForDrops()));
    public static final DeferredBlock<GoldPlacerBlock> GOLD_PLACER = ModBlocks.BLOCKS.register("gold_placer",
            () -> new GoldPlacerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0F)
                    .sound(SoundType.SAND).requiresCorrectToolForDrops()));

    // ----- Станции
    public static final DeferredBlock<CharcoalPileBlock> CHARCOAL_PILE = ModBlocks.BLOCKS.register("charcoal_pile",
            () -> new CharcoalPileBlock(earth().lightLevel(state -> state.getValue(SmithStationBlock.Lit.LIT) ? 4 : 0)));
    public static final DeferredBlock<SmithStationBlock.Lit> BLOOMERY = ModBlocks.BLOCKS.register("bloomery",
            () -> new SmithStationBlock.Lit(stone().lightLevel(state -> state.getValue(SmithStationBlock.Lit.LIT) ? 12 : 0),
                    Block.box(1, 0, 1, 15, 16, 15), BloomeryBlockEntity::new, null));
    public static final DeferredBlock<SmithStationBlock.Lit> CUPEL = ModBlocks.BLOCKS.register("cupel",
            () -> new SmithStationBlock.Lit(stone().lightLevel(state -> state.getValue(SmithStationBlock.Lit.LIT) ? 10 : 0),
                    Block.box(2, 0, 2, 14, 9, 14), CupelBlockEntity::new, null));
    public static final DeferredBlock<SmithStationBlock.Lit> SMITHY_FORGE = ModBlocks.BLOCKS.register("smithy_forge",
            () -> new SmithStationBlock.Lit(stone().lightLevel(state -> state.getValue(SmithStationBlock.Lit.LIT) ? 13 : 0),
                    Block.box(0, 0, 0, 16, 14, 16), ForgeBlockEntity::new, () -> SmithingBlocks.FORGE_ENTITY.get()));
    public static final DeferredBlock<SmithStationBlock> SMITH_ANVIL = ModBlocks.BLOCKS.register("smith_anvil",
            () -> new SmithStationBlock(metal(), Block.box(2, 0, 2, 14, 14, 14), WorkshopBlockEntity.Anvil::new, null));
    public static final DeferredBlock<SharpeningWheelBlock> SHARPENING_WHEEL = ModBlocks.BLOCKS.register(
            "sharpening_wheel", () -> new SharpeningWheelBlock(wood()));
    public static final DeferredBlock<CementationBoxBlock> CEMENTATION_BOX = ModBlocks.BLOCKS.register(
            "cementation_box", () -> new CementationBoxBlock(stone().lightLevel(state -> state.getValue(SmithStationBlock.Lit.LIT) ? 10 : 0)));
    public static final DeferredBlock<SmithStationBlock> CASTING_TABLE = ModBlocks.BLOCKS.register("casting_table",
            () -> new SmithStationBlock(wood(), Block.box(0, 0, 0, 16, 14, 16), WorkshopBlockEntity.CastingTable::new,
                    null));
    public static final DeferredBlock<SmithStationBlock> MORTAR = ModBlocks.BLOCKS.register("mortar",
            () -> new SmithStationBlock(metal(), Block.box(4, 0, 4, 12, 10, 12), MortarBlockEntity::new, null));
    public static final DeferredBlock<SmithStationBlock> SLUICE = ModBlocks.BLOCKS.register("sluice",
            () -> new SmithStationBlock(wood(), Block.box(0, 0, 2, 16, 8, 14), SluiceBlockEntity::new, null));

    // ----- Блок-сущности
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CharcoalPileBlockEntity>> CHARCOAL_PILE_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("charcoal_pile", () -> BlockEntityType.Builder
                    .of(CharcoalPileBlockEntity::new, CHARCOAL_PILE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BloomeryBlockEntity>> BLOOMERY_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("bloomery", () -> BlockEntityType.Builder
                    .of(BloomeryBlockEntity::new, BLOOMERY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CupelBlockEntity>> CUPEL_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("cupel", () -> BlockEntityType.Builder
                    .of(CupelBlockEntity::new, CUPEL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeBlockEntity>> FORGE_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("smithy_forge", () -> BlockEntityType.Builder
                    .of(ForgeBlockEntity::new, SMITHY_FORGE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkshopBlockEntity.Anvil>> ANVIL_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("smith_anvil", () -> BlockEntityType.Builder
                    .of(WorkshopBlockEntity.Anvil::new, SMITH_ANVIL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CementationBoxBlockEntity>> CEMENTATION_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("cementation_box", () -> BlockEntityType.Builder
                    .of(CementationBoxBlockEntity::new, CEMENTATION_BOX.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkshopBlockEntity.CastingTable>> CASTING_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("casting_table", () -> BlockEntityType.Builder
                    .of(WorkshopBlockEntity.CastingTable::new, CASTING_TABLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MortarBlockEntity>> MORTAR_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("mortar", () -> BlockEntityType.Builder
                    .of(MortarBlockEntity::new, MORTAR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SluiceBlockEntity>> SLUICE_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("sluice", () -> BlockEntityType.Builder
                    .of(SluiceBlockEntity::new, SLUICE.get()).build(null));

    // ----- Окна кузнечного и сыродутного горнов и цементационного ящика
    public static final DeferredHolder<MenuType<?>, MenuType<com.nrdhvnstd.halgventmod.menu.ForgeMenu>> FORGE_MENU =
            ModMenus.MENUS.register("smithy_forge", () -> IMenuTypeExtension.create(com.nrdhvnstd.halgventmod.menu.ForgeMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<com.nrdhvnstd.halgventmod.menu.BloomeryMenu>> BLOOMERY_MENU =
            ModMenus.MENUS.register("bloomery", () -> IMenuTypeExtension.create(com.nrdhvnstd.halgventmod.menu.BloomeryMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<com.nrdhvnstd.halgventmod.menu.CementationMenu>> CEMENTATION_MENU =
            ModMenus.MENUS.register("cementation_box", () -> IMenuTypeExtension.create(com.nrdhvnstd.halgventmod.menu.CementationMenu::fromNetwork));

    // ----- Окно наковальни и литейного стола
    public static final DeferredHolder<MenuType<?>, MenuType<WorkshopMenu>> WORKSHOP_MENU =
            ModMenus.MENUS.register("workshop", () -> IMenuTypeExtension.create(WorkshopMenu::fromNetwork));

    private SmithingBlocks() {}

    /** Загрузить класс: блоки, блок-сущности и окно попадают в реестры мода до регистрации. */
    public static void init() {
    }

    public static void initEntities() {
    }

    public static void initMenus() {
    }

    private static DeferredBlock<OreVeinBlock> vein(String id, OreKind kind) {
        return ModBlocks.BLOCKS.register(id, () -> new OreVeinBlock(kind, BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(3.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    }

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(2.5F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F).sound(SoundType.ANVIL)
                .requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion();
    }

    private static BlockBehaviour.Properties earth() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(1.0F).sound(SoundType.ROOTED_DIRT)
                .noOcclusion();
    }
}
