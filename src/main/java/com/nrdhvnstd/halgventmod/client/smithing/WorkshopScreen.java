package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.menu.CarpenterBenchMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.menu.WorkshopMenu;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.smithing.Crucible;
import com.nrdhvnstd.halgventmod.smithing.ForgeOp;
import com.nrdhvnstd.halgventmod.smithing.SmithProducts;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Окно наковальни и литейного стола ({@link WorkshopMenu}): слева вкладки (металл, инструменты, оружие, доспехи;
 * у стола — слитки и отливки из тигля) и изделия вкладки (колесо — листать), посередине ячейки станции, справа — что выйдет
 * и инструменты этапов, под ячейками — что нужно (красное — не хватает). «Ковать» / «Лить» — этапы работы
 * (SmithWorkScreen), окно на это время закрывается. Литейному столу нужен жидкий тигель при себе (из кузнечного горна).
 */
public class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {
    private static final ResourceLocation PLAQUE = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/title");
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int TITLE_GAP = 2;
    private static final int TEXT_DIM = 0xFFAA9C80;
    private static final int MISSING_BG = 0x70C03028;
    private static final int GHOST_VEIL = 0x608B8B8B;
    private static final int SELECTED = 0xFFFFD27F;
    private static final int TAB = 20;

    private record Ghost(ItemStack stack, int count, boolean have) {}

    private final List<SmithProducts.Product> products;
    private final List<SmithProducts.Tab> tabs = new ArrayList<>();
    private final List<Integer> visible = new ArrayList<>();
    private SmithProducts.Tab tab;
    private int scroll;
    private Button workButton;

