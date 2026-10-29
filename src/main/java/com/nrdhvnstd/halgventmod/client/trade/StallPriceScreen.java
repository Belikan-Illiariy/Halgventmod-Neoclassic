package com.nrdhvnstd.halgventmod.client.trade;

import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.network.StallEditPayload;
import com.nrdhvnstd.halgventmod.network.StallSyncPayload;
import com.nrdhvnstd.halgventmod.trade.Coin;
import com.nrdhvnstd.halgventmod.trade.MarketStallBlockEntity;
import com.nrdhvnstd.halgventmod.trade.Price;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно цен одного вида вещей на прилавке — поверх окна прилавка (pushGuiLayer, прилавок остаётся открытым).
 * Таблица: строки — качества (Ужасное … Шедевр; у вещей без качества — одна строка), столбцы — монеты: слева цена
 * продажи, справа цена скупки. Цена — набор монет («1 крона 5 талеров»); пустая строка — не продаётся (не скупается).
 * «Всем как Обычное» копирует строку «Обычное» во все качества. «Сохранить» отправляет и закрывает, Esc — закрыть.
 */
public class StallPriceScreen extends Screen {
    private static final int WIDTH = 334;
    private static final int PADDING = 10;
    private static final int LABEL_WIDTH = 70;
    private static final int BOX_WIDTH = 26;
    private static final int BOX_STEP = 28;
    private static final int GROUP_GAP = 14;
    private static final int ROW = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_TEXT = 0xFFE8E0D0;
    private static final int COLOR_DIM = 0xFFA09888;

    private final StallSyncPayload.Kind kind;
    private final int rows;
    private final String[][] sell;
    private final String[][] buy;

    public StallPriceScreen(StallSyncPayload.Kind kind) {
        super(Component.translatable("gui.halgventmod.stall.prices_title"));
        this.kind = kind;
        this.rows = kind.graded() ? MarketStallBlockEntity.GRADES : 1;
        this.sell = load(kind.sell(), rows);
        this.buy = load(kind.buy(), rows);
    }

    private static String[][] load(int[] flat, int rows) {
        String[][] texts = new String[rows][Price.COINS];
        for (int row = 0; row < rows; row++) {
            for (int coin = 0; coin < Price.COINS; coin++) {
                int index = row * Price.COINS + coin;
                texts[row][coin] = index < flat.length && flat[index] > 0 ? Integer.toString(flat[index]) : "";
            }
        }
        return texts;
    }

    private int height() {
        return 76 + rows * ROW + BUTTON_HEIGHT + 2 * PADDING;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - height()) / 2;
    }

    private int sellX() {
        return left() + PADDING + LABEL_WIDTH;
    }

    private int buyX() {
        return sellX() + Price.COINS * BOX_STEP + GROUP_GAP;
    }

    private int rowsY() {
        return top() + 66;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        for (int row = 0; row < rows; row++) {
            for (int coin = 0; coin < Price.COINS; coin++) {
                box(sellX() + coin * BOX_STEP, rowsY() + row * ROW, sell, row, coin);
                box(buyX() + coin * BOX_STEP, rowsY() + row * ROW, buy, row, coin);
            }
        }
        int y = top() + height() - PADDING - BUTTON_HEIGHT;
        int x = left() + PADDING;
        int span = WIDTH - 2 * PADDING;
        int width = (span - 3 * 4) / 4;
        Button same = Button.builder(Component.translatable("gui.halgventmod.stall.same_for_all"), button -> sameForAll())
                .bounds(x, y, width, BUTTON_HEIGHT).build();
        same.active = rows > 1;
        addRenderableWidget(same);
        Button remove = Button.builder(Component.translatable("gui.halgventmod.stall.remove"), button -> {
            PacketDistributor.sendToServer(StallEditPayload.of(StallEditPayload.Action.REMOVE_OFFER, kind.item()));
            onClose();
        }).bounds(x + width + 4, y, width, BUTTON_HEIGHT).build();
        remove.active = kind.offered();
        addRenderableWidget(remove);
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.stall.cancel"), button -> onClose())
                .bounds(x + 2 * (width + 4), y, width, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.stall.save"), button -> save())
                .bounds(x + 3 * (width + 4), y, width, BUTTON_HEIGHT).build());
    }

    private void box(int x, int y, String[][] texts, int row, int coin) {
        EditBox box = new EditBox(font, x, y, BOX_WIDTH, ROW - 2, Component.empty());
        box.setMaxLength(3);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setValue(texts[row][coin]);
        box.setResponder(text -> texts[row][coin] = text);
        addRenderableWidget(box);
    }

    // Строку «Обычное» — во все качества
    private void sameForAll() {
        int base = Math.min(rows - 1, 2); // GearQuality.Grade.MEDIOCRE
        for (int row = 0; row < rows; row++) {
            sell[row] = sell[base].clone();
            buy[row] = buy[base].clone();
        }
        rebuildWidgets();
    }

    private void save() {
        PacketDistributor.sendToServer(new StallEditPayload(StallEditPayload.Action.SET_PRICES, kind.item(),
                flat(sell), flat(buy)));
        onClose();
    }

    private static int[] flat(String[][] texts) {
        int[] flat = new int[texts.length * Price.COINS];
        for (int row = 0; row < texts.length; row++) {
            for (int coin = 0; coin < Price.COINS; coin++) {
                String text = texts[row][coin];
                flat[row * Price.COINS + coin] = text.isEmpty() ? 0 : Integer.parseInt(text);
            }
        }
        return flat;
    }

    // Окно лежит поверх прилавка — закрыть только себя
    @Override
    public void onClose() {
        minecraft.popGuiLayer();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        ConquestUi.window(graphics, left(), top(), left() + WIDTH, top() + height());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left() + PADDING;
        int y = top() + PADDING;
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(kind.item()));
        graphics.renderItem(stack, x, y);
        graphics.drawString(font, stack.getHoverName(), x + 20, y + 4, COLOR_TITLE, true);
        graphics.drawString(font, Component.translatable("gui.halgventmod.stall.stock", kind.stock()),
                left() + WIDTH - PADDING - font.width(Component.translatable("gui.halgventmod.stall.stock", kind.stock())),
                y + 4, COLOR_DIM, false);
        // Заголовки групп и монеты над столбцами
        graphics.drawString(font, Component.translatable("gui.halgventmod.stall.sell_full"), sellX(), top() + 30, COLOR_TEXT, false);
        graphics.drawString(font, Component.translatable("gui.halgventmod.stall.buy_full"), buyX(), top() + 30, COLOR_TEXT, false);
        for (Coin coin : Coin.values()) {
            graphics.renderItem(coin.stack(1), sellX() + coin.ordinal() * BOX_STEP + 5, top() + 44);
            graphics.renderItem(coin.stack(1), buyX() + coin.ordinal() * BOX_STEP + 5, top() + 44);
        }
        for (int row = 0; row < rows; row++) {
            Component label = kind.graded() ? Component.translatable("gui.halgventmod.stall.grade." + row)
                    : Component.translatable("gui.halgventmod.stall.price");
            graphics.drawString(font, label, x, rowsY() + row * ROW + 4, COLOR_TEXT, false);
        }
        // Подсказка: название монеты под мышью над значком
        for (Coin coin : Coin.values()) {
            for (int groupX : new int[] {sellX(), buyX()}) {
                int cx = groupX + coin.ordinal() * BOX_STEP + 5;
                if (mouseX >= cx && mouseX < cx + 16 && mouseY >= top() + 44 && mouseY < top() + 60) {
                    graphics.renderTooltip(font, coin.title(), mouseX, mouseY);
                }
            }
        }
    }
}
