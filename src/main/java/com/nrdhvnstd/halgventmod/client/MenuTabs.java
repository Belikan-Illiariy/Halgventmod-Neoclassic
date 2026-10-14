package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.map.MapScreen;
import com.nrdhvnstd.halgventmod.client.squad.SquadScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Вкладки «Инвентарь», «Персонаж», «Карта», «Отряд», «Кодекс» над окнами игрока и переход между ними.
 * Клавиши: I, P, M, O, J (переназначаются в «Управлении»).
 * - В игре клавиша открывает своё окно (I — ванильная клавиша инвентаря, через InventoryOverride).
 * - В окне-вкладке клавиша другой вкладки переключает на неё, своя — закрывает окно.
 * - Из инвентаря не уйти, пока на курсоре предмет: при закрытии он мог бы выпасть.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class MenuTabs {
    public static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 2;      // между вкладками
    private static final int TEXT_PADDING = 8; // ширина вкладки = самая длинная надпись + отступы

    private MenuTabs() {}

    /** Плашки вкладок у верхнего края экрана: одинаковой ширины, по центру окна. */
    public static List<MenuTabButton> createButtons(Font font, int windowLeft, int windowWidth, MenuTab current) {
        int width = 0;
        for (MenuTab tab : MenuTab.values()) {
            width = Math.max(width, font.width(tab.title()) + TEXT_PADDING);
        }
        int count = MenuTab.values().length;
        int x = windowLeft + (windowWidth - (count * width + (count - 1) * TAB_GAP)) / 2;
        int y = 0; // вплотную к верхней границе экрана
        List<MenuTabButton> buttons = new ArrayList<>(count);
        for (MenuTab tab : MenuTab.values()) {
            buttons.add(new MenuTabButton(x, y, width, TAB_HEIGHT, tab, tab == current));
            x += width + TAB_GAP;
        }
        return buttons;
    }

    /** Открыть окно вкладки вместо текущего. false — если сейчас нельзя. */
    public static boolean open(MenuTab tab) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }
        if (tab == MenuTab.INVENTORY) {
            InventoryOverride.requestInventory(); // окно сменится, когда сервер откроет меню
            return true;
        }
        if (player.containerMenu != player.inventoryMenu) {
            if (!player.containerMenu.getCarried().isEmpty()) {
                return false;
            }
            player.closeContainer(); // сервер узнаёт, что инвентарь закрыт
        }
        minecraft.setScreen(switch (tab) {
            case CHARACTER -> new CharacterScreen();
            case MAP -> new MapScreen();
            case SQUAD -> new SquadScreen();
            default -> new TabPlaceholderScreen(tab);
        });
        return true;
    }

    // Клавиши P, M, O, J в игре, когда никакое окно не открыто
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        for (MenuTab tab : MenuTab.values()) {
            if (tab == MenuTab.INVENTORY) {
                continue;
            }
            while (tab.key().consumeClick()) {
                if (minecraft.screen == null) {
                    open(tab);
                }
            }
        }
    }

    // Клавиши вкладок внутри окон-вкладок
    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        MenuTab current = currentTab(event.getScreen());
        if (current == null) {
            return;
        }
        for (MenuTab tab : MenuTab.values()) {
            if (!tab.key().matches(event.getKeyCode(), event.getScanCode())) {
                continue;
            }
            if (tab != current) {
                open(tab);
                event.setCanceled(true);
            } else if (tab != MenuTab.INVENTORY) { // клавиша инвентаря в инвентаре закрывает его и так
                event.getScreen().onClose();
                event.setCanceled(true);
            }
            return;
        }
    }

    private static MenuTab currentTab(Screen screen) {
        return screen instanceof MenuTabScreen tabScreen ? tabScreen.tab() : null;
    }
}
