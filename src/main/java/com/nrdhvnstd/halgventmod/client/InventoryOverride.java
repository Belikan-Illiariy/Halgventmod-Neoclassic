package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.OpenInventoryPayload;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Наш инвентарь вместо ванильного — и в выживании, и в творческом режиме.
 * Ванильное окно открывается только на клиенте, поэтому его открытие отменяется,
 * а сервер по запросу открывает PlayerArmorMenu. Творческий режим идёт через то же
 * ванильное окно, так что перехватывается вместе с ним.
 * Каталог творческого режима открывается кнопкой; его вкладка «Инвентарь» ведёт обратно
 * (CreativeModeInventoryScreenMixin).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class InventoryOverride {
    private InventoryOverride() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PLAYER_INVENTORY.get(), ArmorInventoryScreen::new);
        event.register(ModMenus.ARMOR_STAND.get(), ArmorStandScreen::new);
        event.register(ModMenus.SEWING_TABLE.get(), com.nrdhvnstd.halgventmod.client.leather.SewingTableScreen::new);
        event.register(ModMenus.HEARTH.get(), com.nrdhvnstd.halgventmod.client.cooking.HearthScreen::new);
        event.register(ModMenus.OVEN.get(), com.nrdhvnstd.halgventmod.client.cooking.OvenScreen::new);
        event.register(ModMenus.KILN.get(), com.nrdhvnstd.halgventmod.client.pottery.KilnScreen::new);
        event.register(ModMenus.STILL.get(), com.nrdhvnstd.halgventmod.client.brewing.StillScreen::new);
        event.register(ModMenus.AGING_CASK.get(), com.nrdhvnstd.halgventmod.client.brewing.AgingCaskScreen::new);
        event.register(ModMenus.FERMENTING_BARREL.get(), com.nrdhvnstd.halgventmod.client.brewing.FermentingBarrelScreen::new);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof InventoryScreen) {
            event.setCanceled(true);
            requestInventory();
        }
    }

    /** Попросить сервер открыть наш инвентарь. */
    public static void requestInventory() {
        PacketDistributor.sendToServer(OpenInventoryPayload.INSTANCE);
    }

    /**
     * Из каталога творческого режима: открыть наш инвентарь вместе с вещью на курсоре.
     * Вещь на курсоре каталога живёт только у клиента — отдаём её серверу, а у себя убираем,
     * иначе она так и висела бы на курсоре при возврате в каталог.
     */
    public static void requestInventoryFromCatalog() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack carried = player.inventoryMenu.getCarried(); // курсор каталога — это курсор inventoryMenu
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        PacketDistributor.sendToServer(new OpenInventoryPayload(carried));
    }

    /** Закрыть наш инвентарь и открыть каталог творческого режима. */
    public static void openCatalog() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        player.closeContainer(); // сервер узнаёт, что окно закрыто; предмет с курсора вернётся в инвентарь
        minecraft.setScreen(new CreativeModeInventoryScreen(player, player.connection.enabledFeatures(),
                minecraft.options.operatorItemsTab().get()));
    }
}
