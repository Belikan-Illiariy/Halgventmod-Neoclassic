package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.OpenInventoryPayload;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
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
