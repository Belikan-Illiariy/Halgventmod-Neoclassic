package com.nrdhvnstd.halgventmod.client.trade;

import java.util.List;

import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.network.ShopActionPayload;
import com.nrdhvnstd.halgventmod.network.ShopOpenPayload;
import com.nrdhvnstd.halgventmod.trade.Coin;
import com.nrdhvnstd.halgventmod.trade.Money;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Лавка НПС-торговца для покупателя. «Купить» — товар прилавков хозяина: цена набором монет (значок монеты и
 * сколько), сколько есть; кончилось — «Заказать» (запрос хозяину). «Продать» — что торговец скупает и почём; у вас
 * такое есть — «Продать». Внизу — ваши монеты. Колесо мыши листает. После каждой сделки сервер присылает лавку заново.
 */
public class ShopScreen extends Screen {
    private enum Tab { BUY, SELL, EXCHANGE, TILL }

    private static final int WIDTH = 360;
    private static final int HEIGHT = 222;
    private static final int PADDING = 10;
    private static final int ROW = 22;
    private static final int ROWS = 7;
    private static final int BUTTON_WIDTH = 62;
    private static final int TAB_WIDTH = 70;
    private static final int COIN_STEP = 34; // значок монеты и «×12»
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_TEXT = 0xFFE8E0D0;
    private static final int COLOR_DIM = 0xFFA09888;
    private static final int COLOR_OUT = 0xFFC06050;

    private ShopOpenPayload data;
    private Tab tab = Tab.BUY;
    private int scroll;

    private ShopScreen(ShopOpenPayload data) {
        super(Component.translatable("gui.halgventmod.shop.title", data.merchant()));
        this.data = data;
    }

