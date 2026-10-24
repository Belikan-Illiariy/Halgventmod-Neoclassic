package com.nrdhvnstd.halgventmod.alchemy;

import com.nrdhvnstd.halgventmod.menu.RetortMenu;
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
 * Блок, блок-сущность и окно алхимии (реторта). Регистрируются в общих реестрах мода (ModBlocks, ModBlockEntities,
 * ModMenus): их register() зовёт {@link #init()} и прочие — класс загружается до регистрации.
 */
public final class AlchemyBlocks {
    public static final DeferredBlock<RetortBlock> RETORT = ModBlocks.BLOCKS.register("retort",
            () -> new RetortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5F)
                    .sound(SoundType.DECORATED_POT).noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RetortBlockEntity>> RETORT_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("retort", () -> BlockEntityType.Builder
                    .of(RetortBlockEntity::new, RETORT.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<RetortMenu>> RETORT_MENU =
            ModMenus.MENUS.register("retort", () -> IMenuTypeExtension.create(RetortMenu::fromNetwork));

    private AlchemyBlocks() {}

    /** Загрузить класс: блок, блок-сущность и окно попадают в реестры мода до регистрации. */
    public static void init() {
    }

    public static void initEntities() {
    }

    public static void initMenus() {
    }
}
