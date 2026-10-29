package com.nrdhvnstd.halgventmod.client.trade;

import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.network.StallEditPayload;
import com.nrdhvnstd.halgventmod.trade.Coin;
import com.nrdhvnstd.halgventmod.trade.MarketStallBlockEntity;
import com.nrdhvnstd.halgventmod.trade.Mug;
import com.nrdhvnstd.halgventmod.trade.Price;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Цены бочонка с краном — поверх окна бочонка. Строки: «Кружка питья» (цена одной меры: в свою посуду покупатель платит
 * её за каждую меру), «Залог: глиняная кружка» и «Залог: пьютеровая кружка» (сколько берут сверх цены, когда наливают
 * в кружку торговца, и сколько отдают, когда её сдают пустой). Столбцы — монеты. Пустая строка: не продаётся (залога нет —
 * кружку торговца не дают и пустые не принимают).
 */
public class TapPriceScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int PADDING = 10;
    private static final int LABEL_WIDTH = 104;
    private static final int BOX_WIDTH = 26;
    private static final int BOX_STEP = 28;
    private static final int ROW = 18;
    private static final int BUTTON_HEIGHT = 18;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_TEXT = 0xFFE8E0D0;

    private final String[][] texts = new String[MarketStallBlockEntity.TAP_ROWS][Price.COINS];

    public TapPriceScreen(int[] flat) {
        super(Component.translatable("gui.halgventmod.stall.tap_prices_title"));
        for (int row = 0; row < MarketStallBlockEntity.TAP_ROWS; row++) {
            for (int coin = 0; coin < Price.COINS; coin++) {
                int index = row * Price.COINS + coin;
                texts[row][coin] = index < flat.length && flat[index] > 0 ? Integer.toString(flat[index]) : "";
            }
        }
    }

    private int height() {
        return 52 + MarketStallBlockEntity.TAP_ROWS * ROW + BUTTON_HEIGHT + 2 * PADDING;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - height()) / 2;
    }

    private int boxesX() {
        return left() + PADDING + LABEL_WIDTH;
    }

    private int rowsY() {
        return top() + 46;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        for (int row = 0; row < MarketStallBlockEntity.TAP_ROWS; row++) {
            for (int coin = 0; coin < Price.COINS; coin++) {
                int r = row;
                int c = coin;
                EditBox box = new EditBox(font, boxesX() + coin * BOX_STEP, rowsY() + row * ROW, BOX_WIDTH, ROW - 2, Component.empty());
                box.setMaxLength(3);
                box.setFilter(text -> text.chars().allMatch(Character::isDigit));
                box.setValue(texts[row][coin]);
                box.setResponder(text -> texts[r][c] = text);
                addRenderableWidget(box);
            }
        }
        int y = top() + height() - PADDING - BUTTON_HEIGHT;
        int half = (WIDTH - 2 * PADDING - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.stall.cancel"), button -> onClose())
                .bounds(left() + PADDING, y, half, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.stall.save"), button -> save())
                .bounds(left() + PADDING + half + 4, y, half, BUTTON_HEIGHT).build());
    }

    private void save() {
        int[] flat = new int[MarketStallBlockEntity.TAP_ROWS * Price.COINS];
        for (int row = 0; row < MarketStallBlockEntity.TAP_ROWS; row++) {
            for (int coin = 0; coin < Price.COINS; coin++) {
                String text = texts[row][coin];
                flat[row * Price.COINS + coin] = text.isEmpty() ? 0 : Integer.parseInt(text);
            }
        }
        PacketDistributor.sendToServer(new StallEditPayload(StallEditPayload.Action.SET_TAP_PRICES,
                ResourceLocation.withDefaultNamespace("air"), flat, new int[0]));
        onClose();
    }

    // Окно лежит поверх бочонка — закрыть только себя
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
        graphics.drawString(font, title, x, top() + PADDING, COLOR_TITLE, true);
        for (Coin coin : Coin.values()) {
            int cx = boxesX() + coin.ordinal() * BOX_STEP + 5;
            graphics.renderItem(coin.stack(1), cx, top() + 26);
            if (mouseX >= cx && mouseX < cx + 16 && mouseY >= top() + 26 && mouseY < top() + 42) {
                graphics.renderTooltip(font, coin.title(), mouseX, mouseY);
            }
        }
        graphics.drawString(font, Component.translatable("gui.halgventmod.stall.tap_row_cup"), x, rowsY() + 4, COLOR_TEXT, false);
        for (Mug mug : Mug.values()) {
            graphics.drawString(font, Component.translatable("gui.halgventmod.stall.tap_row_" + mug.name().toLowerCase()), x,
                    rowsY() + (1 + mug.ordinal()) * ROW + 4, COLOR_TEXT, false);
        }
    }
}
