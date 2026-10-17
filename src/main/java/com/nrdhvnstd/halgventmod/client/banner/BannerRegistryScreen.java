package com.nrdhvnstd.halgventmod.client.banner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.banner.BannerEntry;
import com.nrdhvnstd.halgventmod.banner.BannerRegistry;
import com.nrdhvnstd.halgventmod.network.BannerActionPayload;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно реестра знамён (только администрация; всё проверяет сервер): карточки сеткой, поиск по названию и id,
 * прокрутка колёсиком. На карточке — знамя, название, id и кнопки «Взять», «Править», «Удалить».
 * Режим выбора (из окна точки или правки книги): кнопок нет, клик по карточке отдаёт id вызвавшему окну.
 * Знамёна берутся из реестра на клиенте каждый кадр — правка с сервера видна сразу.
 */
public class BannerRegistryScreen extends Screen {
    private static final int MARGIN = 10;
    private static final int PAD = 8;
    private static final int GAP = 6;
    private static final int MAX_COLUMNS = 4;
    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 88;
    private static final int BANNER_WIDTH = 40;  // знамя 40 × 80
    private static final int CARD_INNER = 4;
    private static final int TEXT_LEFT = CARD_INNER + BANNER_WIDTH + 6;
    private static final int ACTION_TOP = 40;
    private static final int ACTION_STEP = 15;
    private static final int ACTION_HEIGHT = 13;
    private static final int GRID_TOP = 38;      // от верха окна: заголовок и подсказка
    private static final int SEARCH_WIDTH = 110;
    private static final int CLOSE_SIZE = 14;
    private static final int SCROLL_STEP = 24;
    private static final int TOOLTIP_WIDTH = 200;

    // Стиль меню карты
    private static final int COLOR_PANEL = 0xF0241C14;
    private static final int COLOR_FRAME = 0xFF6E6250;
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_DIM = 0xFFB8A888;
    private static final int COLOR_CARD = 0x40000000;
    private static final int COLOR_BUTTON = 0xFF3A3024;
    private static final int COLOR_HOVER = 0xFF4A3E2E;
    private static final int COLOR_SCROLLBAR = 0x80E8DCC0;

    private enum Action { TAKE, EDIT, DELETE }

    @Nullable
    private final Screen parent;
    @Nullable
    private final Consumer<String> picker; // не null — режим выбора

    private EditBox searchBox;
    private String search = "";
    private int scroll;
    private int columns;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    /** Окно реестра по /banner list. */
    public static BannerRegistryScreen browse() {
        return new BannerRegistryScreen(null, null);
    }

    /** Режим выбора: клик по карточке — onPick(id) и назад к parent. Esc — назад без выбора. */
    public static BannerRegistryScreen pick(Screen parent, Consumer<String> onPick) {
        return new BannerRegistryScreen(parent, onPick);
    }

    private BannerRegistryScreen(@Nullable Screen parent, @Nullable Consumer<String> picker) {
        super(Component.translatable(picker != null ? "gui.halgventmod.banners.pick" : "gui.halgventmod.banners.title"));
        this.parent = parent;
        this.picker = picker;
    }

