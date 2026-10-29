package com.nrdhvnstd.halgventmod.client.trade;

import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.client.StorageSlotOverlay;
import com.nrdhvnstd.halgventmod.menu.MarketStallMenu;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.network.StallEditPayload;
import com.nrdhvnstd.halgventmod.network.StallSyncPayload;
import com.nrdhvnstd.halgventmod.trade.MarketStallBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.trade.Price;
import com.nrdhvnstd.halgventmod.trade.StallKind;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно прилавка для хозяина: товар 9×3, ниже — свой инвентарь, справа панель из трёх вкладок.
 * «Товары» — список видов (с ценой и просто лежащих): значок с числом лежащих, имя, короткая цена продажи; щелчок
 * открывает поверх окно цен этого вида (StallPriceScreen). «Запросы» — чего не хватило покупателям. Денег у прилавка
 * нет: выручка и скупка — через кошелёк НПС-торговца (его лавка, вкладка «Касса» у хозяина).
 */
public class MarketStallScreen extends AbstractContainerScreen<MarketStallMenu> {
    private enum Tab {
        GOODS("gui.halgventmod.stall.tab.goods"),
        REQUESTS("gui.halgventmod.stall.tab.requests");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private static final int TITLE_GAP = 2;
    private static final int PANEL_X = PlayerArmorMenu.PANEL_LEFT;
    private static final int PANEL_WIDTH = PlayerArmorMenu.PANEL_RIGHT - PlayerArmorMenu.PANEL_LEFT;
    private static final int TAB_HEIGHT = 14;
    private static final int GOOD_ROW = 19;
    private static final int GOOD_ROWS = 7;
    private static final int REQUEST_LINES = 9;
    private static final int COLOR_TEXT = 0xFFE8E0D0;
    private static final int COLOR_DIM = 0xFFA09888;
    private static final int FIGURE_SCALE = 30;
    private static final float FIGURE_Y_OFFSET = 0.0625F;

    // Открытое окно прилавка (данные с сервера приходят и пока поверх открыто окно цен)
    @Nullable
    private static MarketStallScreen current;
    @Nullable
    private static StallSyncPayload pending;

    private Tab tab = Tab.GOODS;
    private List<StallSyncPayload.Kind> kinds = List.of();
    @Nullable
    private StallSyncPayload.Tap tap; // у бочонка с краном
    private List<MarketStallBlockEntity.Request> requests = List.of();
    private int scroll;
    @Nullable
    private ArmorStand preview; // манекен бронного прилавка в окне

    public MarketStallScreen(MarketStallMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MarketStallMenu.WIDTH;
        imageHeight = MarketStallMenu.HEIGHT;
    }

    /** StallSyncPayload: обновить открытое окно этого прилавка. */
    public static void sync(StallSyncPayload payload) {
        if (current != null && current.menu.pos().equals(payload.pos())) {
            current.apply(payload);
        } else {
            pending = payload;
        }
    }

    private void apply(StallSyncPayload payload) {
        kinds = payload.kinds();
        requests = payload.requests();
        tap = payload.tap();
        if (minecraft != null) { // и под окном цен: строки товаров должны совпасть с новыми данными
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        super.init();
        current = this;
        topPos = Math.max(topPos, font.lineHeight + TITLE_GAP);
        if (pending != null && pending.pos().equals(menu.pos())) {
            StallSyncPayload payload = pending;
            pending = null;
            kinds = payload.kinds();
            requests = payload.requests();
            tap = payload.tap();
        }
        int x = leftPos + PANEL_X;
        int y = topPos + PlayerArmorMenu.PANEL_TOP;
        if (menu.kind() == StallKind.STORAGE) {
            return; // у хранилища нет цен и запросов — только ячейки
        }
        int tabWidth = (PANEL_WIDTH - 4) / Tab.values().length;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab each = Tab.values()[i];
            String key = each == Tab.GOODS && menu.kind() == StallKind.TAP ? "gui.halgventmod.stall.tab.tap" : each.key;
            Button button = Button.builder(Component.translatable(key), pressed -> {
                tab = each;
                rebuildWidgets();
            }).bounds(x + i * (tabWidth + 2), y, tabWidth, TAB_HEIGHT).build();
            button.active = each != tab;
            addRenderableWidget(button);
        }
        y += TAB_HEIGHT + 4;
        switch (tab) {
            case GOODS -> {
                if (menu.kind() == StallKind.TAP) {
                    initTap(x, y);
                } else {
                    initGoods(x, y);
                }
            }
            case REQUESTS -> addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.stall.clear_requests"),
                    button -> send(StallEditPayload.of(StallEditPayload.Action.CLEAR_REQUESTS)))
                    .bounds(x, topPos + PlayerArmorMenu.PANEL_BOTTOM - TAB_HEIGHT, PANEL_WIDTH, TAB_HEIGHT).build());
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (current == this) {
            current = null;
        }
    }

