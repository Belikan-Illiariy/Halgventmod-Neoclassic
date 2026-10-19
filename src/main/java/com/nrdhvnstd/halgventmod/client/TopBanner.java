package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.Announcements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Надписи вверху экрана (Announcements): «Открытая локация» с названием и смена суток — одного размера,
 * по середине, на месте полосок боссов и рейдов. Пока надпись на экране, полоски не рисуются (CompassHud);
 * погасли все надписи (сначала локация, потом день) — полоски возвращаются.
 * Появляются за 1 с, держатся 4 с, гаснут за 1,5 с, по одной.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class TopBanner {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "top_banner");

    private static final long FADE_IN = 1000;
    private static final long STAY = 4000;
    private static final long FADE_OUT = 1500;
    private static final float SMALL_SCALE = 1.0F; // подпись ⚠️
    private static final float BIG_SCALE = 1.25F;  // название, дата: чуть крупнее подписи ⚠️
    private static final int LINE_GAP = 3;
    /** Верх имени первой полоски босса в ванили (BossHealthOverlay: полоска на 12, имя на 9 выше). */
    private static final int BOSS_NAME_TOP = 3;

    private TopBanner() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, LAYER, TopBanner::render);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Announcements.clear();
    }

    /** Надпись сейчас на экране — полоски боссов ждут. Погасшую заодно убирает. */
    public static boolean showing() {
        if (Minecraft.getInstance().options.hideGui || Announcements.current() == null) {
            return false;
        }
        if (Announcements.age() >= FADE_IN + STAY + FADE_OUT) {
            Announcements.finish();
            return Announcements.current() != null; // следующая — сразу за ней
        }
        return true;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!showing()) {
            return;
        }
        Announcements.Entry entry = Announcements.current();
        long age = Announcements.age();
        float opacity = age < FADE_IN ? (float) age / FADE_IN
                : age < FADE_IN + STAY ? 1 : 1 - (float) (age - FADE_IN - STAY) / FADE_OUT;
        int alpha = Mth.clamp(Math.round(opacity * 255), 0, 255);
        if (alpha < 8) {
            return; // шрифт рисует почти прозрачный текст непрозрачным
        }

        Font font = Minecraft.getInstance().font;
        float centerX = graphics.guiWidth() / 2.0F;
        float y = CompassHud.bossOffset() + BOSS_NAME_TOP; // на месте первой полоски босса
        for (Announcements.Line line : entry.lines()) {
            float scale = line.big() ? BIG_SCALE : SMALL_SCALE;
            centered(graphics, font, line.text(), centerX, y, scale, line.color() | alpha << 24);
            y += font.lineHeight * scale + LINE_GAP;
        }
    }

    private static void centered(GuiGraphics graphics, Font font, Component text, float x, float y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }
}
