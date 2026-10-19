package com.nrdhvnstd.halgventmod.client.armorial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.armorial.ArmorialBook;
import com.nrdhvnstd.halgventmod.armorial.ArmorialPage;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;
import com.nrdhvnstd.halgventmod.banner.BannerRegistry;
import com.nrdhvnstd.halgventmod.client.MapTooltipBox;
import com.nrdhvnstd.halgventmod.client.ScrollText;
import com.nrdhvnstd.halgventmod.client.banner.BannerDraw;
import com.nrdhvnstd.halgventmod.client.banner.BannerEditScreen;
import com.nrdhvnstd.halgventmod.client.banner.BannerRegistryScreen;
import com.nrdhvnstd.halgventmod.network.ArmorialEditPayload;
import com.nrdhvnstd.halgventmod.network.BannerActionPayload;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Книга-гербовник: раскрытая книга, всегда две страницы (слева нечётная, справа чётная; последняя правая может быть пустой).
 * Листается по развороту: стрелки в нижних углах, колёсико, ← → и PageUp / PageDown. Разворот каждой книги помнится до конца сеанса.
 * Страница LARGE — большое знамя, справа его название и описание (описание листается колёсиком под курсором);
 * GRID — сетка 3 × 3, наведение на знамя — окно с названием и описанием. Названия и описания — из реестра знамён.
 * Администрация (уровень прав 2+): ПКМ по знамени — копия баннера себе.
 * Администрация в творческом открывает книгу сразу в правке: тип страницы, сдвиг, удаление, название, знамёна;
 * всё копится здесь и уходит на сервер одним пакетом по «Готово» (ArmorialEditPayload).
 */
public class ArmorialScreen extends Screen {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID,
            "textures/gui/armorial.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    // Книга ⚠️ (заглушка): 360 × 228, страницы по 148 от x = 16 и x = 196
    private static final int WIDTH = 360;
    private static final int HEIGHT = 228;
    private static final int[] PAGE_X = {16, 196};
    private static final int PAGE_WIDTH = 148;

    // Высоты от верха книги
    private static final int TITLE_Y = 12;
    private static final int RULE_Y = 23;
    private static final int CONTENT_Y = 29;
    private static final int HEADER_Y = 9;         // правка: тип, сдвиг, удаление
    private static final int TITLE_BOX_Y = 22;     // правка: поле названия
    private static final int CONTENT_Y_EDIT = 40;
    private static final int NUMBER_Y = 210;
    private static final int ARROW_Y = 205;
    private static final int ARROW_INSET = 14;
    private static final int TOP_BAR = 26;         // правка: строка над книгой

    // Страница LARGE
    private static final int LARGE_BANNER = 60;    // ⚠️ знамя 60 × 120
    private static final int LARGE_GAP = 6;
    private static final int NAME_LINES = 2;
    // Страница GRID: знамя 20 × 40 (тексель в пиксель; 40 × 80 в три ряда не помещается на страницу)
    private static final int GRID_BANNER = 20;     // ⚠️
    private static final int CELL_WIDTH = 28;
    private static final int CELL_HEIGHT = 46;
    private static final int CELL_STEP_X = 38;
    private static final int CELL_STEP_Y = 50;
    private static final int GRID_WIDTH = 2 * CELL_STEP_X + CELL_WIDTH;

    private static final int CONTROL_HEIGHT = 11;
    private static final int SMALL = 11;           // квадратные кнопки ‹ › ×
    private static final int CELL_CLEAR = 8;       // × в углу ячейки

    // Чернила на пергаменте
    private static final int COLOR_INK = 0xFF3A2A1A;
    private static final int COLOR_INK_TITLE = 0xFF7A2E1A;
    private static final int COLOR_INK_DIM = 0xFF7A6A50;
    private static final int COLOR_RULE = 0xFF9A8460;
    private static final int COLOR_EMPTY_CELL = 0x60806A48;
    private static final int COLOR_HOVER = 0x30000000;
    private static final int COLOR_BUTTON = 0x28000000;
    private static final int COLOR_BUTTON_HOVER = 0x50000000;
    private static final int COLOR_SCROLLBAR = 0x807A6A50;
    private static final int COLOR_BAR_TEXT = 0xFFE8DCC0;

    /** Открытый разворот каждой книги — до конца сеанса. */
    private static final Map<String, Integer> SPREADS = new HashMap<>();

    /** Страница в работе: в правке меняется на месте. */
    private static final class PageDraft {
        ArmorialPage.Type type;
        String title;
        final List<String> banners;

