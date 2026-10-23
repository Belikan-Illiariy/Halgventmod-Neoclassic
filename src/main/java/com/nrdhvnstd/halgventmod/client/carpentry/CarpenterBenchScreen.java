package com.nrdhvnstd.halgventmod.client.carpentry;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;
import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.menu.CarpenterBenchMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.carpentry.Operation;

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

/**
 * Окно верстака плотника ({@link CarpenterBenchMenu}): слева изделия (колесо — листать), посередине ячейки
 * верстака, справа — что выйдет и рубанок, под ячейками — что нужно этому изделию (красное — не хватает).
 * «Делать» — этапы работы (BenchWorkScreen), окно на это время закрывается.
 */
public class CarpenterBenchScreen extends AbstractContainerScreen<CarpenterBenchMenu> {
    private static final ResourceLocation PLAQUE = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/title");
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int TITLE_GAP = 2;
    private static final int TEXT_DIM = 0xFFAA9C80;
    private static final int MISSING_BG = 0x70C03028;
    private static final int GHOST_VEIL = 0x608B8B8B;
    private static final int SELECTED = 0xFFFFD27F;

    /** Вещь-призрак: что, сколько нужно, есть ли. */
    private record Ghost(ItemStack stack, int count, boolean have) {}

    private final List<BenchProducts.Product> products = BenchProducts.all();
    private int scroll;
    private Button workButton;

