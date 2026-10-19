package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Как открывается календарь:
 * - K в игре — открыть;
 * - K в инвентаре или хранилище (сундук, печь, верстак...) — закрыть его и открыть календарь,
 *   но только если не печатаешь текст и на курсоре нет предмета;
 * - кнопка с часами над окном инвентаря.
 * В остальных окнах K остаётся обычной буквой.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CalendarInput {

    private CalendarInput() {}

    // K в игре, когда никакое окно не открыто
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (ModKeys.CALENDAR.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new CalendarScreen());
            }
        }
    }

    // K в инвентаре или хранилище
    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!ModKeys.CALENDAR.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || isTyping(screen)) {
            return;
        }
        if (openFromContainer()) {
            event.setCanceled(true);
        }
    }

    /** Закрыть открытое окно хранилища и открыть календарь. false — если сейчас нельзя. */
    public static boolean openFromContainer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.containerMenu.getCarried().isEmpty()) {
            return false; // предмет на курсоре: при закрытии он мог бы выпасть
        }
        minecraft.player.closeContainer(); // сервер узнаёт, что сундук закрыт
        minecraft.setScreen(new CalendarScreen());
        return true;
    }

    // Печатает ли игрок сейчас текст: поиск в творческом инвентаре, переименование на наковальне и т.п.
    private static boolean isTyping(Screen screen) {
        // В открытой книге рецептов есть поле поиска — не перехватываем K, пока она открыта
        if (screen instanceof RecipeUpdateListener listener && listener.getRecipeBookComponent().isVisible()) {
            return true;
        }
        if (screen.getFocused() instanceof EditBox) {
            return true;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused()) {
                return true;
            }
        }
        return false;
    }

    // Кнопка внизу панели предмета в окне инвентаря
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof ArmorInventoryScreen screen) {
            event.addListener(new CalendarButton(screen.getGuiLeft() + ArmorInventoryScreen.CALENDAR_X,
                    screen.getGuiTop() + ArmorInventoryScreen.CALENDAR_Y));
        }
    }
}