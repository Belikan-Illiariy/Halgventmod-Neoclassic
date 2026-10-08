package com.nrdhvnstd.halgventmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Шкалы вместо сердец, окорочков и полоски опыта. Над хотбаром, каждая на полширины хотбара,
 * с числами посередине («73/100»).
 * Слева снизу вверх: здоровье, выносливость.
 * Справа: голод с насыщением, жажда (над ней — пузыри воздуха).
 * Полоски: assets/halgventmod/textures/gui/sprites/hud/bar/<цвет>_background|progress.png (из концепта bars.png)
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class NeedsHud {
    private static final int WIDTH = 89;      // половина хотбара (182) минус 2 пикселя просвета посередине
    private static final int HEIGHT = 9;
    private static final int ROW = 11;        // полоска + 2 пикселя просвета
    private static final int XP_SPACE = 6;    // на столько шкалы опускаются на место полоски опыта
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final float TEXT_SCALE = 0.75F; // размер цифр относительно обычного шрифта

    private static final ResourceLocation STAMINA_LAYER = id("stamina");
    private static final ResourceLocation THIRST_LAYER = id("thirst");
    private static final ResourceLocation FATIGUE_LAYER = id("fatigue");

    private static final ResourceLocation HEALTH_BG = id("hud/bar/red_background");
    private static final ResourceLocation HEALTH = id("hud/bar/red_progress");
    private static final ResourceLocation ABSORPTION = id("hud/bar/yellow_progress");
    private static final ResourceLocation DAMAGE = id("hud/bar/white_progress");
    private static final ResourceLocation STAMINA_BG = id("hud/bar/green_background");
    private static final ResourceLocation STAMINA = id("hud/bar/green_progress");
    private static final ResourceLocation HUNGER_BG = id("hud/bar/yellow_background");
    private static final ResourceLocation HUNGER = id("hud/bar/yellow_progress");
    private static final ResourceLocation SATURATION = id("hud/bar/white_progress");
    private static final ResourceLocation THIRST_BG = id("hud/bar/blue_background");
    private static final ResourceLocation THIRST = id("hud/bar/blue_progress");

    private static final ResourceLocation FATIGUE_VIGNETTE = id("textures/misc/fatigue_vignette.png");

    // Только что потерянное здоровье: белый хвост, который догоняет красную полоску
    private static float shownHealth = -1;
    private static long lastHurtMillis;

    private NeedsHud() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        // Опыта в моде нет: ни полоски, ни уровня над ней
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_BAR, (graphics, delta) -> {});
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, (graphics, delta) -> {});
        // Ванильные значки брони не нужны: у мода своя броня
        event.replaceLayer(VanillaGuiLayers.ARMOR_LEVEL, (graphics, delta) -> {});

        event.replaceLayer(VanillaGuiLayers.PLAYER_HEALTH, NeedsHud::renderHealth);
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, STAMINA_LAYER, NeedsHud::renderStamina);
        event.replaceLayer(VanillaGuiLayers.FOOD_LEVEL, NeedsHud::renderHunger);
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, THIRST_LAYER, NeedsHud::renderThirst);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, FATIGUE_LAYER, NeedsHud::renderFatigue);
    }

    private static void renderHealth(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!showsBars(minecraft)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        float max = player.getMaxHealth();
        float health = Math.min(player.getHealth(), max);
        float absorption = player.getAbsorptionAmount();

        long now = System.currentTimeMillis();
        if (shownHealth < 0 || health >= shownHealth) {
            shownHealth = health;
            lastHurtMillis = now;
        } else if (now - lastHurtMillis > 500) {
            // Полсекунды держится, потом за ~0,3 секунды стекает
            shownHealth = Math.max(health, shownHealth - max * delta.getRealtimeDeltaTicks() / 6.0F);
        }

        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - minecraft.gui.leftHeight + shift(player);
        minecraft.gui.leftHeight += ROW;

        RenderSystem.enableBlend();
        graphics.blitSprite(HEALTH_BG, x, y, WIDTH, HEIGHT);
        segment(graphics, DAMAGE, x, y, health / max, shownHealth / max);
        segment(graphics, HEALTH, x, y, 0, health / max);
        segment(graphics, ABSORPTION, x, y, health / max, (health + absorption) / max);
        RenderSystem.disableBlend();
        value(graphics, minecraft, x, y, health, max);
    }

    private static void renderStamina(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!showsBars(minecraft)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - minecraft.gui.leftHeight + shift(player);
        minecraft.gui.leftHeight += ROW - shift(player); // то, что выше, встаёт сразу над шкалой

        // В изнеможении пустая шкала мигает красным
        boolean blink = Stamina.exhausted(player) && player.tickCount / 4 % 2 == 0;
        RenderSystem.enableBlend();
        graphics.blitSprite(blink ? HEALTH_BG : STAMINA_BG, x, y, WIDTH, HEIGHT);
        segment(graphics, STAMINA, x, y, 0, Stamina.get(player) / Stamina.MAX);
        RenderSystem.disableBlend();
        value(graphics, minecraft, x, y, Stamina.get(player), Stamina.MAX);
    }

    private static void renderHunger(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!showsNeeds(minecraft)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        int x = graphics.guiWidth() / 2 + 91 - WIDTH;
        int y = graphics.guiHeight() - minecraft.gui.rightHeight + shift(player);
        minecraft.gui.rightHeight += ROW;

        RenderSystem.enableBlend();
        graphics.blitSprite(HUNGER_BG, x, y, WIDTH, HEIGHT);
        segment(graphics, HUNGER, x, y, 0, (float) Hunger.get(player) / Hunger.MAX);
        // Насыщение — светлый налёт поверх сытости
        graphics.setColor(1.0F, 1.0F, 1.0F, 0.45F);
        segment(graphics, SATURATION, x, y, 0, Hunger.saturation(player) / Hunger.MAX);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        value(graphics, minecraft, x, y, Hunger.get(player), Hunger.MAX);
    }

    private static void renderThirst(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!showsNeeds(minecraft)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        int x = graphics.guiWidth() / 2 + 91 - WIDTH;
        int y = graphics.guiHeight() - minecraft.gui.rightHeight + shift(player);
        minecraft.gui.rightHeight += ROW - shift(player); // пузыри воздуха встают сразу над шкалой

        RenderSystem.enableBlend();
        graphics.blitSprite(THIRST_BG, x, y, WIDTH, HEIGHT);
        segment(graphics, THIRST, x, y, 0, (float) Thirst.get(player) / Thirst.MAX);
        RenderSystem.disableBlend();
        value(graphics, minecraft, x, y, Thirst.get(player), Thirst.MAX);
    }

    // Изнеможение: края экрана темнеют и к концу двух секунд светлеют обратно
    private static void renderFatigue(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int ticks = minecraft.player.getData(ModAttachments.STAMINA).exhaustedTicks();
        if (ticks <= 0) {
            return;
        }
        float strength = Mth.clamp((ticks - delta.getGameTimeDeltaPartialTick(false)) / 15.0F, 0.0F, 1.0F);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, strength);
        graphics.blit(FATIGUE_VIGNETTE, 0, 0, width, height, 0, 0, 256, 256, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    // Верхом на лошади на месте полоски опыта рисуется шкала прыжка — тогда шкалы не опускаем
    private static int shift(LocalPlayer player) {
        return player.jumpableVehicle() == null ? XP_SPACE : 0;
    }

    /** Кусок полоски от from до to (доли 0–1): рисуем её целиком и обрезаем лишнее. */
    private static void segment(GuiGraphics graphics, ResourceLocation sprite, int x, int y, float from, float to) {
        int left = x + Math.round(WIDTH * Mth.clamp(from, 0.0F, 1.0F));
        int right = x + Math.round(WIDTH * Mth.clamp(to, 0.0F, 1.0F));
        if (right <= left) {
            return;
        }
        graphics.enableScissor(left, y, right, y + HEIGHT);
        graphics.blitSprite(sprite, x, y, WIDTH, HEIGHT);
        graphics.disableScissor();
    }

    /** «73/100» посередине шкалы. Округляем вверх: пока есть хоть капля — не ноль. */
    private static void value(GuiGraphics graphics, Minecraft minecraft, int x, int y, float value, float max) {
        String text = Mth.ceil(value) + "/" + Mth.ceil(max);
        // Шрифт уменьшен; центр текста — в центре шкалы (8 — высота строки с тенью)
        graphics.pose().pushPose();
        graphics.pose().translate(x + WIDTH / 2.0F, y + HEIGHT / 2.0F, 0.0F);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        graphics.drawString(minecraft.font, text, -(minecraft.font.width(text) - 1) / 2, -4, TEXT_COLOR, true);
        graphics.pose().popPose();
    }

    // Здоровье и выносливость — там же, где рисовались сердца: в выживании, без скрытого интерфейса
    private static boolean showsBars(Minecraft minecraft) {
        return !minecraft.options.hideGui
                && minecraft.player != null
                && minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
    }

    // Голод и жажда — как окорочки: верхом на лошади их место занимает её здоровье
    private static boolean showsNeeds(Minecraft minecraft) {
        return showsBars(minecraft) && !(minecraft.player.getVehicle() instanceof LivingEntity);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, path);
    }
}
