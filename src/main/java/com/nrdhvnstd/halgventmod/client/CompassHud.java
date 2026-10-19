package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.map.MapIcon;
import com.nrdhvnstd.halgventmod.client.map.PointShapes;
import com.nrdhvnstd.halgventmod.client.map.Waypoint;
import com.nrdhvnstd.halgventmod.client.map.Waypoints;
import com.nrdhvnstd.halgventmod.point.MapPoint;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Полоса азимута вверху экрана, как в Skyrim: видимые VISIBLE_DEGREES из 360 вокруг взгляда игрока.
 * Засечки — каждые 5° (через 15° — длиннее), стороны света — каждые 45° (С, В, Ю, З — крупнее и золотые).
 * Посередине — метка взгляда; градусов на компасе нет. Азимут считается по компасу: север 0°, восток 90°.
 * Метки карты с «Видимостью на компасе» (Waypoint.compass) — значками в своей стороне; если взгляд на значке,
 * под полосой — название метки. Точки карты (MapPoint) — всегда; неоткрытые — белым «?» без названия.
 * К краям полосы всё гаснет. Полоски боссов сдвигаются ниже компаса.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CompassHud {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "compass");

    private static final int WIDTH = 200;
    private static final float VISIBLE_DEGREES = 150; // сколько градусов видно на всей ширине ⚠️
    private static final int TOP = 0;                 // верх полосы — по верхнему краю экрана
    private static final int HEIGHT = 13;
    private static final int TICK_SMALL = 2;          // засечка через 5°
    private static final int TICK_LARGE = 4;          // засечка через 15°
    /**
     * На сколько опускаются полоски боссов: имя первой (в ванили — с 3 пикселей) встаёт сразу под строкой
     * под компасом (название метки — до 2 + 9 пикселей под полосой) с зазором в пиксель.
     */
    private static final int SPACE = TOP + HEIGHT + 2 + 9 + 1 - 3;

    private static final int COLOR_BACK = 0x70000000;
    private static final int COLOR_EDGE = 0x40FFFFFF;
    private static final int COLOR_TICK = 0xFFFFFF;
    private static final int COLOR_LETTER = 0xFFFFFF;
    private static final int COLOR_CARDINAL = 0xFFE8A0;
    private static final int COLOR_MARKER = 0xFFFFD040;
    private static final int COLOR_WAYPOINT = 0xFFFFFFFF;
    private static final int UNKNOWN = PointShapes.UNKNOWN_CELL; // клетка «?» — неоткрытая точка без своего значка
    private static final int ICON = 11;               // значок метки на полосе

    // Стороны света через 45°, начиная с севера
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private CompassHud() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, LAYER, CompassHud::render);
        event.wrapLayer(VanillaGuiLayers.BOSS_OVERLAY, CompassHud::belowCompass);
    }

    /** Ключ перевода стороны света: hud.halgventmod.compass.ne */
    public static String langKey(String direction) {
        return "hud." + HalgventMod.MODID + ".compass." + direction;
    }

    /** На сколько пикселей компас опустил полоски боссов (0 — компаса не видно). */
    public static int bossOffset() {
        return visible(Minecraft.getInstance()) ? SPACE : 0;
    }

    private static boolean visible(Minecraft minecraft) {
        return !minecraft.options.hideGui && minecraft.player != null;
    }

    // Полоски боссов — ниже компаса, чтобы не накрывать его. Пока на их месте надпись
    // («Открытая локация», смена суток — TopBanner), полоски не рисуются и появляются после неё
    private static LayeredDraw.Layer belowCompass(LayeredDraw.Layer bosses) {
        return (GuiGraphics graphics, DeltaTracker delta) -> {
            if (TopBanner.showing()) {
                return;
            }
            if (!visible(Minecraft.getInstance())) {
                bosses.render(graphics, delta);
                return;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, SPACE, 0);
            bosses.render(graphics, delta);
            graphics.pose().popPose();
        };
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!visible(minecraft)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        Font font = minecraft.font;
        // Поворот Minecraft: 0° — юг, 90° — запад, 180° — север. Азимут: 0° — север, 90° — восток
        float bearing = Mth.positiveModulo(player.getViewYRot(delta.getGameTimeDeltaPartialTick(false)) + 180.0F, 360.0F);

        int center = graphics.guiWidth() / 2;
        int left = center - WIDTH / 2;
        int bottom = TOP + HEIGHT;
        float pixelsPerDegree = WIDTH / VISIBLE_DEGREES;
        float half = VISIBLE_DEGREES / 2;

        graphics.fill(left, TOP, left + WIDTH, bottom, COLOR_BACK);
        graphics.fill(left, bottom - 1, left + WIDTH, bottom, COLOR_EDGE);

        graphics.enableScissor(left, TOP, left + WIDTH, bottom);
        int from = Mth.floor((bearing - half) / 5) * 5;
        int to = Mth.ceil((bearing + half) / 5) * 5;
        for (int degree = from; degree <= to; degree += 5) {
            float offset = Mth.wrapDegrees(degree - bearing);
            if (Math.abs(offset) > half) {
                continue;
            }
            int x = Math.round(center + offset * pixelsPerDegree);
            int alpha = fade(offset / half);
            if (alpha == 0) {
                continue;
            }
            int heading = Math.floorMod(degree, 360);
            if (heading % 45 == 0) {
                boolean cardinal = heading % 90 == 0;
                Component letter = Component.translatable(langKey(DIRECTIONS[heading / 45]));
                int color = (cardinal ? COLOR_CARDINAL : COLOR_LETTER) | alpha << 24;
                graphics.drawString(font, letter, x - font.width(letter) / 2 + 1, TOP + 3, color, true);
            } else if (heading % 15 == 0) {
                graphics.fill(x, bottom - 1 - TICK_LARGE, x + 1, bottom - 1, COLOR_TICK | alpha << 24);
            } else {
                graphics.fill(x, bottom - 1 - TICK_SMALL, x + 1, bottom - 1, COLOR_TICK | alpha << 24);
            }
        }

        // Метки с «Видимостью на компасе»: значок в своей стороне; та, на которую смотрят, — с названием
        List<Mark> marks = new ArrayList<>();
        for (Waypoint waypoint : Waypoints.all()) {
            if (waypoint.compass) {
                marks.add(new Mark(waypoint.name, waypoint.x, waypoint.z, waypoint.cell()));
            }
        }
        for (MapPoint point : PointShapes.here()) { // точки видны на компасе всегда
            // Неоткрытая — значок до открытия или белый «?», без названия
            marks.add(PointShapes.discovered(point)
                    ? new Mark(point.name, point.x, point.z, PointShapes.cell(point))
                    : new Mark(null, point.x, point.z, PointShapes.hiddenCell(point)));
        }

        Mark focused = null;
        int focusedX = 0;
        float focusedOffset = Float.MAX_VALUE;
        RenderSystem.enableBlend();
        for (Mark mark : marks) {
            double dx = mark.x() + 0.5 - player.getX();
            double dz = mark.z() + 0.5 - player.getZ();
            float direction = (float) Math.toDegrees(Math.atan2(dx, -dz)); // от севера по часовой
            float offset = Mth.wrapDegrees(direction - bearing);
            if (Math.abs(offset) > half) {
                continue;
            }
            int x = Math.round(center + offset * pixelsPerDegree);
            int alpha = fade(offset / half);
            if (mark.cell() == UNKNOWN) {
                graphics.setColor(1, 1, 1, 1); // прозрачность у «?» — своя, в цвете текста
                PointShapes.drawUnknown(graphics, font, x, TOP + 1 + ICON / 2.0F, ICON, alpha);
                continue; // у неоткрытой точки названия нет
            }
            graphics.setColor(1, 1, 1, alpha / 255.0F);
            MapIcon.draw(graphics, mark.cell(), x - ICON / 2, TOP + 1, ICON);
            if (Math.abs(offset * pixelsPerDegree) <= ICON / 2.0F && Math.abs(offset) < focusedOffset
                    && mark.name() != null) {
                focused = mark;
                focusedX = x;
                focusedOffset = Math.abs(offset);
            }
        }
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        graphics.disableScissor();

        // Метка взгляда; под полосой — название метки или точки, на которую смотрят
        graphics.fill(center, TOP - 1, center + 1, bottom + 1, COLOR_MARKER);
        if (focused != null) {
            graphics.drawCenteredString(font, focused.name(), focusedX, bottom + 2, COLOR_WAYPOINT);
        }
    }

    /** Значок на полосе: метка с «Видимостью на компасе» или точка карты; name null — неоткрытая точка (без названия), cell UNKNOWN — «?». */
    private record Mark(String name, int x, int z, int cell) {}

    // Прозрачность к краям полосы: в середине — непрозрачно, у края — пропадает
    private static int fade(float edge) {
        float opacity = 1 - edge * edge * edge * edge;
        int alpha = Math.round(255 * Mth.clamp(opacity, 0, 1));
        return alpha < 8 ? 0 : alpha; // шрифт рисует почти прозрачный текст непрозрачным
    }
}