    public CarpenterBenchScreen(CarpenterBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CarpenterBenchMenu.WIDTH;
        this.imageHeight = CarpenterBenchMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, font.lineHeight + TITLE_GAP);
        workButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.carpenter_bench.work"),
                button -> click(CarpenterBenchMenu.BUTTON_WORK))
                .bounds(leftPos + CarpenterBenchMenu.GRID_X - 1, topPos + CarpenterBenchMenu.BUTTONS_Y, 128, 16).build());
        int selected = menu.product();
        if (selected >= 0) {
            scroll = Mth.clamp(selected / CarpenterBenchMenu.PRODUCT_COLUMNS - CarpenterBenchMenu.PRODUCT_ROWS + 1, 0, maxScroll());
        }
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private int maxScroll() {
        return Math.max(0, Mth.positiveCeilDiv(products.size(), CarpenterBenchMenu.PRODUCT_COLUMNS) - CarpenterBenchMenu.PRODUCT_ROWS);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        workButton.active = menu.product() >= 0;
    }

    /** Изделие под мышью или −1. */
    private int productAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - CarpenterBenchMenu.PRODUCTS_X;
        double ry = mouseY - topPos - CarpenterBenchMenu.PRODUCTS_Y;
        if (rx < 0 || ry < 0 || rx >= CarpenterBenchMenu.PRODUCT_COLUMNS * CarpenterBenchMenu.STEP
                || ry >= CarpenterBenchMenu.PRODUCT_ROWS * CarpenterBenchMenu.STEP) {
            return -1;
        }
        int index = ((int) ry / CarpenterBenchMenu.STEP + scroll) * CarpenterBenchMenu.PRODUCT_COLUMNS
                + (int) rx / CarpenterBenchMenu.STEP;
        return index < products.size() ? index : -1;
    }

    // ---------------------------------------------------------------- что нужно

    private List<Ghost> needs(BenchProducts.Product product) {
        List<Ghost> list = new ArrayList<>();
        for (BenchProducts.Need need : product.needs()) {
            list.add(new Ghost(need.icon().get(), need.count(), menu.onBench(need) >= need.count()));
        }
        return list;
    }

    /** Инструменты этапов: есть ли каждый в инвентаре. */
    private List<Ghost> tools(BenchProducts.Product product) {
        Inventory inventory = minecraft.player.getInventory();
        List<Ghost> list = new ArrayList<>();
        for (Operation operation : product.operations()) {
            boolean have = false;
            for (int i = 0; i < inventory.getContainerSize() && !have; i++) {
                have = operation.tool().test(inventory.getItem(i));
            }
            list.add(new Ghost(operation.icon(), 1, have));
        }
        return list;
    }

    /** «Строгание → сверление ×4 → сборка ×4». */
    private static Component chain(BenchProducts.Product product) {
        MutableComponent text = Component.empty();
        List<BenchProducts.Step> steps = product.steps();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                text.append(" → ");
            }
            BenchProducts.Step step = steps.get(i);
            text.append(Component.translatable(step.operation().langKey()));
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
        ItemStack hovered = hoveredGhost(mouseX, mouseY);
        if (!hovered.isEmpty() && menu.getCarried().isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    /** Вещь под мышью вне настоящих ячеек: изделие, итог, призраки. */
    private ItemStack hoveredGhost(int mouseX, int mouseY) {
        int index = productAt(mouseX, mouseY);
        if (index >= 0) {
            return products.get(index).result();
        }
        int selected = menu.product();
        if (selected < 0) {
            return ItemStack.EMPTY;
        }
        BenchProducts.Product product = products.get(selected);
        if (over(mouseX, mouseY, CarpenterBenchMenu.RESULT_X, CarpenterBenchMenu.RESULT_Y)) {
            return product.result();
        }
        List<Ghost> needs = needs(product);
        for (int i = 0; i < needs.size(); i++) {
            if (over(mouseX, mouseY, CarpenterBenchMenu.GRID_X + i * CarpenterBenchMenu.STEP, CarpenterBenchMenu.NEEDS_Y)) {
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

    private boolean over(int mouseX, int mouseY, int itemX, int itemY) {
        int x = leftPos + itemX;
        int y = topPos + itemY;
        return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Заголовки — пергаментные плашки в renderBg
    }

    /** Пергаментная плашка-заголовок с тёмной надписью посередине. */
    private void plaque(GuiGraphics g, Component text, int x0, int y, int x1) {
        g.blitSprite(PLAQUE, leftPos + x0, topPos + y, x1 - x0, 11);
        g.drawString(font, text, leftPos + (x0 + x1) / 2 - font.width(text) / 2, topPos + y + 2, 0xFF3A2414, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        ConquestUi.window(g, x, y, x + imageWidth, y + imageHeight);
        ConquestUi.inset(g, x + 4, y + 4, x + 105, y + 112);
        ConquestUi.inset(g, x + 106, y + 4, x + 252, y + 126);
        plaque(g, Component.translatable("gui.halgventmod.carpenter_bench.products"), 8, 6, 100);
        plaque(g, Component.translatable("gui.halgventmod.carpenter_bench.on_bench"), 110, 6, 248);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConquestUi.slot(g, x + slot.x, y + slot.y, slot instanceof StorageSlot storage && !storage.isOpen());
            }
        }

        // Изделия
        int hovered = productAt(mouseX, mouseY);
        int selected = menu.product();
        for (int row = 0; row < CarpenterBenchMenu.PRODUCT_ROWS; row++) {
            for (int col = 0; col < CarpenterBenchMenu.PRODUCT_COLUMNS; col++) {
                int index = (row + scroll) * CarpenterBenchMenu.PRODUCT_COLUMNS + col;
                int ix = x + CarpenterBenchMenu.PRODUCTS_X + col * CarpenterBenchMenu.STEP + 1;
                int iy = y + CarpenterBenchMenu.PRODUCTS_Y + row * CarpenterBenchMenu.STEP + 1;
                ConquestUi.slot(g, ix, iy, index >= products.size());
                if (index >= products.size()) {
                    continue;
                }
                if (index == selected) {
                    g.renderOutline(ix - 1, iy - 1, 18, 18, SELECTED);
                } else if (index == hovered) {
                    g.fill(ix, iy, ix + 16, iy + 16, 0x30FFFFFF);
                }
                ItemStack result = products.get(index).result();
                g.renderItem(result, ix, iy);
                g.renderItemDecorations(font, result, ix, iy);
            }
        }
        if (maxScroll() > 0) {
            int track = CarpenterBenchMenu.PRODUCT_ROWS * CarpenterBenchMenu.STEP;
            int knob = Math.max(8, track * CarpenterBenchMenu.PRODUCT_ROWS / (CarpenterBenchMenu.PRODUCT_ROWS + maxScroll()));
            int ky = y + CarpenterBenchMenu.PRODUCTS_Y + (track - knob) * scroll / maxScroll();
            int kx = x + CarpenterBenchMenu.PRODUCTS_X + CarpenterBenchMenu.PRODUCT_COLUMNS * CarpenterBenchMenu.STEP + 2;
            g.fill(kx, ky, kx + 3, ky + knob, TEXT_DIM);
        }

        // Стрелка, итог, нужное и рубанок
        g.blitSprite(ARROW, x + CarpenterBenchMenu.GRID_X + 3 * CarpenterBenchMenu.STEP + 3, y + CarpenterBenchMenu.RESULT_Y, 24, 16);
        ConquestUi.slot(g, x + CarpenterBenchMenu.RESULT_X, y + CarpenterBenchMenu.RESULT_Y, false);
        if (selected < 0) {
            return;
        }
        BenchProducts.Product product = products.get(selected);
        ItemStack result = product.result();
        g.renderItem(result, x + CarpenterBenchMenu.RESULT_X, y + CarpenterBenchMenu.RESULT_Y);
        g.renderItemDecorations(font, result, x + CarpenterBenchMenu.RESULT_X, y + CarpenterBenchMenu.RESULT_Y);
        List<Ghost> needs = needs(product);
        for (int i = 0; i < needs.size(); i++) {
            ghost(g, needs.get(i), x + CarpenterBenchMenu.GRID_X + i * CarpenterBenchMenu.STEP, y + CarpenterBenchMenu.NEEDS_Y);
        }
        List<Ghost> tools = tools(product);
        for (int i = 0; i < tools.size(); i++) {
            ghost(g, tools.get(i), x + CarpenterBenchMenu.TOOLS_X, y + CarpenterBenchMenu.GRID_Y + i * CarpenterBenchMenu.STEP);
        }
        TextFit.draw(g, font, chain(product), x + CarpenterBenchMenu.GRID_X - 1, y + CarpenterBenchMenu.NEEDS_Y - 11,
                imageWidth - CarpenterBenchMenu.GRID_X - 6, TEXT_DIM);
    }

    /** Слот-призрак, как в книге рецептов: блеклая вещь, сколько нужно, красный фон — не хватает. */
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
