package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.StationMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Окно станции (очаг, печь, горн, куб, бочки) в стиле Conquest, как окно инвентаря: название над окном, рамки ячеек,
 * сумки и карманы внизу. Разделы — тёмные вставки ({@link #section}) с пергаментной плашкой-заголовком
 * ({@link #plaque}); их рисует {@link #panels} до ячеек. Помощники: градусник с колбой, объёмная полоса, огонёк
 * горящего полена, время «м:сс», мелкий текст, кнопка, шлющая номер на сервер (clickMenuButton).
 */
public abstract class StationScreen<T extends StationMenu> extends AbstractContainerScreen<T> {
    protected static final int TEXT = 0xFFE9DCB4;
    protected static final int TEXT_DIM = 0xFFAA9C80;
    protected static final int GOOD = 0xFF8FD16A;
    protected static final int WARN = 0xFFE8B04A;
    protected static final int BAD = 0xFFE0604A;
    protected static final int TRACK = 0xFF1E160E;
    protected static final int FRAME = 0xFF0E0A06;
    protected static final int INK = 0xFF3A2414;  // тёмный текст на пергаменте
    private static final int TITLE_GAP = 2;
    private static final ResourceLocation PLAQUE = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/title");
    private static final ResourceLocation FLAME = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");

    protected StationScreen(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, font.lineHeight + TITLE_GAP);
        if (!book().isEmpty()) {
            leftPos = Math.min(width - imageWidth, leftPos + (BOOK_WIDTH + BOOK_GAP) / 2);
        }
    }

    // ---------------------------------------------------------------- книга «что сделать» слева от окна

    /** Строка книги: значок и ключ перевода (название; .how — что делать; .serve — что выйдет, необязательно). */
    public record BookEntry(net.minecraft.world.item.ItemStack icon, String key) {}

    protected static final int BOOK_WIDTH = 132;
    protected static final int BOOK_GAP = 4;
    protected static final int BOOK_ROW = 16;

    /** Что делают на станции — строки книги слева; пусто — книги нет. */
    protected java.util.List<BookEntry> book() {
        return java.util.List.of();
    }

    /** Подпись под строками книги или null. */
    protected Component bookHint() {
        return null;
    }

    private void renderBook(GuiGraphics g, int mouseX, int mouseY) {
        java.util.List<BookEntry> entries = book();
        int left = leftPos - BOOK_WIDTH - BOOK_GAP;
        int top = topPos;
        ConquestUi.window(g, left, top, left + BOOK_WIDTH, top + imageHeight);
        ConquestUi.inset(g, left + 4, top + 4, left + BOOK_WIDTH - 4, top + 18 + entries.size() * BOOK_ROW + 2);
        plaque(g, Component.translatable("gui.halgventmod.recipes.what"), -BOOK_WIDTH - BOOK_GAP + 8, 6, -BOOK_GAP - 8);
        for (int i = 0; i < entries.size(); i++) {
            int ry = top + 18 + i * BOOK_ROW;
            boolean hover = mouseX >= left + 4 && mouseX < left + BOOK_WIDTH - 4 && mouseY >= ry && mouseY < ry + BOOK_ROW;
            if (hover) {
                g.fill(left + 4, ry, left + BOOK_WIDTH - 4, ry + BOOK_ROW, 0x30FFFFFF);
            }
            g.renderItem(entries.get(i).icon(), left + 6, ry);
            fit(g, Component.translatable(entries.get(i).key()), left + 25, ry + 4, BOOK_WIDTH - 31, hover ? TEXT : TEXT_DIM);
        }
        Component hint = bookHint();
        if (hint != null) {
            smallWrap(g, hint, left + 8, top + 18 + entries.size() * BOOK_ROW + 6, BOOK_WIDTH - 14, top + imageHeight - 4, TEXT_DIM);
        }
    }

    /** Подсказка строки книги под мышью: название, что делать, что выйдет. true — показана. */
    private boolean bookTooltip(GuiGraphics g, int mouseX, int mouseY) {
        java.util.List<BookEntry> entries = book();
        int left = leftPos - BOOK_WIDTH - BOOK_GAP;
        for (int i = 0; i < entries.size(); i++) {
            int ry = topPos + 18 + i * BOOK_ROW;
            if (mouseX >= left + 4 && mouseX < left + BOOK_WIDTH - 4 && mouseY >= ry && mouseY < ry + BOOK_ROW) {
                String key = entries.get(i).key();
                java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
                lines.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GOLD).getVisualOrderText());
                lines.addAll(font.split(Component.translatable(key + ".how"), 200));
                if (net.minecraft.client.resources.language.I18n.exists(key + ".serve")) {
                    lines.addAll(font.split(Component.translatable(key + ".serve").withStyle(net.minecraft.ChatFormatting.GRAY), 200));
                }
                g.renderTooltip(font, lines, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        boolean inBook = !book().isEmpty() && mouseX >= left - BOOK_WIDTH - BOOK_GAP && mouseX < left && mouseY >= top
                && mouseY < top + imageHeight;
        return !inBook && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    protected void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    protected Button button(Component label, int x, int y, int width, int id) {
        return addRenderableWidget(Button.builder(label, button -> click(id)).bounds(leftPos + x, topPos + y, width, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, leftPos, topPos - font.lineHeight - TITLE_GAP, 0xFFFFFF, true);
        if (!book().isEmpty()) {
            renderBook(g, mouseX, mouseY);
            if (menu.getCarried().isEmpty() && bookTooltip(g, mouseX, mouseY)) {
                return;
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    /** Рамка окна, разделы ({@link #panels}) и ячейки (неактивные — нет, закрытые ячейки сумок — тёмные). */
    protected void frame(GuiGraphics g) {
        ConquestUi.window(g, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        panels(g);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConquestUi.slot(g, leftPos + slot.x, topPos + slot.y, slot instanceof StorageSlot storage && !storage.isOpen());
            }
        }
    }

    /** Разделы окна: вставки и плашки. Рисуются под ячейками. */
    protected void panels(GuiGraphics g) {
    }

    /** Тёмная утопленная вставка раздела (координаты — в окне). */
    protected void section(GuiGraphics g, int x0, int y0, int x1, int y1) {
        ConquestUi.inset(g, leftPos + x0, topPos + y0, leftPos + x1, topPos + y1);
    }

    /** Пергаментная плашка-заголовок раздела с тёмной надписью посередине (не влезает — мельче). */
    protected void plaque(GuiGraphics g, Component text, int x0, int y, int x1) {
        g.blitSprite(PLAQUE, leftPos + x0, topPos + y, x1 - x0, 11);
        int width = x1 - x0 - 12;
        int center = leftPos + (x0 + x1) / 2;
        if (font.width(text) <= width) {
            g.drawString(font, text, center - font.width(text) / 2, topPos + y + 2, INK, false);
        } else {
            int w = Math.round(font.width(text) * SMALL);
            small(g, text, center - w / 2, topPos + y + 3, INK);
        }
    }

    /** Вертикальный градусник с колбой: value из max, снизу вверх; цвет от холодного к жаркому. */
    protected void thermometer(GuiGraphics g, int x, int y, int width, int height, float value, float max) {
        float share = Math.max(0, Math.min(1, value / max));
        int color = share < 0.33F ? 0xFFB0763A : share < 0.66F ? 0xFFE08A2A : 0xFFF4C04A;
        int light = share < 0.33F ? 0xFFD8A060 : share < 0.66F ? 0xFFF8B860 : 0xFFFFE890;
        // Трубка и колба
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, FRAME);
        g.fill(x - 3, y + height - 1, x + width + 3, y + height + 6, FRAME);
        g.fill(x, y, x + width, y + height, TRACK);
        g.fill(x - 2, y + height, x + width + 2, y + height + 5, value > 0 ? color : TRACK);
        int filled = Math.round(height * share);
        if (filled > 0) {
            g.fillGradient(x, y + height - filled, x + width, y + height, light, color);
        }
        g.fill(x + 1, y + 1, x + 2, y + height - 1, 0x30FFFFFF); // блик на стекле
    }

    /** Отметка на градуснике (зелёная зона, порог). */
    protected void mark(GuiGraphics g, int x, int y, int width, int height, float value, float max, int color) {
        int my = y + height - Math.round(height * Math.max(0, Math.min(1, value / max)));
        g.fill(x + width + 1, my, x + width + 4, my + 1, color);
        g.fill(x, my, x + width, my + 1, (color & 0x00FFFFFF) | 0x40000000);
    }

    /** Горизонтальная объёмная полоса: доля share, цвет color (сверху светлее, по верху — отсвет). */
    protected void bar(GuiGraphics g, int x, int y, int width, int height, float share, int color) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, FRAME);
        g.fill(x, y, x + width, y + height, TRACK);
        g.fill(x, y, x + width, y + 1, 0x40000000);
        int filled = Math.round(width * Math.max(0, Math.min(1, share)));
        if (filled > 0) {
            g.fillGradient(x, y, x + filled, y + height, lighter(color), color);
            if (height > 2) {
                g.fill(x, y, x + filled, y + 1, 0x50FFFFFF);
            }
        }
    }

    private static int lighter(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 48);
        int gr = Math.min(255, ((color >> 8) & 0xFF) + 48);
        int b = Math.min(255, (color & 0xFF) + 48);
        return (color & 0xFF000000) | r << 16 | gr << 8 | b;
    }

    /** Огонёк горящего полена (как в печи): горит — убывает с поленом; не горит — тусклый. */
    protected void flame(GuiGraphics g, int x, int y, float share, boolean lit) {
        int h = lit ? Math.max(1, Math.round(14 * Math.max(0, Math.min(1, share)))) : 0;
        if (h > 0) {
            g.blitSprite(FLAME, 14, 14, 0, 14 - h, x, y + 14 - h, 14, h);
        }
    }

    /**
     * Раздел «Огонь» слева в окне (очаг, костёр, куб над очагом, печь): вставка с плашкой, градусник с колбой,
     * температура, состояние, сколько гореть, полоса и огонёк горящего полена; ячейки дров — под ним (x 27, y 81),
     * кнопка — ниже раздела.
     * @param state строка под температурой (жар, прогрев)
     */
    protected void fireSection(GuiGraphics g, float temperature, float max, Component degrees, Component state, int stateColor,
                               boolean lit, int remainingTicks, int burnLeft, int burnTotal, float... marks) {
        int x = leftPos;
        int y = topPos;
        thermometer(g, x + 11, y + 21, 6, 54, temperature, max);
        for (float mark : marks) {
            mark(g, x + 11, y + 21, 6, 54, mark, max, 0xC0FFFFFF);
        }
        g.drawString(font, degrees, x + 25, y + 21, TEXT, true);
        fit(g, state, x + 25, y + 32, 70, stateColor);
        fit(g, Component.translatable(lit ? "gui.halgventmod.hearth.burns" : "gui.halgventmod.hearth.out", time(remainingTicks)),
                x + 25, y + 43, 70, lit ? TEXT : TEXT_DIM);
        float share = burnLeft / (float) Math.max(1, burnTotal);
        bar(g, x + 25, y + 57, 52, 4, share, lit ? 0xFFE08A2A : 0xFF6A5A48);
        flame(g, x + 80, y + 53, share, lit);
        small(g, Component.translatable("gui.halgventmod.hearth.fuel"), x + 26, y + 67, TEXT_DIM);
    }

    /** Вставка и плашка «Огонь» под {@link #fireSection} (рисовать в {@link #panels}). */
    protected void firePanel(GuiGraphics g) {
        section(g, 4, 4, 98, 104);
        plaque(g, Component.translatable("gui.halgventmod.hearth.fire"), 8, 6, 94);
    }

    /** Мелкий текст (×0,75): подписи, которым тесно. Ширина — в пикселях окна. */
    protected static final float SMALL = 0.75F;

    protected void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SMALL, SMALL, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Мелкий текст с переносом по ширине width (в пикселях окна); не ниже maxY. */
    protected void smallWrap(GuiGraphics g, Component text, int x, int y, int width, int maxY, int color) {
        int line = 0;
        for (net.minecraft.util.FormattedCharSequence part : font.split(text, Math.round(width / SMALL))) {
            int ly = y + Math.round(line * (font.lineHeight + 1) * SMALL);
            if (ly + font.lineHeight * SMALL > maxY) {
                break;
            }
            g.pose().pushPose();
            g.pose().translate(x, ly, 0);
            g.pose().scale(SMALL, SMALL, 1);
            g.drawString(font, part, 0, 0, color, false);
            g.pose().popPose();
            line++;
        }
    }

    /** Обычный текст, а если не влезает в width — мелкий. */
    protected void fit(GuiGraphics g, Component text, int x, int y, int width, int color) {
        if (font.width(text) <= width) {
            g.drawString(font, text, x, y, color, false);
        } else {
            small(g, text, x, y + 1, color);
        }
    }

    /** «4:05» из тиков. */
    protected static String time(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    protected boolean hovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width && mouseY >= topPos + y && mouseY < topPos + y + height;
    }
}
