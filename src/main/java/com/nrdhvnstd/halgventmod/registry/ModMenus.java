package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.AgingCaskMenu;
import com.nrdhvnstd.halgventmod.menu.ArmorStandMenu;
import com.nrdhvnstd.halgventmod.menu.CarpenterBenchMenu;
import com.nrdhvnstd.halgventmod.menu.FermentingBarrelMenu;
import com.nrdhvnstd.halgventmod.menu.HearthMenu;
import com.nrdhvnstd.halgventmod.menu.KilnMenu;
import com.nrdhvnstd.halgventmod.menu.OvenMenu;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;
import com.nrdhvnstd.halgventmod.menu.SewingTableMenu;
import com.nrdhvnstd.halgventmod.menu.StillMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Окна с ячейками (меню). Экраны к ним привязываются в client/InventoryOverride. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, HalgventMod.MODID);

    // Инвентарь игрока: рюкзак, хотбар, вторая рука, 12 ячеек брони
    public static final DeferredHolder<MenuType<?>, MenuType<PlayerArmorMenu>> PLAYER_INVENTORY =
            MENUS.register("player_inventory", () -> new MenuType<>(PlayerArmorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // Стойка для брони: 12 ячеек брони стойки + хранилище, карманы и вторая рука игрока
    public static final DeferredHolder<MenuType<?>, MenuType<ArmorStandMenu>> ARMOR_STAND =
            MENUS.register("armor_stand", () -> IMenuTypeExtension.create(ArmorStandMenu::fromNetwork));

    // Шитейный стол: выкройки, ячейки стола 3×3 + сумки, карманы и вторая рука игрока
    public static final DeferredHolder<MenuType<?>, MenuType<SewingTableMenu>> SEWING_TABLE =
            MENUS.register("sewing_table", () -> IMenuTypeExtension.create(SewingTableMenu::fromNetwork));

    // Верстак плотника: изделия, ячейки верстака 3×3 + сумки, карманы и вторая рука игрока
    public static final DeferredHolder<MenuType<?>, MenuType<CarpenterBenchMenu>> CARPENTER_BENCH =
            MENUS.register("carpenter_bench", () -> IMenuTypeExtension.create(CarpenterBenchMenu::fromNetwork));

    // Огонь и печи: очаг и костёр (с горшком), печь, горн
    public static final DeferredHolder<MenuType<?>, MenuType<HearthMenu>> HEARTH =
            MENUS.register("hearth", () -> IMenuTypeExtension.create(HearthMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<OvenMenu>> OVEN =
            MENUS.register("oven", () -> IMenuTypeExtension.create(OvenMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<KilnMenu>> KILN =
            MENUS.register("kiln", () -> IMenuTypeExtension.create(KilnMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<StillMenu>> STILL =
            MENUS.register("still", () -> IMenuTypeExtension.create(StillMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<AgingCaskMenu>> AGING_CASK =
            MENUS.register("aging_cask", () -> IMenuTypeExtension.create(AgingCaskMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<FermentingBarrelMenu>> FERMENTING_BARREL =
            MENUS.register("fermenting_barrel", () -> IMenuTypeExtension.create(FermentingBarrelMenu::fromNetwork));

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        com.nrdhvnstd.halgventmod.smithing.SmithingBlocks.initMenus();
        com.nrdhvnstd.halgventmod.alchemy.AlchemyBlocks.initMenus();
        com.nrdhvnstd.halgventmod.glass.GlassBlocks.initMenus();
        MENUS.register(modEventBus);
    }
}
