package com.nrdhvnstd.halgventmod.trade;

import java.util.EnumMap;
import java.util.Map;

import com.nrdhvnstd.halgventmod.menu.MarketStallMenu;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModBlocks;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Торговля: прилавки по видам (StallKind), их блок-сущность и окно хозяина. Предметы — в ModItems. */
public final class TradeBlocks {
    public static final Map<StallKind, DeferredBlock<MarketStallBlock>> STALLS = new EnumMap<>(StallKind.class);
    static {
        for (StallKind kind : StallKind.values()) {
            STALLS.put(kind, ModBlocks.BLOCKS.register(kind.id(), () -> new MarketStallBlock(kind,
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion())));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarketStallBlockEntity>> MARKET_STALL_ENTITY =
            ModBlockEntities.BLOCK_ENTITIES.register("market_stall", () -> BlockEntityType.Builder.of(MarketStallBlockEntity::new,
                    STALLS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<MarketStallMenu>> MARKET_STALL_MENU =
            ModMenus.MENUS.register("market_stall", () -> IMenuTypeExtension.create(MarketStallMenu::fromNetwork));

    private TradeBlocks() {}

    /** Загрузить класс: блоки, блок-сущность и окно попадают в реестры мода до регистрации. */
    public static void init() {
    }
}