        PageDraft(ArmorialPage page) {
            type = page.type();
            title = page.title();
            banners = new ArrayList<>(ArmorialPage.fit(page.type(), page.banners()));
        }

        ArmorialPage toPage() {
            return new ArmorialPage(type, title, ArmorialPage.fit(type, banners));
        }
    }

    /** Кнопка, нарисованная прямо на странице; список строится заново каждый кадр и им же пользуется клик. */
    private record Control(int x, int y, int width, int height, Component label, Runnable action,
                           @Nullable Component tooltip, boolean selected, boolean active) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /** Место знамени на странице: наведение, ПКМ (копия), в правке ЛКМ — выбрать другое. */
    private record Spot(int x, int y, int width, int height, String id, boolean grid, @Nullable Runnable pick) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final String bookId;
    private final ArmorialBook book;
    private final boolean editing;
    private final List<PageDraft> pages = new ArrayList<>();
    private final ScrollText[] descriptions = new ScrollText[2];
    private final List<Control> controls = new ArrayList<>();
    private final List<Spot> spots = new ArrayList<>();
    private final EditBox[] titleBoxes = new EditBox[2];
    private int spread;
    private boolean dirty;
    private int left;
    private int top;

    public ArmorialScreen(String bookId, ArmorialBook book, boolean editing) {
        super(book.title().isEmpty() ? ModItems.ARMORIAL.get().getDescription() : Component.literal(book.title()));
        this.bookId = bookId;
        this.book = book;
        this.editing = editing;
        for (ArmorialPage page : book.pages()) {
            pages.add(new PageDraft(page));
        }
        for (int i = 0; i < descriptions.length; i++) {
            descriptions[i] = new ScrollText(false, COLOR_INK, COLOR_INK_DIM, COLOR_SCROLLBAR);
        }
        spread = SPREADS.getOrDefault(bookId, 0);
    }

    // ===== Развороты =====

    private int spreads() {
        return Math.max(1, (pages.size() + 1) / 2);
    }

    private void flip(int delta) {
        int next = Mth.clamp(spread + delta, 0, spreads() - 1);
        if (next != spread) {
            spread = next;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            rebuildWidgets();
        }
    }

    private void showPage(int index) {
        spread = Mth.clamp(index / 2, 0, spreads() - 1);
        rebuildWidgets();
    }

