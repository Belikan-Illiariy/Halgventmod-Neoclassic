package com.nrdhvnstd.halgventmod.client.map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.banner.BannerRegistry;
import com.nrdhvnstd.halgventmod.client.ScrollText;
import com.nrdhvnstd.halgventmod.client.banner.BannerDraw;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Панель описания открытой точки слева на карте (карта справа остаётся живой):
 * вверху знамя из реестра, справа от него название и описание; ниже до конца панели — «Сведения».
 * Описание и сведения листаются колёсиком, когда курсор над ними. Точку берёт по id из точек на клиенте
 * каждый кадр: администрация поправила текст — видно сразу; точку удалили или сняли «с описанием» — панель закрывается.
 */
final class PointInfoPanel {
    private static final int MAX_WIDTH = 320;          // ⚠️
    private static final float WIDTH_SHARE = 0.45F;    // ⚠️ от ширины экрана
    private static final int PAD = 8;
    private static final int BANNER_WIDTH = 60;        // ⚠️ знамя 60 × 120
    private static final int TITLE_GAP = 8;            // между знаменем и текстом
    private static final float TITLE_SCALE = 1.25F;
    private static final int TITLE_HEIGHT = 16;        // строка названия и черта под ней
    private static final int CLOSE_SIZE = 11;
    private static final int INFO_GAP = 8;             // между верхней частью и «Сведениями»
    private static final int INFO_HEADER = 14;

    private static final int COLOR_PANEL = 0xF0241C14;
    private static final int COLOR_FRAME = 0xFF6E6250;
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_DIM = 0xFFB8A888;
    private static final int COLOR_HOVER = 0xFF4A3E2E;
    private static final int COLOR_INFO = 0x30000000;

    private final int pointId;
    private final ScrollText description = new ScrollText(false, COLOR_TEXT, COLOR_DIM);
    private final ScrollText info = new ScrollText(true, COLOR_TEXT, COLOR_DIM);
    private int left;
    private int top;
    private int width;
    private int height;

    PointInfoPanel(int pointId) {
        this.pointId = pointId;
    }

    int pointId() {
        return pointId;
    }

    private static int widthFor(int screenWidth) {
        return Math.min(MAX_WIDTH, Math.round(screenWidth * WIDTH_SHARE));
    }

    /** Место панели: от left, по высоте от top до bottom; правее maxRight не заходит (там правая панель). */
    void place(int left, int top, int bottom, int maxRight, int screenWidth) {
        this.left = left;
        this.top = top;
        this.width = Math.min(widthFor(screenWidth), maxRight - left);
        this.height = bottom - top;
    }

    /** Точка панели, если её ещё можно показывать; null — панель пора закрыть. */
    @Nullable
    MapPoint point() {
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.id == pointId) {
                return point.described && PointShapes.discovered(point) ? point : null;
            }
        }
        return null;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private int closeX() {
        return left + width - PAD - CLOSE_SIZE;
    }

    private int closeY() {
        return top + PAD - 2;
    }

    boolean overClose(double mouseX, double mouseY) {
        return mouseX >= closeX() && mouseX < closeX() + CLOSE_SIZE && mouseY >= closeY() && mouseY < closeY() + CLOSE_SIZE;
    }

    /** Колёсико над панелью: листает текст под курсором; в любом случае карту под панелью не масштабирует. */
    void scroll(double mouseX, double mouseY, double amount) {
        if (description.contains(mouseX, mouseY)) {
            description.scroll(amount);
        } else if (info.contains(mouseX, mouseY)) {
            info.scroll(amount);
        }
    }

    void render(GuiGraphics graphics, Font font, MapPoint point, int mouseX, int mouseY) {
        graphics.fill(left, top, left + width, top + height, COLOR_PANEL);
        graphics.renderOutline(left, top, width, height, COLOR_FRAME);

        // Знамя: нет знамени или его убрали из реестра — место пустое
        int bannerX = left + PAD;
        int bannerY = top + PAD;
        BannerDraw.draw(graphics, BannerRegistry.client(point.banner), bannerX, bannerY, BANNER_WIDTH);

        // Название крупнее, цветом заголовка, черта под ним; справа — [×]
        int textX = bannerX + BANNER_WIDTH + TITLE_GAP;
        int textWidth = left + width - PAD - textX;
        int titleWidth = closeX() - 4 - textX;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, top + PAD, 0);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE, 1);
        graphics.drawString(font, font.plainSubstrByWidth(point.name, (int) (titleWidth / TITLE_SCALE)), 0, 0, COLOR_TITLE, false);
        graphics.pose().popPose();
        int lineY = top + PAD + TITLE_HEIGHT - 3;
        graphics.fill(textX, lineY, textX + textWidth, lineY + 1, COLOR_FRAME);

        boolean closeHovered = overClose(mouseX, mouseY);
        if (closeHovered) {
            graphics.fill(closeX(), closeY(), closeX() + CLOSE_SIZE, closeY() + CLOSE_SIZE, COLOR_HOVER);
        }
        graphics.drawCenteredString(font, "×", closeX() + CLOSE_SIZE / 2 + 1, closeY() + 2, closeHovered ? COLOR_TITLE : COLOR_TEXT);

        // Описание — от черты до низа знамени
        int descriptionTop = top + PAD + TITLE_HEIGHT;
        int upperBottom = bannerY + 2 * BANNER_WIDTH;
        description.set(font, point.description, textX, descriptionTop, textWidth, upperBottom - descriptionTop);
        description.render(graphics, font);

        // «Сведения» — рамка до низа панели
        int infoTop = upperBottom + INFO_GAP;
        int infoBottom = top + height - PAD;
        if (infoBottom - infoTop > INFO_HEADER) {
            int infoLeft = left + PAD;
            int infoRight = left + width - PAD;
            graphics.fill(infoLeft, infoTop, infoRight, infoBottom, COLOR_INFO);
            graphics.renderOutline(infoLeft, infoTop, infoRight - infoLeft, infoBottom - infoTop, COLOR_FRAME);
            graphics.drawString(font, Component.translatable("gui.halgventmod.point.info"), infoLeft + 5, infoTop + 5,
                    COLOR_TITLE, false);
            info.set(font, point.info, infoLeft + 5, infoTop + 5 + INFO_HEADER, infoRight - infoLeft - 8,
                    infoBottom - infoTop - 10 - INFO_HEADER);
            info.render(graphics, font);
        }
    }
}