    // Розлив: «Цены» (мера и залоги — окно поверх) и «Вылить»; налитое и кружки рисует drawTap
    private void initTap(int x, int y) {
        int bottom = topPos + PlayerArmorMenu.PANEL_BOTTOM - TAB_HEIGHT;
        int half = (PANEL_WIDTH - 2) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.stall.tap_prices"), button -> {
            if (tap != null) {
                minecraft.pushGuiLayer(new TapPriceScreen(tap.prices()));
            }
        }).bounds(x, bottom, half, TAB_HEIGHT).build());
        Button empty = Button.builder(Component.translatable("gui.halgventmod.stall.tap_empty"),
                button -> send(StallEditPayload.of(StallEditPayload.Action.EMPTY_TAP))).bounds(x + half + 2, bottom, half, TAB_HEIGHT).build();
        empty.active = tap != null && tap.measures() > 0;
        addRenderableWidget(empty);
    }

    private void drawTap(GuiGraphics graphics) {
        int x = leftPos + PANEL_X;
        int y = topPos + PlayerArmorMenu.PANEL_TOP + TAB_HEIGHT + 4;
        if (tap == null || tap.drink().isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("gui.halgventmod.stall.tap_none"), x, y, PANEL_WIDTH, COLOR_DIM);
        } else {
            graphics.renderItem(tap.drink(), x, y);
            graphics.drawString(font, font.plainSubstrByWidth(tap.drink().getHoverName().getString(), PANEL_WIDTH - 20),
                    x + 20, y, COLOR_TEXT, false);
            var grade = com.nrdhvnstd.halgventmod.quality.QualityDisplay.of(tap.drink());
            if (grade != null) {
                graphics.drawString(font, Component.translatable("gui.halgventmod.stall.grade." + grade.ordinal()), x + 20, y + 9,
                        COLOR_DIM, false);
            }
            graphics.drawString(font, Component.translatable("gui.halgventmod.stall.tap_measures", tap.measures(),
                    MarketStallBlockEntity.TAP_CAPACITY), x, y + 22, COLOR_TEXT, false);
        }
        int[] prices = tap == null ? new int[0] : tap.prices();
        int line = y + 36;
        graphics.drawString(font, Component.translatable("gui.halgventmod.stall.tap_cup_price", priceText(prices, 0)), x, line,
                COLOR_DIM, false);
        for (com.nrdhvnstd.halgventmod.trade.Mug mug : com.nrdhvnstd.halgventmod.trade.Mug.values()) {
            line += 10;
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.halgventmod.stall.tap_deposit_" + mug.name().toLowerCase(),
                    priceText(prices, 1 + mug.ordinal())).getString(), PANEL_WIDTH), x, line, COLOR_DIM, false);
        }
        graphics.drawString(font, Component.translatable("gui.halgventmod.stall.tap_mugs", tap == null ? 0 : tap.mugs()), x,
                line + 14, COLOR_TEXT, false);
        graphics.drawWordWrap(font, Component.translatable("gui.halgventmod.stall.tap_hint"), x, line + 26, PANEL_WIDTH, COLOR_DIM);
    }

    // Цена строки row из плоского массива; не задана — «—»
    private static Component priceText(int[] flat, int row) {
        if (flat.length < (row + 1) * Price.COINS) {
            return Component.literal("—");
        }
        int[] price = java.util.Arrays.copyOfRange(flat, row * Price.COINS, (row + 1) * Price.COINS);
        return Price.isSet(price) ? Price.text(price) : Component.literal("—");
    }

    // Строка вида — кнопка на всю ширину (значок рисуется поверх слева); щелчок — окно цен
    private void initGoods(int x, int y) {
        scroll = Mth.clamp(scroll, 0, Math.max(0, kinds.size() - GOOD_ROWS));
        for (int i = 0; i < GOOD_ROWS && scroll + i < kinds.size(); i++) {
            StallSyncPayload.Kind kind = kinds.get(scroll + i);
            addRenderableWidget(Button.builder(Component.empty(), button -> minecraft.pushGuiLayer(new StallPriceScreen(kind)))
                    .bounds(x, y + i * GOOD_ROW, PANEL_WIDTH, GOOD_ROW - 1).build());
        }
    }

    private static void send(StallEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    // Колесо над панелью листает список товаров
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.GOODS && mouseX >= leftPos + PANEL_X) {
            int next = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, kinds.size() - GOOD_ROWS));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos, topPos - font.lineHeight - TITLE_GAP, 0xFFFFFF, true);
        // У хранилища панели нет — только подсказка (renderBg)
        if (tab == Tab.GOODS && menu.kind() == StallKind.TAP) {
            drawTap(graphics);
        } else if (tab == Tab.GOODS && menu.kind() != StallKind.STORAGE) {
            drawGoods(graphics); // поверх кнопок строк
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        ConquestUi.window(graphics, x, y, x + imageWidth, y + imageHeight);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConquestUi.slot(graphics, x + slot.x, y + slot.y, slot instanceof StorageSlot storage && !storage.isOpen());
            }
        }
        StorageSlotOverlay.drawDividers(graphics, this, x, y);
        if (menu.kind() == StallKind.ARMOR) {
            // Манекен в броне из ячеек — справа от сетки, как фигурка в окне инвентаря
            ConquestUi.inset(graphics, x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                    x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM);
            ArmorStand stand = mannequin();
            if (stand != null) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                        x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                        x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM,
                        FIGURE_SCALE, FIGURE_Y_OFFSET, mouseX, mouseY, stand);
            }
        }
        int px = x + PANEL_X;
        int py = y + PlayerArmorMenu.PANEL_TOP + TAB_HEIGHT + 4;
        if (menu.kind() == StallKind.STORAGE) { // хранилище: только подсказка
            graphics.drawWordWrap(font, Component.translatable("gui.halgventmod.stall.storage_hint"), x + PANEL_X,
                    y + PlayerArmorMenu.PANEL_TOP, PANEL_WIDTH, COLOR_DIM);
            return;
        }
        switch (tab) {
            case GOODS -> {
                if (kinds.isEmpty() && menu.kind() != StallKind.TAP) {
                    graphics.drawWordWrap(font, Component.translatable("gui.halgventmod.stall.empty"), px, py, PANEL_WIDTH, COLOR_DIM);
                }
            }
            case REQUESTS -> drawRequests(graphics, px, py);
        }
    }

    // Значок с числом лежащих, имя (тусклое — без цены) и короткая цена продажи самого дешёвого качества
    private void drawGoods(GuiGraphics graphics) {
        int x = leftPos + PANEL_X;
        int y = topPos + PlayerArmorMenu.PANEL_TOP + TAB_HEIGHT + 4;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200); // над кнопками
        for (int i = 0; i < GOOD_ROWS && scroll + i < kinds.size(); i++) {
            StallSyncPayload.Kind kind = kinds.get(scroll + i);
            int rowY = y + i * GOOD_ROW;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(kind.item()));
            graphics.renderItem(stack, x + 1, rowY + 1);
            graphics.renderItemDecorations(font, stack, x + 1, rowY + 1, Integer.toString(kind.stock()));
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(), PANEL_WIDTH - 22);
            graphics.drawString(font, name, x + 20, rowY + 2, kind.offered() ? COLOR_TEXT : COLOR_DIM, false);
            Component price = kind.offered() ? cheapest(kind) : Component.translatable("gui.halgventmod.stall.no_offer");
            graphics.drawString(font, font.plainSubstrByWidth(price.getString(), PANEL_WIDTH - 22), x + 20, rowY + 10, COLOR_DIM, false);
        }
        graphics.pose().popPose();
    }

    // Первая заданная цена продажи: «2 × Талер»; ни одной — «только скупка»
    private static Component cheapest(StallSyncPayload.Kind kind) {
        int[] flat = kind.sell();
        for (int row = 0; row * Price.COINS < flat.length; row++) {
            int[] price = java.util.Arrays.copyOfRange(flat, row * Price.COINS, row * Price.COINS + Price.COINS);
            if (Price.isSet(price)) {
                return Price.text(price);
            }
        }
        return Component.translatable("gui.halgventmod.stall.buy_only");
    }

    // Манекен для окна: стойка для брони с бронёй из ячеек; броня перекладывается, только когда ячейки изменились
    @Nullable
    private ArmorStand mannequin() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        if (preview == null) {
            preview = new ArmorStand(EntityType.ARMOR_STAND, minecraft.level);
            preview.setNoBasePlate(true);
            preview.setShowArms(true);
        }
        ArmorInventory armor = preview.getData(ModAttachments.ARMOR);
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = menu.getSlot(MarketStallMenu.STOCK_START + slot.ordinal()).getItem();
            if (!ItemStack.matches(stack, armor.get(slot))) {
                armor.set(slot, stack.copy());
            }
        }
        return preview;
    }

    private void drawRequests(GuiGraphics graphics, int x, int y) {
        if (requests.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("gui.halgventmod.stall.no_requests"), x, y, PANEL_WIDTH, COLOR_DIM);
            return;
        }
        int from = Math.max(0, requests.size() - REQUEST_LINES); // последние
        for (int i = from; i < requests.size(); i++) {
            MarketStallBlockEntity.Request request = requests.get(i);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(request.item()));
            graphics.renderFakeItem(stack, x, y - 4);
            String line = request.buyer() + ": " + stack.getHoverName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(line, PANEL_WIDTH - 18), x + 18, y, COLOR_TEXT, false);
            y += 14;
        }
    }
}