    private int pageX(int side) {
        return left + PAGE_X[side];
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        if (editing) {
            top = Math.max(top, TOP_BAR + 2); // над книгой — строка правки
        }
        spread = Mth.clamp(spread, 0, spreads() - 1);
        SPREADS.put(bookId, spread);

        PageButton back = addRenderableWidget(new PageButton(left + ARROW_INSET, top + ARROW_Y, false, button -> flip(-1), false));
        PageButton forward = addRenderableWidget(new PageButton(left + WIDTH - ARROW_INSET - 23, top + ARROW_Y, true,
                button -> flip(1), false));
        back.visible = spread > 0;
        forward.visible = spread < spreads() - 1;

        if (!editing) {
            return;
        }
        for (int side = 0; side < 2; side++) {
            int index = spread * 2 + side;
            titleBoxes[side] = null;
            if (index >= pages.size()) {
                continue;
            }
            PageDraft page = pages.get(index);
            EditBox box = new EditBox(font, pageX(side), top + TITLE_BOX_Y, PAGE_WIDTH, 14,
                    Component.translatable("armorial.halgventmod.page_title"));
            box.setMaxLength(ArmorialPage.TITLE_MAX);
            box.setValue(page.title);
            box.setHint(Component.translatable("armorial.halgventmod.page_title").withColor(0xFF8A7A60));
            box.setResponder(value -> {
                if (!value.equals(page.title)) {
                    page.title = value;
                    dirty = true;
                }
            });
            titleBoxes[side] = addRenderableWidget(box);
        }
        int barY = top - TOP_BAR + 3;
        int doneWidth = 60;
        int addWidth = 120;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> done())
                .bounds(left + WIDTH - doneWidth, barY, doneWidth, 20).build());
        Button add = addRenderableWidget(Button.builder(Component.translatable("armorial.halgventmod.add_page"),
                        button -> addPage())
                .bounds(left + WIDTH - doneWidth - 4 - addWidth, barY, addWidth, 20).build());
        add.active = pages.size() < ArmorialBook.PAGES_MAX;
    }

    // ===== Правка =====

    private void addPage() {
        if (pages.size() >= ArmorialBook.PAGES_MAX) {
            return;
        }
        pages.add(new PageDraft(ArmorialPage.empty()));
        dirty = true;
        showPage(pages.size() - 1);
    }

    private void setType(PageDraft page, ArmorialPage.Type type) {
        if (page.type == type) {
            return;
        }
        // Первое знамя сохраняется: из сетки в большое — первое непустое
        String first = "";
        for (String id : page.banners) {
            if (!id.isEmpty()) {
                first = id;
                break;
            }
        }
        page.type = type;
        List<String> fitted = ArmorialPage.fit(type, List.of(first));
        page.banners.clear();
        page.banners.addAll(fitted);
        dirty = true;
    }

    private void movePage(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= pages.size()) {
            return;
        }
        pages.add(target, pages.remove(index));
        dirty = true;
        showPage(target);
    }

    private void deletePage(int index) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                pages.remove(index);
                dirty = true;
            }
            minecraft.setScreen(this);
        }, Component.translatable("armorial.halgventmod.delete_page_question", index + 1), Component.empty()));
    }

    private void pickBanner(PageDraft page, int slot) {
        minecraft.setScreen(BannerRegistryScreen.pick(this, id -> {
            page.banners.set(slot, id);
            dirty = true;
        }));
    }

    private void done() {
        List<ArmorialPage> result = new ArrayList<>();
        for (PageDraft page : pages) {
            result.add(page.toPage());
        }
        PacketDistributor.sendToServer(new ArmorialEditPayload(bookId, new ArmorialBook(book.title(), result)));
        dirty = false;
        onClose();
    }

    // ===== Рисование =====

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(TEXTURE, left, top, 0, 0, WIDTH, HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        controls.clear();
        spots.clear();
        if (editing) {
            graphics.drawString(font, Component.translatable("armorial.halgventmod.edit_title", title),
                    left, top - TOP_BAR + 9, COLOR_BAR_TEXT, true);
        }
        if (pages.isEmpty() && !editing) {
            Component empty = Component.translatable("armorial.halgventmod.empty");
            graphics.drawString(font, empty, left + (WIDTH - font.width(empty)) / 2, top + HEIGHT / 2 - 4, COLOR_INK_DIM, false);
            return;
        }
        for (int side = 0; side < 2; side++) {
            renderPage(graphics, spread * 2 + side, side, mouseX, mouseY);
        }
        for (Control control : controls) {
            renderControl(graphics, control, mouseX, mouseY);
        }
        renderHover(graphics, mouseX, mouseY);
    }

    private void renderPage(GuiGraphics graphics, int index, int side, int mouseX, int mouseY) {
        if (index >= pages.size()) {
            return; // последняя правая страница при нечётном числе — пустая и без номера
        }
        PageDraft page = pages.get(index);
        int x = pageX(side);
        // На пергаменте всё рисуется без тени
        String number = Integer.toString(index + 1);
        graphics.drawString(font, number, x + (PAGE_WIDTH - font.width(number)) / 2, top + NUMBER_Y, COLOR_INK_DIM, false);
        int contentY;
        if (editing) {
            pageControls(page, index, x);
            contentY = top + CONTENT_Y_EDIT;
        } else {
            String title = font.plainSubstrByWidth(page.title, PAGE_WIDTH);
            graphics.drawString(font, title, x + (PAGE_WIDTH - font.width(title)) / 2, top + TITLE_Y, COLOR_INK_TITLE, false);
            graphics.fill(x, top + RULE_Y, x + PAGE_WIDTH, top + RULE_Y + 1, COLOR_RULE);
            contentY = top + CONTENT_Y;
        }
        if (page.type == ArmorialPage.Type.LARGE) {
            renderLarge(graphics, page, side, x, contentY);
        } else {
            renderGrid(graphics, page, x, contentY, mouseX, mouseY);
        }
    }

    // Правка: [1 знамя][9 знамён] слева, ‹ › × справа
    private void pageControls(PageDraft page, int index, int x) {
        int y = top + HEADER_Y;
        int cx = x;
        for (ArmorialPage.Type type : ArmorialPage.Type.values()) {
            Component label = Component.translatable(type == ArmorialPage.Type.LARGE
                    ? "armorial.halgventmod.type_large" : "armorial.halgventmod.type_grid");
            int w = font.width(label) + 6;
            controls.add(new Control(cx, y, w, CONTROL_HEIGHT, label, () -> setType(page, type), null,
                    page.type == type, true));
            cx += w + 3;
        }
        int right = x + PAGE_WIDTH;
        controls.add(new Control(right - SMALL, y, SMALL, CONTROL_HEIGHT, Component.literal("×"), () -> deletePage(index),
                Component.translatable("armorial.halgventmod.delete_page"), false, true));
        controls.add(new Control(right - 2 * SMALL - 2, y, SMALL, CONTROL_HEIGHT, Component.literal("›"),
                () -> movePage(index, 1), Component.translatable("armorial.halgventmod.move_right"), false,
                index < pages.size() - 1));
        controls.add(new Control(right - 3 * SMALL - 4, y, SMALL, CONTROL_HEIGHT, Component.literal("‹"),
                () -> movePage(index, -1), Component.translatable("armorial.halgventmod.move_left"), false, index > 0));
    }

    // Большое знамя слева, справа название цветом заголовка, черта и описание с переносом
    private void renderLarge(GuiGraphics graphics, PageDraft page, int side, int x, int y) {
        String id = page.banners.get(0);
        BannerEntry entry = BannerRegistry.client(id);
        if (editing && entry == null) {
            graphics.renderOutline(x, y, LARGE_BANNER, 2 * LARGE_BANNER, COLOR_EMPTY_CELL);
        }
        BannerDraw.draw(graphics, entry, x, y, LARGE_BANNER);
        spots.add(new Spot(x, y, LARGE_BANNER, 2 * LARGE_BANNER, id, false, editing ? () -> pickBanner(page, 0) : null));

        int textX = x + LARGE_BANNER + LARGE_GAP;
        int textWidth = PAGE_WIDTH - LARGE_BANNER - LARGE_GAP;
        int bottom = editing ? y + 2 * LARGE_BANNER : top + NUMBER_Y - 6;
        if (entry != null) {
            List<FormattedCharSequence> name = font.split(Component.literal(entry.name()), textWidth);
            int lineY = y;
            for (int i = 0; i < Math.min(NAME_LINES, name.size()); i++) {
                graphics.drawString(font, name.get(i), textX, lineY, COLOR_INK_TITLE, false);
                lineY += 10;
            }
            graphics.fill(textX, lineY + 1, textX + textWidth, lineY + 2, COLOR_RULE);
            descriptions[side].set(font, entry.description(), textX, lineY + 5, textWidth, bottom - lineY - 5);
            descriptions[side].render(graphics, font);
        } else {
            descriptions[side].set(font, "", textX, y, textWidth, 0);
        }
        if (editing) {
            int buttonY = y + 2 * LARGE_BANNER + 4;
            controls.add(new Control(x, buttonY, PAGE_WIDTH, 13, Component.translatable("armorial.halgventmod.change_banner"),
                    () -> pickBanner(page, 0), null, false, true));
            controls.add(new Control(x, buttonY + 15, PAGE_WIDTH, 13, Component.translatable("armorial.halgventmod.edit_banner"),
                    () -> minecraft.setScreen(new BannerEditScreen(this, entry)), null, false, entry != null));
        }
    }

    // Сетка 3 × 3: пустая ячейка — бледная рамка (в правке ещё «+»), наведённая — подсвечена
    private void renderGrid(GuiGraphics graphics, PageDraft page, int x, int y, int mouseX, int mouseY) {
        int gridX = x + (PAGE_WIDTH - GRID_WIDTH) / 2;
        int gridY = y + 4;
        for (int i = 0; i < ArmorialPage.Type.GRID.slots(); i++) {
            int cx = gridX + i % 3 * CELL_STEP_X;
            int cy = gridY + i / 3 * CELL_STEP_Y;
            String id = page.banners.get(i);
            BannerEntry entry = BannerRegistry.client(id);
            boolean hovered = mouseX >= cx && mouseX < cx + CELL_WIDTH && mouseY >= cy && mouseY < cy + CELL_HEIGHT;
            if (entry == null) {
                graphics.renderOutline(cx, cy, CELL_WIDTH, CELL_HEIGHT, COLOR_EMPTY_CELL);
                if (editing) {
                    graphics.drawString(font, "+", cx + (CELL_WIDTH - font.width("+")) / 2 + 1, cy + CELL_HEIGHT / 2 - 4,
                            hovered ? COLOR_INK_TITLE : COLOR_INK_DIM, false);
                }
            } else if (hovered) {
                graphics.fill(cx, cy, cx + CELL_WIDTH, cy + CELL_HEIGHT, COLOR_HOVER);
            }
            BannerDraw.draw(graphics, entry, cx + (CELL_WIDTH - GRID_BANNER) / 2, cy + 3, GRID_BANNER);
            int slot = i;
            if (editing && !id.isEmpty()) {
                controls.add(new Control(cx + CELL_WIDTH - CELL_CLEAR, cy, CELL_CLEAR, CELL_CLEAR, Component.literal("×"),
                        () -> {
                            page.banners.set(slot, "");
                            dirty = true;
                        }, Component.translatable("armorial.halgventmod.clear_cell"), false, true));
            }
            spots.add(new Spot(cx, cy, CELL_WIDTH, CELL_HEIGHT, id, true, editing ? () -> pickBanner(page, slot) : null));
        }
    }

    private void renderControl(GuiGraphics graphics, Control control, int mouseX, int mouseY) {
        boolean hovered = control.active() && control.contains(mouseX, mouseY);
        graphics.fill(control.x(), control.y(), control.x() + control.width(), control.y() + control.height(),
                hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        if (control.selected()) {
            graphics.renderOutline(control.x(), control.y(), control.width(), control.height(), COLOR_INK_TITLE);
        }
        int color = !control.active() ? COLOR_EMPTY_CELL : control.selected() ? COLOR_INK_TITLE : COLOR_INK;
        graphics.drawString(font, control.label(), control.x() + (control.width() - font.width(control.label())) / 2 + 1,
                control.y() + (control.height() - 8) / 2, color, false);
    }

    // Подсказки: у кнопок — их назначение; у знамени — название и описание (в сетке), администрации — «ПКМ — взять копию»
    private void renderHover(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Control control : controls) {
            if (control.contains(mouseX, mouseY)) {
                if (control.tooltip() != null) {
                    graphics.renderTooltip(font, control.tooltip(), mouseX, mouseY);
                }
                return;
            }
        }
        Spot spot = spotAt(mouseX, mouseY);
        BannerEntry entry = spot != null ? BannerRegistry.client(spot.id()) : null;
        if (entry == null) {
            return;
        }
        boolean admin = Permissions.isAdmin(minecraft.player);
        if (!spot.grid() && !admin) {
            return; // у большого знамени название и описание и так на странице
        }
        List<Component> dim = admin ? List.of(Component.translatable("armorial.halgventmod.take_copy")) : List.of();
        MapTooltipBox.render(graphics, font, Component.literal(entry.name()),
                spot.grid() ? Component.literal(entry.description()) : null, dim, mouseX, mouseY);
    }

    @Nullable
    private Spot spotAt(double mouseX, double mouseY) {
        for (Spot spot : spots) {
            if (spot.contains(mouseX, mouseY)) {
                return spot;
            }
        }
        return null;
    }

    // ===== Управление =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (Control control : List.copyOf(controls)) {
                if (control.contains(mouseX, mouseY)) {
                    if (control.active()) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        control.action().run();
                    }
                    return true;
                }
            }
        }
        Spot spot = spotAt(mouseX, mouseY);
        if (spot == null) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !spot.id().isEmpty() && Permissions.isAdmin(minecraft.player)
                && BannerRegistry.client(spot.id()) != null) {
            PacketDistributor.sendToServer(BannerActionPayload.simple(BannerActionPayload.Action.GIVE, spot.id()));
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && spot.pick() != null) {
            spot.pick().run();
            return true;
        }
        return false;
    }

    // Над описанием большого знамени — листать его; в остальных местах — листать книгу
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (ScrollText text : descriptions) {
            if (text.contains(mouseX, mouseY)) {
                text.scroll(scrollY);
                return true;
            }
        }
        if (scrollY != 0) {
            flip(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // В поле названия стрелки двигают курсор, а не листают
        if (!(getFocused() instanceof EditBox box && box.isFocused())) {
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                flip(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                flip(1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Esc с несохранённой правкой — «Сохранить?»: «Да» — сохранить, «Нет» — закрыть без сохранения
    @Override
    public void onClose() {
        SPREADS.put(bookId, spread);
        if (editing && dirty) {
            minecraft.setScreen(new ConfirmScreen(save -> {
                if (save) {
                    done();
                } else {
                    dirty = false;
                    minecraft.setScreen(null);
                }
            }, Component.translatable("armorial.halgventmod.save_question"),
                    Component.translatable("armorial.halgventmod.save_hint")));
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
