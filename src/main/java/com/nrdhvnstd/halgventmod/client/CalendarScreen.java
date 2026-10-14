package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.Calendar;
import com.nrdhvnstd.halgventmod.world.DayLength;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Окно календаря на текстуре textures/gui/calendar.png (лист 512 × 512):
 * на свитке — значок сезона, месяц и год; верхний ряд ячеек — дни недели; ниже 4 недели по 8 дней.
 * Сегодня — золотая рамка, день под курсором — светлая рамка, день с событием — сургучная печать.
 * Под окном — сегодняшняя дата, длина дня и время до заката или рассвета.
 * Листание: стрелки или ← → — на месяц, с Shift — на год, с Ctrl — на 10 лет, Home — к сегодня.
 */
public class CalendarScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "textures/gui/calendar.png");
    private static final int TEXTURE_SIZE = 512;

    /** Кусок листа текстуры: левый верхний угол и размер. */
    private record Part(int u, int v, int width, int height) {}

    // ===== Куски листа =====
    private static final Part WINDOW = new Part(0, 11, 316, 199);
    private static final Part ARROW_RIGHT = new Part(330, 0, 23, 18);
    private static final Part ARROW_RIGHT_HOVER = new Part(356, 0, 23, 18);
    private static final Part ARROW_LEFT = new Part(330, 22, 23, 18);
    private static final Part ARROW_LEFT_HOVER = new Part(356, 22, 23, 18);
    private static final Part FRAME_TODAY = new Part(331, 48, 32, 32);
    private static final Part FRAME_HOVER = new Part(367, 48, 32, 32);
    private static final Part SEAL = new Part(403, 49, 11, 10);
    private static final Part[] SEASON_ICONS = {        // в порядке Calendar.Season
            new Part(331, 86, 14, 14),   // весна — росток
            new Part(348, 84, 16, 16),   // лето — солнце
            new Part(367, 84, 14, 16),   // осень — лист
            new Part(384, 84, 15, 15)};  // зима — снежинка

    // ===== Разметка внутри окна (от левого верхнего угла окна) =====
    private static final int COLUMNS = Calendar.DAYS_PER_WEEK;
    private static final int GRID_X = 15;          // левый край первой ячейки
    private static final int CELL = 32;            // ячейка дня 32 × 32
    private static final int STEP = 36;            // ячейка + промежуток
    private static final int HEADER_Y = 23;        // ряд дней недели
    private static final int HEADER_H = 24;
    private static final int WEEKS_Y = 51;         // первая неделя
    private static final int TITLE_CENTER_X = 158; // середина свитка
    private static final int TITLE_Y = 7;
    private static final int ARROW_LEFT_X = 36;
    private static final int ARROW_RIGHT_X = 261;
    private static final int ARROW_Y = 1;
    private static final int FOOTER_Y = WINDOW.height() + 6; // строки под окном
    private static final int TOTAL_HEIGHT = FOOTER_Y + 3 * 12;
    private static final int TOOLTIP_WIDTH = 200;

    // ===== Цвета текста (ARGB) =====
    private static final int COLOR_TITLE = 0xFF3B2A1A;         // тёмный на свитке
    private static final int COLOR_WEEKDAY = 0xFFB8A888;
    private static final int COLOR_DAY = 0xFFE8DCC0;
    private static final int COLOR_DAY_PAST = 0xFF7A6A50;
    private static final int COLOR_DAY_BEFORE_START = 0xFF443A2C; // дни до начала мира
    private static final int COLOR_FOOTER = 0xFFE8DCC0;
    private static final int COLOR_FOOTER_DIM = 0xFFB8A888;

    // Дальше этого листать некуда — защита от переполнения
    private static final int MAX_MONTH_INDEX = Calendar.MONTHS_PER_YEAR * 10000;

    private int left;
    private int top;
    private int viewYear;  // показанный месяц: настоящий год и месяц с единицы
    private int viewMonth;

    public CalendarScreen() {
        super(Component.translatable("calendar.halgventmod.screen.title"));
        showToday();
    }

    @Override
    protected void init() {
        left = (width - WINDOW.width()) / 2;
        top = (height - TOTAL_HEIGHT) / 2;
        Tooltip hint = Tooltip.create(Component.translatable("calendar.halgventmod.screen.arrows"));

        ArrowButton back = new ArrowButton(left + ARROW_LEFT_X, top + ARROW_Y, ARROW_LEFT, ARROW_LEFT_HOVER,
                Component.literal("<"), () -> shiftMonth(-step()));
        ArrowButton forward = new ArrowButton(left + ARROW_RIGHT_X, top + ARROW_Y, ARROW_RIGHT, ARROW_RIGHT_HOVER,
                Component.literal(">"), () -> shiftMonth(step()));
        back.setTooltip(hint);
        forward.setTooltip(hint);
        addRenderableWidget(back);
        addRenderableWidget(forward);
    }

    // Мир не встаёт на паузу: время, голод и жажда идут дальше
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.CALENDAR.matches(keyCode, scanCode)) {
            onClose(); // K закрывает так же, как Esc
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> shiftMonth(-step());
            case GLFW.GLFW_KEY_RIGHT -> shiftMonth(step());
            case GLFW.GLFW_KEY_HOME -> showToday();
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    // Обычно на месяц, с Shift — на год, с Ctrl — на 10 лет
    private static int step() {
        if (hasControlDown()) {
            return Calendar.MONTHS_PER_YEAR * 10;
        }
        if (hasShiftDown()) {
            return Calendar.MONTHS_PER_YEAR;
        }
        return 1;
    }

    private void shiftMonth(int delta) {
        int start = Calendar.monthIndex(Calendar.START_YEAR, Calendar.START_MONTH);
        int index = Math.clamp((long) Calendar.monthIndex(viewYear, viewMonth) + delta, start, MAX_MONTH_INDEX);
        viewYear = Calendar.START_YEAR + index / Calendar.MONTHS_PER_YEAR;
        viewMonth = index % Calendar.MONTHS_PER_YEAR + 1;
    }

    private void showToday() {
        Calendar.Date today = Calendar.date(Calendar.clientDay);
        viewYear = today.year();
        viewMonth = today.month();
    }

    // ----- Отрисовка -----

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blit(graphics, WINDOW, left, top);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); // фон и стрелки
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        long today = Calendar.clientDay;
        long firstDay = Calendar.toDay(viewYear, viewMonth, 1);
        Calendar.Season season = Calendar.season(viewMonth);

        // Свиток: значок сезона и «Декабрь 1384 г.», вместе по центру
        Component title = Component.translatable("calendar.halgventmod.screen.header",
                Component.translatable(Calendar.monthKey(viewMonth)), viewYear);
        Part icon = SEASON_ICONS[season.ordinal()];
        int groupWidth = icon.width() + 4 + font.width(title);
        int groupX = left + TITLE_CENTER_X - groupWidth / 2;
        int iconY = top + TITLE_Y + 4 - icon.height() / 2;
        blit(graphics, icon, groupX, iconY);
        graphics.drawString(font, title, groupX + icon.width() + 4, top + TITLE_Y, COLOR_TITLE, false);
        boolean seasonHovered = isInside(mouseX, mouseY, groupX, iconY, icon.width(), icon.height());

        // Дни недели — первые три буквы названия
        for (int col = 0; col < COLUMNS; col++) {
            String name = Component.translatable(Calendar.weekdayKey(col + 1)).getString();
            String shortName = name.length() > 3 ? name.substring(0, 3) : name;
            int x = left + GRID_X + col * STEP;
            graphics.drawString(font, shortName, x + (CELL - font.width(shortName)) / 2,
                    top + HEADER_Y + (HEADER_H - 8) / 2, COLOR_WEEKDAY, false);
        }

        // Недели: число, рамка «сегодня», рамка под курсором, печать события
        long hoveredEventDay = -1;
        for (int i = 0; i < Calendar.DAYS_PER_MONTH; i++) {
            int x = left + GRID_X + (i % COLUMNS) * STEP;
            int y = top + WEEKS_Y + (i / COLUMNS) * STEP;
            long day = firstDay + i;
            boolean hovered = isInside(mouseX, mouseY, x, y, CELL, CELL);
            boolean hasEvents = day >= 0 && Calendar.clientEvents.containsKey(day);

            if (day == today) {
                blit(graphics, FRAME_TODAY, x, y);
            }
            if (hovered && day >= 0) {
                blit(graphics, FRAME_HOVER, x, y);
            }
            if (hasEvents) {
                blit(graphics, SEAL, x + CELL - SEAL.width() - 2, y + 2);
                if (hovered) {
                    hoveredEventDay = day;
                }
            }

            String number = Integer.toString(i + 1);
            int color = day < 0 ? COLOR_DAY_BEFORE_START : day < today ? COLOR_DAY_PAST : COLOR_DAY;
            graphics.drawString(font, number, x + (CELL - font.width(number)) / 2, y + (CELL - 8) / 2, color, false);
        }

        renderFooter(graphics, today);

        // Подсказки рисуются последними, поверх всего, и пропадают, как только курсор уйдёт
        if (hoveredEventDay >= 0) {
            graphics.renderTooltip(font, eventTooltip(hoveredEventDay), mouseX, mouseY);
        } else if (seasonHovered) {
            graphics.renderTooltip(font, season.displayName(), mouseX, mouseY);
        }
    }

    // Под окном — всегда про сегодня, какой бы месяц ни был открыт
    private void renderFooter(GuiGraphics graphics, long today) {
        Calendar.Date now = Calendar.date(today);
        int y = top + FOOTER_Y;
        centered(graphics, Component.translatable("calendar.halgventmod.screen.today",
                now.weekdayName(), now.dayOfMonth(), now.monthNameOf()), y, COLOR_FOOTER);

        int dayMinutes = (int) Math.round(Calendar.dayShare(now.dayOfYear()) * DayLength.DAY_LENGTH_TICKS / 1200.0);
        int nightMinutes = DayLength.DAY_LENGTH_TICKS / 1200 - dayMinutes;
        centered(graphics, Component.translatable("calendar.halgventmod.screen.daylight",
                duration(dayMinutes), duration(nightMinutes)), y + 12, COLOR_FOOTER_DIM);

        if (minecraft != null && minecraft.level != null) {
            long time = Math.floorMod(minecraft.level.getDayTime(), (long) Calendar.TIME_PER_DAY);
            boolean daytime = time < Calendar.TIME_PER_DAY / 2;
            long remaining = (daytime ? Calendar.TIME_PER_DAY / 2 : Calendar.TIME_PER_DAY) - time;
            double ticks = remaining / DayLength.rate(time, now.dayOfYear());
            int minutes = (int) Math.ceil(ticks / 1200.0);
            centered(graphics, Component.translatable(daytime
                            ? "calendar.halgventmod.screen.until_sunset"
                            : "calendar.halgventmod.screen.until_sunrise", duration(minutes)),
                    y + 24, COLOR_FOOTER_DIM);
        }
    }

    // «3 декабрь 1384 г.», затем по каждому событию — название и пояснение
    private List<FormattedCharSequence> eventTooltip(long day) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Calendar.dateText(day).withStyle(ChatFormatting.GOLD).getVisualOrderText());
        for (Calendar.Event event : Calendar.clientEvents.getOrDefault(day, List.of())) {
            lines.add(Component.literal(event.title()).withStyle(ChatFormatting.WHITE).getVisualOrderText());
            if (!event.description().isEmpty()) {
                lines.addAll(font.split(Component.literal(event.description()).withStyle(ChatFormatting.GRAY),
                        TOOLTIP_WIDTH));
            }
        }
        return lines;
    }

    // ----- Вспомогательное -----

    private static void blit(GuiGraphics graphics, Part part, int x, int y) {
        graphics.blit(TEXTURE, x, y, part.u(), part.v(), part.width(), part.height(), TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // Под окном текст лежит на размытом мире, поэтому с тенью
    private void centered(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, text, left + (WINDOW.width() - font.width(text)) / 2, y, color, true);
    }

    // «1 ч 5 мин» или «45 мин»
    private static Component duration(int minutes) {
        return minutes >= 60
                ? Component.translatable("calendar.halgventmod.duration.hours", minutes / 60, minutes % 60)
                : Component.translatable("calendar.halgventmod.duration.minutes", minutes);
    }

    /** Стрелка с листа текстуры: обычная и подсвеченная под курсором. */
    private static final class ArrowButton extends AbstractButton {
        private final Part normal;
        private final Part hover;
        private final Runnable action;

        ArrowButton(int x, int y, Part normal, Part hover, Component narration, Runnable action) {
            super(x, y, normal.width(), normal.height(), narration);
            this.normal = normal;
            this.hover = hover;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            blit(graphics, isHovered() ? hover : normal, getX(), getY());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}