    public WorkshopScreen(WorkshopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WorkshopMenu.WIDTH;
        this.imageHeight = WorkshopMenu.HEIGHT;
        this.products = menu.products();
        for (SmithProducts.Product product : products) {
            if (!tabs.contains(product.tab())) {
                tabs.add(product.tab());
            }
        }
        tab = tabs.isEmpty() ? SmithProducts.Tab.METAL : tabs.get(0);
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, font.lineHeight + TITLE_GAP);
        leftPos = Math.max(leftPos, TAB + 4);
        workButton = addRenderableWidget(Button.builder(Component.translatable(menu.casting()
                        ? "gui.halgventmod.workshop.cast" : "gui.halgventmod.workshop.forge"),
                button -> click(WorkshopMenu.BUTTON_WORK))
                .bounds(leftPos + CarpenterBenchMenu.GRID_X - 1, topPos + CarpenterBenchMenu.BUTTONS_Y, 128, 16).build());
        int selected = menu.product();
        if (selected >= 0) {
            tab = products.get(selected).tab();
        }
        refresh();
        int at = visible.indexOf(selected);
        if (at >= 0) {
            scroll = Mth.clamp(at / CarpenterBenchMenu.PRODUCT_COLUMNS - CarpenterBenchMenu.PRODUCT_ROWS + 1, 0, maxScroll());
        }
    }

    private void refresh() {
        visible.clear();
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).tab() == tab) {
                visible.add(i);
            }
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private int maxScroll() {
        return Math.max(0, Mth.positiveCeilDiv(visible.size(), CarpenterBenchMenu.PRODUCT_COLUMNS) - CarpenterBenchMenu.PRODUCT_ROWS);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int selected = menu.product();
        workButton.active = selected >= 0 && products.get(selected).available();
    }

    private int productAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - CarpenterBenchMenu.PRODUCTS_X;
        double ry = mouseY - topPos - CarpenterBenchMenu.PRODUCTS_Y;
        if (rx < 0 || ry < 0 || rx >= CarpenterBenchMenu.PRODUCT_COLUMNS * CarpenterBenchMenu.STEP
                || ry >= CarpenterBenchMenu.PRODUCT_ROWS * CarpenterBenchMenu.STEP) {
            return -1;
        }
        int index = ((int) ry / CarpenterBenchMenu.STEP + scroll) * CarpenterBenchMenu.PRODUCT_COLUMNS
                + (int) rx / CarpenterBenchMenu.STEP;
        return index < visible.size() ? visible.get(index) : -1;
    }

    private int tabAt(double mouseX, double mouseY) {
        double rx = mouseX - (leftPos - TAB - 2);
        double ry = mouseY - (topPos + 4);
        if (rx < 0 || rx >= TAB || ry < 0) {
            return -1;
        }
        int i = (int) ry / (TAB + 2);
        return i < tabs.size() && ry % (TAB + 2) < TAB ? i : -1;
    }

    private static ItemStack tabIcon(SmithProducts.Tab tab) {
        return switch (tab) {
            case METAL -> new ItemStack(SmithingItems.IRON_INGOT.get());
            case TOOLS -> new ItemStack(SmithingItems.SMITH_HAMMER.get());
            case WEAPONS -> new ItemStack(Items.IRON_SWORD);
            case ARMOR -> new ItemStack(ModItems.BASCINET.get());
            case ALLOYS -> new ItemStack(SmithingItems.BRONZE_INGOT.get());
            case CASTINGS -> new ItemStack(Items.BELL);
        };
    }

    // ---------------------------------------------------------------- что нужно

    private List<Ghost> needs(SmithProducts.Product product) {
        List<Ghost> list = new ArrayList<>();
        for (SmithProducts.Need need : product.needs()) {
            if (need.role() == SmithProducts.Role.MELT) {
                // Расплав — в жидком тигле при себе
                int slot = Crucible.find(minecraft.player, need.metal());
                int parts = slot < 0 ? 0 : Crucible.total(Crucible.melt(minecraft.player.getInventory().getItem(slot)));
                list.add(new Ghost(need.icon().get(), need.count(), parts >= need.count() * Crucible.PER_INGOT));
                continue;
            }
            list.add(new Ghost(need.icon().get(), need.count(), menu.onStation(need) >= need.count()));
        }
        return list;
    }

    private List<Ghost> tools(SmithProducts.Product product) {
        Inventory inventory = minecraft.player.getInventory();
        List<ForgeOp.Tool> needed = new ArrayList<>();
        boolean hot = false;
        for (ForgeOp op : product.operations()) {
            hot |= op.hot();
            if (op.tool() != ForgeOp.Tool.NONE && !needed.contains(op.tool())) {
                needed.add(op.tool());
            }
        }
        if (hot && !needed.contains(ForgeOp.Tool.TONGS)) {
            needed.add(ForgeOp.Tool.TONGS);
        }
        List<Ghost> list = new ArrayList<>();
        for (ForgeOp.Tool tool : needed) {
            boolean have = false;
            for (int i = 0; i < inventory.getContainerSize() && !have; i++) {
                have = tool.test(inventory.getItem(i));
            }
            list.add(new Ghost(tool.icon(), 1, have));
        }
        return list;
    }

    private static Component chain(SmithProducts.Product product) {
        MutableComponent text = Component.empty();
        List<SmithProducts.Step> steps = product.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                text.append(" → ");
            }
            SmithProducts.Step step = steps.get(i);
            text.append(Component.translatable(step.op().langKey()));
            if (step.amount() > 1) {
                text.append(" ×" + step.amount());
            }
        }
        return text;
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, leftPos, topPos - font.lineHeight - TITLE_GAP, 0xFFFFFF, true);
        int hoveredTab = tabAt(mouseX, mouseY);
        if (hoveredTab >= 0) {
            g.renderTooltip(font, Component.translatable(tabs.get(hoveredTab).langKey()), mouseX, mouseY);
            return;
        }
        ItemStack hovered = hoveredGhost(mouseX, mouseY);
        if (!hovered.isEmpty() && menu.getCarried().isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    private ItemStack hoveredGhost(int mouseX, int mouseY) {
        int index = productAt(mouseX, mouseY);
        if (index >= 0) {
            return products.get(index).result();
        }
        int selected = menu.product();
        if (selected < 0) {
            return ItemStack.EMPTY;
        }
        SmithProducts.Product product = products.get(selected);
        if (over(mouseX, mouseY, CarpenterBenchMenu.RESULT_X, CarpenterBenchMenu.RESULT_Y)) {
            return product.result();
        }
        List<Ghost> needs = needs(product);
        for (int i = 0; i < needs.size(); i++) {
            if (over(mouseX, mouseY, needX(i), needY(i))) {
                return needs.get(i).stack();
            }
        }
        List<Ghost> tools = tools(product);
        for (int i = 0; i < tools.size(); i++) {
            if (over(mouseX, mouseY, CarpenterBenchMenu.TOOLS_X, CarpenterBenchMenu.GRID_Y + i * CarpenterBenchMenu.STEP)) {
                return tools.get(i).stack();
            }
        }
        return ItemStack.EMPTY;
    }

    // Нужное: до семи в ряд под ячейками, дальше — второй ряд справа от кнопки нет места, поэтому сжато
    private static int needX(int i) {
        return CarpenterBenchMenu.GRID_X + (i % 7) * (CarpenterBenchMenu.STEP - 1);
    }

    private static int needY(int i) {
        return CarpenterBenchMenu.NEEDS_Y;
    }

    private boolean over(int mouseX, int mouseY, int itemX, int itemY) {
        int x = leftPos + itemX;
        int y = topPos + itemY;
        return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    private void plaque(GuiGraphics g, Component text, int x0, int y, int x1) {
        g.blitSprite(PLAQUE, leftPos + x0, topPos + y, x1 - x0, 11);
        TextFit.drawCentered(g, font, text, leftPos + (x0 + x1) / 2, topPos + y + 2, x1 - x0 - 6, 0xFF3A2414);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Вкладки слева снаружи
        for (int i = 0; i < tabs.size(); i++) {
            int tx = x - TAB - 2;
            int ty = y + 4 + i * (TAB + 2);
            ConquestUi.inset(g, tx, ty, tx + TAB + 3, ty + TAB);
            if (tabs.get(i) == tab) {
                g.renderOutline(tx, ty, TAB + 3, TAB, SELECTED);
            }
            g.renderItem(tabIcon(tabs.get(i)), tx + 2, ty + 2);
        }
        ConquestUi.window(g, x, y, x + imageWidth, y + imageHeight);
        ConquestUi.inset(g, x + 4, y + 4, x + 105, y + 112);
        ConquestUi.inset(g, x + 106, y + 4, x + 252, y + 126);
        plaque(g, Component.translatable(tab.langKey()), 8, 6, 100);
        plaque(g, Component.translatable(menu.casting() ? "gui.halgventmod.workshop.on_table"
                : "gui.halgventmod.workshop.on_anvil"), 110, 6, 248);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConquestUi.slot(g, x + slot.x, y + slot.y, slot instanceof StorageSlot storage && !storage.isOpen());
            }
        }
        int hovered = productAt(mouseX, mouseY);
        int selected = menu.product();
        for (int row = 0; row < CarpenterBenchMenu.PRODUCT_ROWS; row++) {
            for (int col = 0; col < CarpenterBenchMenu.PRODUCT_COLUMNS; col++) {
                int at = (row + scroll) * CarpenterBenchMenu.PRODUCT_COLUMNS + col;
                int ix = x + CarpenterBenchMenu.PRODUCTS_X + col * CarpenterBenchMenu.STEP + 1;
                int iy = y + CarpenterBenchMenu.PRODUCTS_Y + row * CarpenterBenchMenu.STEP + 1;
                ConquestUi.slot(g, ix, iy, at >= visible.size());
                if (at >= visible.size()) {
                    continue;
                }
                int index = visible.get(at);
                if (index == selected) {
                    g.renderOutline(ix - 1, iy - 1, 18, 18, SELECTED);
                } else if (index == hovered) {
                    g.fill(ix, iy, ix + 16, iy + 16, 0x30FFFFFF);
                }
                SmithProducts.Product product = products.get(index);
                ItemStack result = product.result();
                g.renderItem(result, ix, iy);
                g.renderItemDecorations(font, result, ix, iy);
                if (!product.available()) {
                    g.fill(ix, iy, ix + 16, iy + 16, 0x90202020);
                }
            }
        }
        if (maxScroll() > 0) {
            int track = CarpenterBenchMenu.PRODUCT_ROWS * CarpenterBenchMenu.STEP;
            int knob = Math.max(8, track * CarpenterBenchMenu.PRODUCT_ROWS / (CarpenterBenchMenu.PRODUCT_ROWS + maxScroll()));
            int ky = y + CarpenterBenchMenu.PRODUCTS_Y + (track - knob) * scroll / maxScroll();
            int kx = x + CarpenterBenchMenu.PRODUCTS_X + CarpenterBenchMenu.PRODUCT_COLUMNS * CarpenterBenchMenu.STEP + 2;
            g.fill(kx, ky, kx + 3, ky + knob, TEXT_DIM);
        }
        g.blitSprite(ARROW, x + CarpenterBenchMenu.GRID_X + 3 * CarpenterBenchMenu.STEP + 3, y + CarpenterBenchMenu.RESULT_Y, 24, 16);
        ConquestUi.slot(g, x + CarpenterBenchMenu.RESULT_X, y + CarpenterBenchMenu.RESULT_Y, false);
        if (selected < 0) {
            return;
        }
        SmithProducts.Product product = products.get(selected);
        ItemStack result = product.result();
        g.renderItem(result, x + CarpenterBenchMenu.RESULT_X, y + CarpenterBenchMenu.RESULT_Y);
        g.renderItemDecorations(font, result, x + CarpenterBenchMenu.RESULT_X, y + CarpenterBenchMenu.RESULT_Y);
        List<Ghost> needs = needs(product);
        for (int i = 0; i < needs.size(); i++) {
            ghost(g, needs.get(i), x + needX(i), y + needY(i));
        }
        List<Ghost> tools = tools(product);
        for (int i = 0; i < tools.size(); i++) {
            ghost(g, tools.get(i), x + CarpenterBenchMenu.TOOLS_X, y + CarpenterBenchMenu.GRID_Y + i * CarpenterBenchMenu.STEP);
        }
        TextFit.draw(g, font, chain(product), x + CarpenterBenchMenu.GRID_X - 1, y + CarpenterBenchMenu.NEEDS_Y - 11,
                imageWidth - CarpenterBenchMenu.GRID_X - 6, TEXT_DIM);
    }

    private void ghost(GuiGraphics g, Ghost ghost, int ix, int iy) {
        ConquestUi.slot(g, ix, iy, false);
        if (!ghost.have()) {
            g.fill(ix, iy, ix + 16, iy + 16, MISSING_BG);
        }
        g.renderFakeItem(ghost.stack(), ix, iy);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(ix, iy, ix + 16, iy + 16, GHOST_VEIL);
        if (ghost.count() > 1) {
            String text = String.valueOf(ghost.count());
            g.drawString(font, text, ix + 17 - font.width(text), iy + 9, ghost.have() ? 0xFFFFFFFF : 0xFFFF6A5A, true);
        }
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- мышь

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tabIndex = tabAt(mouseX, mouseY);
        if (tabIndex >= 0 && button == 0) {
            tab = tabs.get(tabIndex);
            scroll = 0;
            refresh();
            return true;
        }
        int index = productAt(mouseX, mouseY);
        if (index >= 0 && button == 0) {
            click(index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double rx = mouseX - leftPos - CarpenterBenchMenu.PRODUCTS_X;
        if (rx >= 0 && rx < CarpenterBenchMenu.PRODUCT_COLUMNS * CarpenterBenchMenu.STEP + 6 && maxScroll() > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