    /** ShopOpenPayload: обновить открытую лавку того же НПС или открыть новую. */
    public static void open(ShopOpenPayload data) {
        if (Minecraft.getInstance().screen instanceof ShopScreen screen && screen.data.npcId() == data.npcId()) {
            screen.data = data;
            screen.rebuildWidgets();
        } else {
            Minecraft.getInstance().setScreen(new ShopScreen(data));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<ShopOpenPayload.Entry> entries() {
        return tab == Tab.BUY ? data.sell() : tab == Tab.SELL ? data.buy() : List.of();
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private int listTop() {
        return top() + PADDING + font.lineHeight + 22;
    }

    private int priceX() {
        return left() + WIDTH - PADDING - BUTTON_WIDTH - 6 - Coin.values().length * COIN_STEP;
    }

    @Override
    protected void init() {
        int x = left() + PADDING;
        int y = top() + PADDING + font.lineHeight + 2;
        if (tab == Tab.TILL && !data.owner()) {
            tab = Tab.BUY;
        }
        for (Tab each : Tab.values()) {
            if (each == Tab.TILL && !data.owner()) {
                continue; // касса — только хозяину торговца
            }
            Button button = Button.builder(Component.translatable("gui.halgventmod.shop.tab." + each.name().toLowerCase()), pressed -> {
                tab = each;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x, y, TAB_WIDTH, 16).build();
            button.active = each != tab;
            addRenderableWidget(button);
            x += TAB_WIDTH + 4;
        }
        if (tab == Tab.EXCHANGE) {
            initExchange();
            return;
        }
        if (tab == Tab.TILL) {
            initTill();
            return;
        }
        List<ShopOpenPayload.Entry> entries = entries();
        scroll = Mth.clamp(scroll, 0, Math.max(0, entries.size() - ROWS));
        int buttonX = left() + WIDTH - PADDING - BUTTON_WIDTH;
        for (int i = 0; i < ROWS && scroll + i < entries.size(); i++) {
            ShopOpenPayload.Entry entry = entries.get(scroll + i);
            int rowY = listTop() + i * ROW + 2;
            Button button;
            if (entry.type() == ShopOpenPayload.Entry.TAP) {
                // Розлив: «Налить» — в посуду в руке, «С кружкой» — в кружку торговца (с залогом)
                Button mug = Button.builder(Component.translatable("gui.halgventmod.shop.tap_mug"),
                        pressed -> act(ShopActionPayload.Action.TAP_MUG, entry)).bounds(buttonX - BUTTON_WIDTH - 2, rowY, BUTTON_WIDTH, 18)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                                "gui.halgventmod.shop.tap_mug_hint", com.nrdhvnstd.halgventmod.trade.Price.text(entry.deposit()))))
                        .build();
                mug.active = entry.mugs() > 0;
                addRenderableWidget(mug);
                button = Button.builder(Component.translatable("gui.halgventmod.shop.tap_own"),
                        pressed -> act(ShopActionPayload.Action.TAP_OWN, entry)).bounds(buttonX, rowY, BUTTON_WIDTH, 18)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.halgventmod.shop.tap_own_hint")))
                        .build();
            } else if (entry.type() == ShopOpenPayload.Entry.MUG) {
                button = Button.builder(Component.translatable("gui.halgventmod.shop.return_mug"),
                        pressed -> act(ShopActionPayload.Action.MUG_RETURN, entry)).bounds(buttonX, rowY, BUTTON_WIDTH, 18).build();
                button.active = entry.count() > 0;
            } else if (tab == Tab.BUY) {
                boolean inStock = entry.count() > 0;
                button = Button.builder(Component.translatable(inStock ? "gui.halgventmod.shop.buy" : "gui.halgventmod.shop.request"),
                        pressed -> act(inStock ? ShopActionPayload.Action.BUY : ShopActionPayload.Action.REQUEST, entry))
                        .bounds(buttonX, rowY, BUTTON_WIDTH, 18).build();
            } else {
                button = Button.builder(Component.translatable("gui.halgventmod.shop.sell"),
                        pressed -> act(ShopActionPayload.Action.SELL, entry)).bounds(buttonX, rowY, BUTTON_WIDTH, 18).build();
                button.active = entry.count() > 0;
            }
            addRenderableWidget(button);
        }
    }

    // Обмен: все пары монет «отдать → получить» (Coin.exchange — без сдачи)
    private static final List<Coin[]> PAIRS = pairs();

    private static List<Coin[]> pairs() {
        List<Coin[]> pairs = new java.util.ArrayList<>();
        for (Coin from : Coin.values()) {
            for (Coin to : Coin.values()) {
                if (from != to) {
                    pairs.add(new Coin[] {from, to});
                }
            }
        }
        return List.copyOf(pairs);
    }

    // Строка пары — «Обменять» (Shift — сколько хватит)
    private void initExchange() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, PAIRS.size() - ROWS));
        int buttonX = left() + WIDTH - PADDING - BUTTON_WIDTH;
        for (int i = 0; i < ROWS && scroll + i < PAIRS.size(); i++) {
            Coin[] pair = PAIRS.get(scroll + i);
            int[] rate = Coin.exchange(pair[0], pair[1]);
            Button button = Button.builder(Component.translatable("gui.halgventmod.shop.exchange"), pressed ->
                    PacketDistributor.sendToServer(new ShopActionPayload(data.npcId(), ShopActionPayload.Action.EXCHANGE,
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"),
                            pair[0].ordinal() * com.nrdhvnstd.halgventmod.trade.Price.COINS + pair[1].ordinal(),
                            new int[] {hasShiftDown() ? 1 : 0, 0, 0, 0})))
                    .bounds(buttonX, listTop() + i * ROW + 2, BUTTON_WIDTH, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.halgventmod.shop.exchange_hint")))
                    .build();
            button.active = Money.count(minecraft.player, pair[0]) >= rate[0];
            addRenderableWidget(button);
        }
    }

    // «2 × талер → 5 × марка» и сколько отдаваемых монет у вас
    private void drawExchange(GuiGraphics graphics, int x, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.halgventmod.shop.exchange_rate"), x, top() + HEIGHT - PADDING - 10,
                COLOR_DIM, false);
        Coin hoveredCoin = null;
        for (int i = 0; i < ROWS && scroll + i < PAIRS.size(); i++) {
            Coin[] pair = PAIRS.get(scroll + i);
            int[] rate = Coin.exchange(pair[0], pair[1]);
            int rowY = listTop() + i * ROW;
            graphics.renderItem(pair[0].stack(1), x, rowY + 3);
            graphics.drawString(font, "×" + rate[0] + "  →", x + 18, rowY + 7, COLOR_TEXT, false);
            graphics.renderItem(pair[1].stack(1), x + 60, rowY + 3);
            graphics.drawString(font, "×" + rate[1], x + 78, rowY + 7, COLOR_TEXT, false);
            graphics.drawString(font, Component.translatable("gui.halgventmod.shop.you_have",
                    Money.count(minecraft.player, pair[0])), x + 110, rowY + 7, COLOR_DIM, false);
            for (int k = 0; k < 2; k++) {
                int cx = x + k * 60;
                if (mouseX >= cx && mouseX < cx + 16 && mouseY >= rowY + 3 && mouseY < rowY + 19) {
                    hoveredCoin = pair[k];
                }
            }
        }
        if (hoveredCoin != null) {
            graphics.renderTooltip(font, hoveredCoin.title(), mouseX, mouseY);
        }
    }

    // Касса хозяина: по строке на монету — «Внести» все такие монеты из инвентаря; внизу «Забрать всё»
    private void initTill() {
        int buttonX = left() + WIDTH - PADDING - BUTTON_WIDTH;
        int y = listTop();
        for (Coin coin : Coin.values()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.shop.deposit"),
                    pressed -> till(ShopActionPayload.Action.DEPOSIT, coin.ordinal())).bounds(buttonX, y + 2, BUTTON_WIDTH, 18).build());
            y += ROW;
        }
        Button withdraw = Button.builder(Component.translatable("gui.halgventmod.shop.withdraw"),
                pressed -> till(ShopActionPayload.Action.WITHDRAW, 0)).bounds(buttonX, y + 6, BUTTON_WIDTH, 18).build();
        withdraw.active = com.nrdhvnstd.halgventmod.trade.Price.isSet(data.purse());
        addRenderableWidget(withdraw);
    }

    private void till(ShopActionPayload.Action action, int coin) {
        PacketDistributor.sendToServer(new ShopActionPayload(data.npcId(), action,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"), coin, new int[0]));
    }

    private void act(ShopActionPayload.Action action, ShopOpenPayload.Entry entry) {
        PacketDistributor.sendToServer(new ShopActionPayload(data.npcId(), action, entry.item(), entry.grade(), entry.price()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, (tab == Tab.EXCHANGE ? PAIRS.size() : entries().size()) - ROWS);
        int next = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, max);
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        ConquestUi.window(graphics, left(), top(), left() + WIDTH, top() + HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left() + PADDING;
        graphics.drawString(font, title, x, top() + PADDING, COLOR_TITLE, true);
        if (tab == Tab.EXCHANGE) {
            drawExchange(graphics, x, mouseX, mouseY);
            return;
        }
        if (tab == Tab.TILL) {
            drawTill(graphics, x, mouseX, mouseY);
            return;
        }
        List<ShopOpenPayload.Entry> entries = entries();
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable(tab == Tab.BUY ? "gui.halgventmod.shop.nothing_for_sale"
                    : "gui.halgventmod.shop.nothing_wanted"), x, listTop() + 6, COLOR_DIM, false);
        }
        ShopOpenPayload.Entry hovered = null;
        Coin hoveredCoin = null;
        int priceX = priceX();
        for (int i = 0; i < ROWS && scroll + i < entries.size(); i++) {
            ShopOpenPayload.Entry entry = entries.get(scroll + i);
            int rowY = listTop() + i * ROW;
            graphics.renderItem(entry.display(), x, rowY + 3);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= rowY + 3 && mouseY < rowY + 19) {
                hovered = entry;
            }
            int nameLimit = (entry.type() == ShopOpenPayload.Entry.TAP ? priceX - BUTTON_WIDTH - 2 : priceX) - x - 24;
            String name = font.plainSubstrByWidth(entry.display().getHoverName().getString(), nameLimit);
            graphics.drawString(font, name, x + 20, rowY + 2, COLOR_TEXT, false);
            // Вторая строка: качество и сколько есть (у вас)
            MutableComponent info = Component.empty();
            if (entry.graded()) {
                info.append(Component.translatable("gui.halgventmod.stall.grade." + entry.grade())).append(" · ");
            }
            boolean out = tab == Tab.BUY && entry.count() <= 0;
            if (entry.type() == ShopOpenPayload.Entry.TAP) {
                info.append(Component.translatable("gui.halgventmod.shop.tap_info", entry.count(), entry.mugs()));
            } else {
                info.append(tab == Tab.BUY
                        ? (out ? Component.translatable("gui.halgventmod.shop.sold_out")
                                : Component.translatable("gui.halgventmod.shop.in_stock", entry.count()))
                        : Component.translatable("gui.halgventmod.shop.you_have", entry.count()));
            }
            graphics.drawString(font, font.plainSubstrByWidth(info.getString(), nameLimit), x + 20, rowY + 12,
                    out ? COLOR_OUT : COLOR_DIM, false);
            // Цена: монеты, которые в ней есть (у розлива справа две кнопки — цена левее)
            int cx = entry.type() == ShopOpenPayload.Entry.TAP ? priceX - BUTTON_WIDTH - 2 : priceX;
            for (Coin coin : Coin.values()) {
                int amount = coin.ordinal() < entry.price().length ? entry.price()[coin.ordinal()] : 0;
                if (amount <= 0) {
                    continue;
                }
                graphics.renderItem(coin.stack(1), cx, rowY + 3);
                graphics.drawString(font, "×" + amount, cx + 17, rowY + 7, COLOR_TEXT, false);
                if (mouseX >= cx && mouseX < cx + 16 && mouseY >= rowY + 3 && mouseY < rowY + 19) {
                    hoveredCoin = coin;
                }
                cx += COIN_STEP;
            }
        }
        // Ваши монеты
        int coinsY = top() + HEIGHT - PADDING - 16;
        graphics.drawString(font, Component.translatable("gui.halgventmod.shop.your_coins"), x, coinsY + 4, COLOR_DIM, false);
        int coinX = x + 80;
        for (Coin coin : Coin.values()) {
            graphics.renderItem(coin.stack(1), coinX, coinsY);
            graphics.drawString(font, "× " + Money.count(minecraft.player, coin), coinX + 18, coinsY + 4, COLOR_TEXT, false);
            if (mouseX >= coinX && mouseX < coinX + 16 && mouseY >= coinsY && mouseY < coinsY + 16) {
                hoveredCoin = coin;
            }
            coinX += 60;
        }
        if (hovered != null) {
            graphics.renderTooltip(font, hovered.display(), mouseX, mouseY);
        } else if (hoveredCoin != null) {
            graphics.renderTooltip(font, hoveredCoin.title(), mouseX, mouseY);
        }
    }

    // Касса: сколько каждой монеты в кошельке торговца и сколько у вас при себе
    private void drawTill(GuiGraphics graphics, int x, int mouseX, int mouseY) {
        int y = listTop();
        Coin hoveredCoin = null;
        for (Coin coin : Coin.values()) {
            graphics.renderItem(coin.stack(1), x, y + 3);
            graphics.drawString(font, coin.title(), x + 20, y + 3, COLOR_TEXT, false);
            graphics.drawString(font, Component.translatable("gui.halgventmod.shop.purse_line", data.purse()[coin.ordinal()],
                    Money.count(minecraft.player, coin)), x + 20, y + 12, COLOR_DIM, false);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 3 && mouseY < y + 19) {
                hoveredCoin = coin;
            }
            y += ROW;
        }
        graphics.drawWordWrap(font, Component.translatable("gui.halgventmod.shop.till_hint"), x, y + 8,
                WIDTH - 2 * PADDING - BUTTON_WIDTH - 8, COLOR_DIM);
        if (hoveredCoin != null) {
            graphics.renderTooltip(font, hoveredCoin.title(), mouseX, mouseY);
        }
    }
}
