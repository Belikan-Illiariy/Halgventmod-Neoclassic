package com.nrdhvnstd.halgventmod.glass;

import com.nrdhvnstd.halgventmod.menu.GlassFurnaceMenu;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModBlocks;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Блок, блок-сущность и окно стекольного дела (стекловаренная печь). Регистрируются в общих реестрах мода (ModBlocks,
 * ModBlockEntities, ModMenus): их register() зовёт {@link #init()} и прочие — класс загружается до регистрации.
 */
public final class GlassBlocks {
    public static final DeferredBlock<GlassFurnaceBlock> GLASS_FURNACE = ModBlocks.BLOCKS.register("glass_furnace",
            () -> new GlassFurnaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(3.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(GlassFurnaceBlock.LIT) && state.getValue(GlassFurnaceBlock.PART) == 0 ? 14 : 0)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GlassFurnaceBlockEntity>> GLASS_FURNACE_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("glass_furnace", () -> BlockEntityType.Builder
                    .of(GlassFurnaceBlockEntity::new, GLASS_FURNACE.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<GlassFurnaceMenu>> GLASS_FURNACE_MENU =
            ModMenus.MENUS.register("glass_furnace", () -> IMenuTypeExtension.create(GlassFurnaceMenu::fromNetwork));

    private GlassBlocks() {}

    /** Загрузить класс: блок, блок-сущность и окно попадают в реестры мода до регистрации. */
    public static void init() {
    }

    public static void initEntities() {
    }

    public static void initMenus() {
    }
}