    @Override
    protected void init() {
        columns = Mth.clamp((width - 2 * MARGIN - 2 * PAD + GAP) / (CARD_WIDTH + GAP), 1, MAX_COLUMNS);
        panelWidth = columns * (CARD_WIDTH + GAP) - GAP + 2 * PAD;
        panelHeight = height - 2 * MARGIN;
        left = (width - panelWidth) / 2;
        top = MARGIN;

        int right = left + panelWidth - PAD;
        addRenderableWidget(Button.builder(Component.literal("×"), button -> onClose())
                .bounds(right - CLOSE_SIZE, top + 5, CLOSE_SIZE, CLOSE_SIZE).build());
        searchBox = new EditBox(font, right - CLOSE_SIZE - 4 - SEARCH_WIDTH, top + 5, SEARCH_WIDTH, CLOSE_SIZE,
                Component.translatable("gui.halgventmod.banners.search"));
        searchBox.setHint(Component.translatable("gui.halgventmod.banners.search").withColor(COLOR_DIM));
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            scroll = 0;
        });
        addRenderableWidget(searchBox);
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    // ===== Знамёна и разметка =====

    private List<BannerEntry> shown() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        List<BannerEntry> result = new ArrayList<>();
        for (BannerEntry entry : BannerRegistry.clientEntries.values()) {
            if (query.isEmpty() || entry.id().contains(query) || entry.name().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(entry);
            }
        }
        return result;
    }

    private int gridTop() {
        return top + GRID_TOP;
    }

    private int gridBottom() {
        return top + panelHeight - PAD;
    }

    private int cardX(int index) {
        return left + PAD + index % columns * (CARD_WIDTH + GAP);
    }

    private int cardY(int index) {
        return gridTop() + index / columns * (CARD_HEIGHT + GAP) - scroll;
    }

    private int maxScroll() {
        int rows = (shown().size() + columns - 1) / columns;
        int content = rows * (CARD_HEIGHT + GAP) - GAP;
        return Math.max(0, content - (gridBottom() - gridTop()));
    }

    private boolean overGrid(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + panelWidth && mouseY >= gridTop() && mouseY < gridBottom();
    }

    private int cardAt(List<BannerEntry> entries, double mouseX, double mouseY) {
        if (!overGrid(mouseX, mouseY)) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private Action actionAt(int cardX, int cardY, double mouseX, double mouseY) {
        if (picker != null) {
            return null;
        }
        int x = cardX + TEXT_LEFT;
        int width = CARD_WIDTH - TEXT_LEFT - CARD_INNER;
        for (Action action : Action.values()) {
            int y = cardY + ACTION_TOP + action.ordinal() * ACTION_STEP;
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ACTION_HEIGHT) {
                return action;
            }
        }
        return null;
    }

    // ===== Рисование =====

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, COLOR_PANEL);
        graphics.renderOutline(left, top, panelWidth, panelHeight, COLOR_FRAME);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + PAD, top + 8, COLOR_TITLE, false);
        Component hint = Component.translatable(picker != null
                ? "gui.halgventmod.banners.pick_hint" : "gui.halgventmod.banners.hint");
        graphics.drawString(font, hint, left + PAD, top + 24, COLOR_DIM, false);

        List<BannerEntry> entries = shown();
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(BannerRegistry.clientEntries.isEmpty()
                    ? "gui.halgventmod.banners.empty" : "gui.halgventmod.banners.not_found"),
                    left + panelWidth / 2, gridTop() + 20, COLOR_DIM);
            return;
        }
        int hovered = cardAt(entries, mouseX, mouseY);
        BannerEntry tooltip = null;
        graphics.enableScissor(left, gridTop(), left + panelWidth, gridBottom());
        for (int i = 0; i < entries.size(); i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (y + CARD_HEIGHT < gridTop() || y > gridBottom()) {
                continue;
            }
            BannerEntry entry = entries.get(i);
            renderCard(graphics, entry, x, y, i == hovered, mouseX, mouseY);
            int bannerX = x + CARD_INNER;
            int bannerY = y + CARD_INNER;
            if (i == hovered && !entry.description().isEmpty() && mouseX >= bannerX && mouseX < bannerX + BANNER_WIDTH
                    && mouseY >= bannerY && mouseY < bannerY + 2 * BANNER_WIDTH) {
                tooltip = entry;
            }
        }
        graphics.disableScissor();

        int max = maxScroll();
        if (max > 0) {
            int view = gridBottom() - gridTop();
            int content = view + max;
            int thumb = Math.max(12, view * view / content);
            int y = gridTop() + (view - thumb) * scroll / max;
            int x = left + panelWidth - PAD / 2 - 1;
            graphics.fill(x, y, x + 2, y + thumb, COLOR_SCROLLBAR);
        }
        if (tooltip != null) {
            graphics.renderTooltip(font, font.split(Component.literal(tooltip.description()), TOOLTIP_WIDTH), mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphics graphics, BannerEntry entry, int x, int y, boolean hovered, int mouseX, int mouseY) {
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, COLOR_CARD);
        graphics.renderOutline(x, y, CARD_WIDTH, CARD_HEIGHT, picker != null && hovered ? COLOR_TITLE : COLOR_FRAME);
        BannerDraw.draw(graphics, entry, x + CARD_INNER, y + CARD_INNER, BANNER_WIDTH);

        int textX = x + TEXT_LEFT;
        int textWidth = CARD_WIDTH - TEXT_LEFT - CARD_INNER;
        graphics.drawString(font, font.plainSubstrByWidth(entry.name(), textWidth), textX, y + 6, COLOR_TITLE, false);
        graphics.drawString(font, font.plainSubstrByWidth(entry.id(), textWidth), textX, y + 17, COLOR_DIM, false);
        graphics.drawString(font, Component.translatable("gui.halgventmod.banners.in_points", pointsWith(entry.id())),
                textX, y + 28, COLOR_DIM, false);
        if (picker != null) {
            return;
        }
        Action hoveredAction = hovered ? actionAt(x, y, mouseX, mouseY) : null;
        for (Action action : Action.values()) {
            int top = y + ACTION_TOP + action.ordinal() * ACTION_STEP;
            graphics.fill(textX, top, textX + textWidth, top + ACTION_HEIGHT, action == hoveredAction ? COLOR_HOVER : COLOR_BUTTON);
            graphics.renderOutline(textX, top, textWidth, ACTION_HEIGHT, COLOR_FRAME);
            graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.banners."
                    + action.name().toLowerCase(Locale.ROOT)), textX + textWidth / 2, top + 3, COLOR_TEXT);
        }
    }

    /** Сколько точек карты (всех измерений) показывают это знамя. */
    private static int pointsWith(String id) {
        int count = 0;
        for (MapPoint point : MapPoints.clientPoints) {
            if (point.banner.equals(id)) {
                count++;
            }
        }
        return count;
    }

    // ===== Управление =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        List<BannerEntry> entries = shown();
        int index = cardAt(entries, mouseX, mouseY);
        if (index < 0 || button != 0) {
            return false;
        }
        BannerEntry entry = entries.get(index);
        if (picker != null) {
            onClose();
            picker.accept(entry.id());
            return true;
        }
        Action action = actionAt(cardX(index), cardY(index), mouseX, mouseY);
        if (action == null) {
            return false;
        }
        playClick();
        switch (action) {
            case TAKE -> PacketDistributor.sendToServer(BannerActionPayload.simple(BannerActionPayload.Action.GIVE, entry.id()));
            case EDIT -> minecraft.setScreen(new BannerEditScreen(this, entry));
            case DELETE -> minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    PacketDistributor.sendToServer(BannerActionPayload.simple(BannerActionPayload.Action.REMOVE, entry.id()));
                }
                minecraft.setScreen(this);
            }, Component.translatable("gui.halgventmod.banners.delete_title"),
                    Component.translatable("gui.halgventmod.banners.delete_question", entry.name(), entry.id())));
        }
        return true;
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overGrid(mouseX, mouseY)) {
            scroll = Mth.clamp(scroll - (int) Math.round(scrollY * SCROLL_STEP), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        scroll = Mth.clamp(scroll, 0, maxScroll()); // знамя удалили — сетка стала короче
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
