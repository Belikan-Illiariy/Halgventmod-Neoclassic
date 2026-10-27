package com.nrdhvnstd.halgventmod.client.social;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.social.Restraint;
import com.nrdhvnstd.halgventmod.social.SocialAction;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Шкала применения (ТЗ 4) по SocialProgressPayload: у жертвы — красная полоска в серой оболочке над шкалами
 * здоровья и голода, у атакующего — такая же над головой жертвы. Подпись над полоской — что делают («Обыск»).
 * Над головой каждого связанного игрока — значки мешка, кандалов, наручников (маска Restraint),
 * над поверженным — тёмно-красная полоска оставшегося времени с подписью «Повержен» (docs/TZ_downed_and_hp.md, 9).
 * Сервер шлёт прогресс раз в 2 тика, полоска догоняет его плавно.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class SocialBars {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "social_bar");

    private static final int TIMEOUT = 40;          // пакеты перестали приходить (атакующий вышел) — полоска пропадает
    private static final float CATCH_UP = 0.6F;     // доля разрыва до прогресса сервера, которую полоска проходит за тик

    // На экране жертвы
    private static final int HUD_WIDTH = 182;       // ширина хотбара
    private static final int HUD_HEIGHT = 5;
    private static final int HUD_GAP = 3;           // над шкалами
    // Над головой (в пикселях подписи, 1/40 блока)
    private static final int WORLD_WIDTH = 50;
    private static final int WORLD_HEIGHT = 4;
    private static final float WORLD_SCALE = 0.025F;   // как ванильная подпись имени
    private static final double ABOVE_NAME = 0.35;     // над подписью имени, блоков
    private static final double ICONS_DISTANCE = 16;   // значки ограничений видно с этого расстояния ⚠️
    private static final int ICON_SIZE = 9;

    // Цвета (ARGB)
    private static final int COLOR_FRAME = 0xFF8A8A8A;
    private static final int COLOR_EMPTY = 0xFF2A2A2A;
    private static final int COLOR_FILL = 0xFFC03030;
    private static final int COLOR_DOWNED = 0xFF701818;   // остаток «Повержен»
    private static final int COLOR_LABEL = 0xFFFFFFFF;

    /** Шкала одного атакующего. */
    private static final class Bar {
        int targetId;
        SocialAction action;
        float server;
        float shown;
        float previous;
        int age;
    }

    private static final Map<Integer, Bar> BARS = new HashMap<>();

    private SocialBars() {}

    /** SocialProgressPayload. */
    public static void progress(int attackerId, int targetId, SocialAction action, float progress) {
        Bar bar = BARS.computeIfAbsent(attackerId, id -> new Bar());
        bar.targetId = targetId;
        bar.action = action;
        bar.server = progress;
        bar.age = 0;
    }

    /** SocialEndPayload. */
    public static void end(int attackerId) {
        BARS.remove(attackerId);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Iterator<Bar> it = BARS.values().iterator();
        while (it.hasNext()) {
            Bar bar = it.next();
            if (++bar.age > TIMEOUT) {
                it.remove();
                continue;
            }
            bar.previous = bar.shown;
            bar.shown += (bar.server - bar.shown) * CATCH_UP;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BARS.clear();
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        // После всех шкал: leftHeight и rightHeight уже до верхней
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, LAYER, SocialBars::renderHud);
    }

    // Полоска жертвы над её шкалами
    private static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        int self = minecraft.player.getId();
        for (Bar bar : BARS.values()) {
            if (bar.targetId != self) {
                continue;
            }
            Font font = minecraft.font;
            int x = (graphics.guiWidth() - HUD_WIDTH) / 2;
            int y = graphics.guiHeight() - Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight) - HUD_GAP - HUD_HEIGHT;
            int fill = Math.round((HUD_WIDTH - 2) * shown(bar, delta.getGameTimeDeltaPartialTick(false)));
            graphics.fill(x, y, x + HUD_WIDTH, y + HUD_HEIGHT, COLOR_FRAME);
            graphics.fill(x + 1, y + 1, x + HUD_WIDTH - 1, y + HUD_HEIGHT - 1, COLOR_EMPTY);
            graphics.fill(x + 1, y + 1, x + 1 + fill, y + HUD_HEIGHT - 1, COLOR_FILL);
            graphics.drawCenteredString(font, Component.translatable(bar.action.barKey()),
                    graphics.guiWidth() / 2, y - font.lineHeight - 1, COLOR_LABEL);
            return; // несколько атакующих сразу — видна одна полоска
        }
    }

    private static float shown(Bar bar, float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, bar.previous, bar.shown), 0, 1);
    }

    // Над головой: полоска «Повержен» и значки ограничений (всем рядом), у атакующего — его полоска между ними
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = event.getEntity();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        Bar bar = BARS.get(minecraft.player.getId());
        boolean ownBar = bar != null && bar.targetId == player.getId();
        int mask = player.getData(ModAttachments.RESTRAINTS);
        boolean near = player.distanceToSqr(minecraft.player) < ICONS_DISTANCE * ICONS_DISTANCE;
        boolean icons = mask != 0 && near;
        boolean downed = near && player != minecraft.player && Downed.isDowned(player);
        if (!ownBar && !icons && !downed) {
            return;
        }
        float partialTick = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        Vec3 tag = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, player.getViewYRot(partialTick));
        pose.pushPose();
        pose.translate(0, (tag != null ? tag.y : player.getBbHeight()) + 0.5 + ABOVE_NAME, 0);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);
        Matrix4f matrix = pose.last().pose();
        int light = LightTexture.FULL_BRIGHT;
        Font font = minecraft.font;

        int top = 0; // снизу вверх: «Повержен», полоска действия, значки
        if (downed) {
            top = labelledBar(buffers, matrix, font, top, Downed.ticksLeft(player) / (float) Downed.DOWNED_TICKS,
                    COLOR_DOWNED, Component.translatable("hud.halgventmod.downed.label"), light);
        }
        if (ownBar) {
            top = labelledBar(buffers, matrix, font, top, shown(bar, partialTick), COLOR_FILL,
                    Component.translatable(bar.action.barKey()), light);
        }
        if (icons) {
            int count = Integer.bitCount(mask);
            int x = -count * ICON_SIZE / 2;
            int y = top - ICON_SIZE - 1;
            for (Restraint restraint : Restraint.values()) {
                if (restraint.on(player)) {
                    icon(buffers, matrix, minecraft.getMobEffectTextures().get(restraint.effect()), x, y, light);
                    x += ICON_SIZE;
                }
            }
        }
        pose.popPose();
    }

    // Полоска с подписью над ней, низом — над top; возвращает новый верх
    private static int labelledBar(MultiBufferSource buffers, Matrix4f matrix, Font font, int top, float progress, int color,
                                   Component label, int light) {
        int y = top - WORLD_HEIGHT;
        int left = -WORLD_WIDTH / 2;
        int fill = Math.round((WORLD_WIDTH - 2) * Mth.clamp(progress, 0, 1));
        VertexConsumer quads = buffers.getBuffer(RenderType.textBackground());
        // Рамка и заливка — не внахлёст: иначе одна просвечивает сквозь другую
        rect(quads, matrix, left, y, left + WORLD_WIDTH, y + 1, COLOR_FRAME, light);
        rect(quads, matrix, left, y + WORLD_HEIGHT - 1, left + WORLD_WIDTH, y + WORLD_HEIGHT, COLOR_FRAME, light);
        rect(quads, matrix, left, y + 1, left + 1, y + WORLD_HEIGHT - 1, COLOR_FRAME, light);
        rect(quads, matrix, left + WORLD_WIDTH - 1, y + 1, left + WORLD_WIDTH, y + WORLD_HEIGHT - 1, COLOR_FRAME, light);
        rect(quads, matrix, left + 1, y + 1, left + 1 + fill, y + WORLD_HEIGHT - 1, color, light);
        rect(quads, matrix, left + 1 + fill, y + 1, left + WORLD_WIDTH - 1, y + WORLD_HEIGHT - 1, COLOR_EMPTY, light);
        int labelY = y - font.lineHeight - 1;
        font.drawInBatch(label, -font.width(label) / 2.0F, labelY, COLOR_LABEL, false, matrix, buffers,
                Font.DisplayMode.NORMAL, 0, light);
        return labelY;
    }

    private static void icon(MultiBufferSource buffers, Matrix4f matrix, TextureAtlasSprite sprite, int x, int y, int light) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(sprite.atlasLocation()));
        float x1 = x + ICON_SIZE;
        float y1 = y + ICON_SIZE;
        consumer.addVertex(matrix, x, y, 0).setColor(-1).setUv(sprite.getU0(), sprite.getV0()).setLight(light);
        consumer.addVertex(matrix, x, y1, 0).setColor(-1).setUv(sprite.getU0(), sprite.getV1()).setLight(light);
        consumer.addVertex(matrix, x1, y1, 0).setColor(-1).setUv(sprite.getU1(), sprite.getV1()).setLight(light);
        consumer.addVertex(matrix, x1, y, 0).setColor(-1).setUv(sprite.getU1(), sprite.getV0()).setLight(light);
    }

    // Обход вершин — как у букв подписи (BakedGlyph), чтобы прямоугольник смотрел в камеру
    private static void rect(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, int color, int light) {
        if (x1 <= x0) {
            return;
        }
        consumer.addVertex(matrix, x0, y0, 0).setColor(color).setLight(light);
        consumer.addVertex(matrix, x0, y1, 0).setColor(color).setLight(light);
        consumer.addVertex(matrix, x1, y1, 0).setColor(color).setLight(light);
        consumer.addVertex(matrix, x1, y0, 0).setColor(color).setLight(light);
    }
}
