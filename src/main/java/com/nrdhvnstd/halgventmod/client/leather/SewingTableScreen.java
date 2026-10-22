package com.nrdhvnstd.halgventmod.client.leather;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.client.TextFit;
import com.nrdhvnstd.halgventmod.leather.LeatherPatterns;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.menu.SewingTableMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Окно шитейного стола ({@link SewingTableMenu}): слева выкройки (колесо — листать), посередине ячейки стола,
 * справа — что выйдет и инструменты, под ячейками — что нужно этой выкройке (красное — не хватает).
 * «Раскроить» и «Шить» — мини-игры (CutScreen, SewingScreen), окно на это время закрывается.
 */
public class SewingTableScreen extends AbstractContainerScreen<SewingTableMenu> {
    private static final ResourceLocation PLAQUE = ResourceLocation.fromNamespaceAndPath(
            com.nrdhvnstd.halgventmod.HalgventMod.MODID, "character/title");
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int TITLE_GAP = 2;
    private static final int TEXT_DIM = 0xFFAA9C80;
    private static final int MISSING_BG = 0x70C03028;
    private static final int GHOST_VEIL = 0x608B8B8B;
    private static final int SELECTED = 0xFFFFD27F;

    /** Вещь-призрак: что, сколько нужно, есть ли. */
    private record Ghost(ItemStack stack, int count, boolean have) {}

    private final List<LeatherPatterns.Pattern> patterns = LeatherPatterns.all();
    private int scroll;
    private Button cutButton;
    private Button sewButton;

