package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Всплывающий хотбар (ТЗ 5.2): по умолчанию скрыт, выезжает снизу при прокрутке колёсика или 1–9
 * и уезжает через HOTBAR_HIDE_DELAY без них. Колёсико листает сразу (ваниль), «холостого» поворота нет.
 * Пока открыто окно с ячейками, хотбар не рисуется — карманы видны в самом окне.
 * Шкалы потребностей (NeedsHud) прячутся и выезжают вместе с хотбаром (visible). Урон тоже показывает хотбар.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class HotbarPopup {
    public static final int HOTBAR_HIDE_DELAY = 60; // тиков без прокрутки до того, как хотбар уедет ⚠️
    private static final int SLIDE_TICKS = 4;       // выезд и уезд
    private static final int HIDDEN_OFFSET = 24;    // на сколько пикселей уезжает вниз (хотбар 22 + запас)

    private static int idleTicks = HOTBAR_HIDE_DELAY; // с последней прокрутки
    private static int lastSelected = -1;
    private static float lastHealth = -1;
    private static int lastHurtTime;
    private static float shown;                     // 0 — спрятан, 1 — на месте
    private static float shownBefore;

    private HotbarPopup() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.HOTBAR, HotbarPopup::wrap);
    }

    private static LayeredDraw.Layer wrap(LayeredDraw.Layer hotbar) {
        return (GuiGraphics graphics, DeltaTracker delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.isSpectator()) {
                hotbar.render(graphics, delta); // у наблюдателя на месте хотбара — своё меню
                return;
            }
            if (minecraft.screen instanceof AbstractContainerScreen<?>) {
                return;
            }
            float visible = visible(delta);
            if (visible <= 0) {
                return;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, (1 - visible) * HIDDEN_OFFSET, 0);
            hotbar.render(graphics, delta);
            graphics.pose().popPose();
        };
    }

    /** Насколько хотбар выехал в этом кадре: 0 — спрятан, 1 — на месте. По нему же прячутся шкалы (NeedsHud). */
    public static float visible(DeltaTracker delta) {
        return Mth.lerp(delta.getGameTimeDeltaPartialTick(false), shownBefore, shown);
    }

    /** Показать хотбар заново на HOTBAR_HIDE_DELAY. */
    public static void wake() {
        idleTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            lastSelected = -1;
            lastHealth = -1;
            return;
        }
        // Урон: здоровье упало или игрок дёрнулся от удара (удар мог уйти в поглощение)
        float health = player.getHealth();
        if ((lastHealth >= 0 && health < lastHealth) || player.hurtTime > lastHurtTime) {
            wake();
        }
        lastHealth = health;
        lastHurtTime = player.hurtTime;

        int selected = player.getInventory().selected;
        if (lastSelected >= 0 && selected != lastSelected) {
            wake();
        }
        lastSelected = selected;
        // 1–9 по уже выбранному карману ячейку не меняют, но хотбар показать должны
        for (KeyMapping key : minecraft.options.keyHotbarSlots) {
            if (key.isDown() && minecraft.screen == null) {
                wake();
            }
        }
        if (idleTicks < HOTBAR_HIDE_DELAY) {
            idleTicks++;
        }
        shownBefore = shown;
        float target = idleTicks < HOTBAR_HIDE_DELAY ? 1 : 0;
        shown = Mth.approach(shown, target, 1.0F / SLIDE_TICKS);
    }

    // Колёсико в мире: ваниль тут же листает карманы, а мы показываем хотбар
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (Minecraft.getInstance().screen == null) {
            wake();
        }
    }
}
