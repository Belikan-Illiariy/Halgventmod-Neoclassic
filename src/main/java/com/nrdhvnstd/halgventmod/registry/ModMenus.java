package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.ArmorStandMenu;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;

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

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