    public SewingTableScreen(SewingTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = SewingTableMenu.WIDTH;
        this.imageHeight = SewingTableMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, font.lineHeight + TITLE_GAP);
        int bx = leftPos + SewingTableMenu.GRID_X - 1;
        int by = topPos + SewingTableMenu.BUTTONS_Y;
        cutButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.sewing_table.cut"),
                button -> click(SewingTableMenu.BUTTON_CUT)).bounds(bx, by, 62, 16).build());
        sewButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.sewing_table.sew"),
                button -> click(SewingTableMenu.BUTTON_SEW)).bounds(bx + 66, by, 62, 16).build());
        int selected = menu.pattern();
        if (selected >= 0) {
            scroll = Mth.clamp(selected / SewingTableMenu.PATTERN_COLUMNS - SewingTableMenu.PATTERN_ROWS + 1, 0, maxScroll());
        }
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private int maxScroll() {
        return Math.max(0, Mth.positiveCeilDiv(patterns.size(), SewingTableMenu.PATTERN_COLUMNS) - SewingTableMenu.PATTERN_ROWS);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int selected = menu.pattern();
        cutButton.active = selected >= 0 && !menu.isCut();
        sewButton.active = selected >= 0 && menu.isCut() && patterns.get(selected).stitches() > 0;
    }

    /** Выкройка под мышью или −1. */
    private int patternAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - SewingTableMenu.PATTERNS_X;
        double ry = mouseY - topPos - SewingTableMenu.PATTERNS_Y;
        if (rx < 0 || ry < 0 || rx >= SewingTableMenu.PATTERN_COLUMNS * SewingTableMenu.STEP
                || ry >= SewingTableMenu.PATTERN_ROWS * SewingTableMenu.STEP) {
            return -1;
        }
        int index = ((int) ry / SewingTableMenu.STEP + scroll) * SewingTableMenu.PATTERN_COLUMNS + (int) rx / SewingTableMenu.STEP;
        return index < patterns.size() ? index : -1;
    }

    // ---------------------------------------------------------------- что нужно

    private List<Ghost> needs(LeatherPatterns.Pattern pattern) {
        List<Ghost> list = new ArrayList<>();
        if (!menu.isCut()) {
            for (LeatherPatterns.Need need : pattern.leather()) {
                list.add(new Ghost(new ItemStack(need.item().get()), need.count(), menu.onTable(need) >= need.count()));
            }
        }
        for (LeatherPatterns.Need need : pattern.extras()) {
            list.add(new Ghost(new ItemStack(need.item().get()), need.count(), menu.onTable(need) >= need.count()));
        }
        return list;
    }

    private List<Ghost> tools(LeatherPatterns.Pattern pattern) {
        Inventory inventory = minecraft.player.getInventory();
        boolean cloth = pattern.material() == LeatherPatterns.Material.CLOTH;
        boolean cutter = false;
        for (int i = 0; i < inventory.getContainerSize() && !cutter; i++) {
            ItemStack stack = inventory.getItem(i);
            cutter = cloth ? stack.is(Items.SHEARS) : stack.is(LeatherTableBlockEntity.KNIVES);
        }
        List<Ghost> list = new ArrayList<>();
        list.add(new Ghost(new ItemStack(cloth ? Items.SHEARS : ModItems.KNIFE.get()), 1, cutter || menu.isCut()));
        if (pattern.stitches() > 0) {
            ItemStack needle = new ItemStack(ModItems.NEEDLE.get());
            list.add(new Ghost(needle, 1, inventory.countItem(needle.getItem()) > 0));
            int thread = LeatherTableBlockEntity.threadIn(inventory);
            list.add(new Ghost(new ItemStack(ModItems.THREADS.get()), pattern.thread(), thread >= pattern.thread()));
        }
        return list;
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

    /** Вещь под мышью вне настоящих ячеек: выкройка, итог, призраки. */
    private ItemStack hoveredGhost(int mouseX, int mouseY) {
        int index = patternAt(mouseX, mouseY);
        if (index >= 0) {
            return patterns.get(index).result();
        }
        int selected = menu.pattern();
        if (selected < 0) {
            return ItemStack.EMPTY;
        }
        LeatherPatterns.Pattern pattern = patterns.get(selected);
        if (over(mouseX, mouseY, SewingTableMenu.RESULT_X, SewingTableMenu.RESULT_Y)) {
            return pattern.result();
        }
        List<Ghost> needs = needs(pattern);
        for (int i = 0; i < needs.size(); i++) {
            if (over(mouseX, mouseY, SewingTableMenu.GRID_X + i * SewingTableMenu.STEP, SewingTableMenu.NEEDS_Y)) {
                return needs.get(i).stack();
            }
        }
        List<Ghost> tools = tools(pattern);
        for (int i = 0; i < tools.size(); i++) {
            if (over(mouseX, mouseY, SewingTableMenu.TOOLS_X, SewingTableMenu.GRID_Y + i * SewingTableMenu.STEP)) {
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
        plaque(g, Component.translatable("gui.halgventmod.pattern.title"), 8, 6, 100);
        plaque(g, Component.translatable("gui.halgventmod.sewing_table.on_table"), 110, 6, 248);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConquestUi.slot(g, x + slot.x, y + slot.y, slot instanceof StorageSlot storage && !storage.isOpen());
            }
        }

        // Выкройки
        int hovered = patternAt(mouseX, mouseY);
        int selected = menu.pattern();
        for (int row = 0; row < SewingTableMenu.PATTERN_ROWS; row++) {
            for (int col = 0; col < SewingTableMenu.PATTERN_COLUMNS; col++) {
                int index = (row + scroll) * SewingTableMenu.PATTERN_COLUMNS + col;
                int ix = x + SewingTableMenu.PATTERNS_X + col * SewingTableMenu.STEP + 1;
                int iy = y + SewingTableMenu.PATTERNS_Y + row * SewingTableMenu.STEP + 1;
                ConquestUi.slot(g, ix, iy, index >= patterns.size());
                if (index >= patterns.size()) {
                    continue;
                }
                if (index == selected) {
                    g.renderOutline(ix - 1, iy - 1, 18, 18, SELECTED);
                } else if (index == hovered) {
                    g.fill(ix, iy, ix + 16, iy + 16, 0x30FFFFFF);
                }
                ItemStack result = patterns.get(index).result();
                g.renderItem(result, ix, iy);
                g.renderItemDecorations(font, result, ix, iy);
            }
        }
        if (maxScroll() > 0) {
            int track = SewingTableMenu.PATTERN_ROWS * SewingTableMenu.STEP;
            int knob = Math.max(8, track * SewingTableMenu.PATTERN_ROWS / (SewingTableMenu.PATTERN_ROWS + maxScroll()));
            int ky = y + SewingTableMenu.PATTERNS_Y + (track - knob) * scroll / maxScroll();
            int kx = x + SewingTableMenu.PATTERNS_X + SewingTableMenu.PATTERN_COLUMNS * SewingTableMenu.STEP + 2;
            g.fill(kx, ky, kx + 3, ky + knob, TEXT_DIM);
        }

        // Стрелка, итог, нужное и инструменты
        g.blitSprite(ARROW, x + SewingTableMenu.GRID_X + 3 * SewingTableMenu.STEP + 3, y + SewingTableMenu.RESULT_Y, 24, 16);
        ConquestUi.slot(g, x + SewingTableMenu.RESULT_X, y + SewingTableMenu.RESULT_Y, false);
        if (selected < 0) {
            return;
        }
        LeatherPatterns.Pattern pattern = patterns.get(selected);
        ItemStack result = pattern.result();
        g.renderItem(result, x + SewingTableMenu.RESULT_X, y + SewingTableMenu.RESULT_Y);
        g.renderItemDecorations(font, result, x + SewingTableMenu.RESULT_X, y + SewingTableMenu.RESULT_Y);
        List<Ghost> needs = needs(pattern);
        for (int i = 0; i < needs.size(); i++) {
            ghost(g, needs.get(i), x + SewingTableMenu.GRID_X + i * SewingTableMenu.STEP, y + SewingTableMenu.NEEDS_Y);
        }
        List<Ghost> tools = tools(pattern);
        for (int i = 0; i < tools.size(); i++) {
            ghost(g, tools.get(i), x + SewingTableMenu.TOOLS_X, y + SewingTableMenu.GRID_Y + i * SewingTableMenu.STEP);
        }
        Component material = Component.translatable(menu.isCut() ? "gui.halgventmod.sewing_table.cut_already"
                : pattern.material() == LeatherPatterns.Material.CLOTH ? "gui.halgventmod.sewing_table.cloth"
                : "gui.halgventmod.sewing_table.leather");
        TextFit.draw(g, font, material, x + SewingTableMenu.GRID_X - 1, y + SewingTableMenu.NEEDS_Y - 11,
                imageWidth - SewingTableMenu.GRID_X - 6, TEXT_DIM);
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
        int index = patternAt(mouseX, mouseY);
        if (index >= 0 && button == 0) {
            click(index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double rx = mouseX - leftPos - SewingTableMenu.PATTERNS_X;
        if (rx >= 0 && rx < SewingTableMenu.PATTERN_COLUMNS * SewingTableMenu.STEP + 6 && maxScroll() > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
