package com.nrdhvnstd.halgventmod.client.cooking;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.KitchenRecipes;
import com.nrdhvnstd.halgventmod.network.KitchenChoosePayload;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Рецепты разделочного стола, как книга рецептов (по образцу окна выкроек кожевника):
 * <pre>
 *  [выкройки 2 столбца]   [ингредиенты 3×3] → [результат]   [инструменты 3 столбца]
 * </pre>
 * Щелчок по выкройке — её ингредиенты и инструменты появляются в слотах «призраками»; чего нет в инвентаре —
 * на красном фоне. «Выбрать» или двойной щелчок — отправить на сервер ({@link KitchenChoosePayload}).
 */
public class KitchenScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");
    private static final ResourceLocation CELL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/cell");
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    private static final int PAD = 10;
    private static final int RECIPE_COLUMNS = 2;
    private static final int RECIPE = 22;          // клетка выкройки
    private static final int SLOT = 18;            // слот ингредиента и инструмента
    private static final int GRID = 3;             // ингредиенты 3×3
    private static final int TOOL_COLUMNS = 1;
    private static final int TOP = 32;
    private static final int RECIPES_X = PAD;
    private static final int GRID_X = RECIPES_X + RECIPE_COLUMNS * RECIPE + 14;
    private static final int ARROW_X = GRID_X + GRID * SLOT + 6;
    private static final int RESULT_X = ARROW_X + 24 + 6;
    private static final int RESULT = 26;
    private static final int TOOLS_X = RESULT_X + RESULT + 16;
    private static final int TOOLS_AREA = 64;     // окно шире столбика: подпись над ним в одну строку
    private static final int TOOLS_SLOT_X = TOOLS_X;
    private static final int WIDTH = TOOLS_X + TOOLS_AREA + PAD;

    private static final int TEXT = 0xFFE9DCB4;
    private static final int TEXT_DIM = 0xFFAA9C80;
    private static final int ENOUGH = 0xFF8FD16A;
    private static final int MISSING_BG = 0x70C03028;
    private static final int GHOST_VEIL = 0x608B8B8B;
    private static final long DOUBLE_CLICK_MS = 300;

    /** Что лежит в слоте-призраке: вещь, сколько нужно, есть ли. */
    private record Ghost(ItemStack stack, int count, boolean have, boolean optional) {
        Ghost(ItemStack stack, int count, boolean have) {
            this(stack, count, have, false);
        }
    }

    private final BlockPos pos;
    private final int current;
    private final List<KitchenRecipes.Recipe> patterns = KitchenRecipes.all();
    private int selected;
    private int lastClicked = -1;
    private long lastClickAt;
    private int panelHeight;

    public KitchenScreen(BlockPos pos, int current) {
        super(Component.translatable("gui.halgventmod.kitchen.title"));
        this.pos = pos;
        this.current = current;
        this.selected = Math.max(0, current);
    }

    public static void open(BlockPos pos, int current) {
        Minecraft.getInstance().setScreen(new KitchenScreen(pos, current));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rows() {
        return Mth.positiveCeilDiv(patterns.size(), RECIPE_COLUMNS);
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    @Override
    protected void init() {
        // Справа под слотами — название, стежки и кнопка; высота — не меньше столбца выкроек
        panelHeight = TOP + Math.max(rows() * RECIPE, GRID * SLOT + 60) + PAD;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.pattern.choose"), button -> choose())
                .bounds(left() + GRID_X, top() + panelHeight - PAD - 20, WIDTH - PAD - GRID_X, 20)
                .build());
    }

    private void choose() {
        PacketDistributor.sendToServer(new KitchenChoosePayload(pos, selected));
        onClose();
    }

    /** Индекс выкройки под мышью или −1. */
    private int recipeAt(double mouseX, double mouseY) {
        double rx = mouseX - left() - RECIPES_X;
        double ry = mouseY - top() - TOP;
        if (rx < 0 || ry < 0 || rx >= RECIPE_COLUMNS * RECIPE || ry >= rows() * RECIPE) {
            return -1;
        }
        int index = (int) ry / RECIPE * RECIPE_COLUMNS + (int) rx / RECIPE;
        return index < patterns.size() ? index : -1;
    }

    // ---------------------------------------------------------------- что нужно

    @Nullable
    private Inventory inventory() {
        return minecraft == null || minecraft.player == null ? null : minecraft.player.getInventory();
    }

    private static int count(@Nullable Inventory inventory, KitchenRecipes.Need need) {
        int n = 0;
        if (inventory != null) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (need.matches().test(stack)) {
                    n += stack.getCount();
                }
            }
        }
        return n;
    }

    private static List<Ghost> ingredients(KitchenRecipes.Recipe recipe, @Nullable Inventory inventory) {
        List<Ghost> list = new ArrayList<>();
        for (KitchenRecipes.Need need : recipe.needs()) {
            list.add(new Ghost(new ItemStack(need.icon().get()), need.count(), count(inventory, need) >= need.count()));
        }
        for (KitchenRecipes.Extra extra : recipe.extras()) {
            KitchenRecipes.Need asNeed = new KitchenRecipes.Need(extra.matches(), 1, extra.icon());
            list.add(new Ghost(new ItemStack(extra.icon().get()), 1, count(inventory, asNeed) > 0, true));
        }
        return list;
    }

    /** Добавки: ведро воды (у теста), соль — по желанию, +качество. */
    private static List<Ghost> tools(KitchenRecipes.Recipe recipe, @Nullable Inventory inventory) {
        List<Ghost> list = new ArrayList<>();
        if (recipe.water()) {
            list.add(new Ghost(new ItemStack(Items.WATER_BUCKET), 1,
                    inventory != null && inventory.countItem(Items.WATER_BUCKET) > 0));
        }
        if (recipe.salted()) {
            list.add(new Ghost(new ItemStack(ModItems.SALT.get()), 1,
                    inventory != null && inventory.countItem(ModItems.SALT.get()) > 0));
        }
        return list;
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blitSprite(PANEL, left(), top(), WIDTH, panelHeight);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        g.drawCenteredString(font, title, x + WIDTH / 2, y + 8, TEXT);
        g.drawString(font, Component.translatable("gui.halgventmod.kitchen.ingredients"), x + GRID_X, y + TOP - 11, TEXT_DIM, false);
        g.drawString(font, Component.translatable("gui.halgventmod.kitchen.extras"), x + TOOLS_X, y + TOP - 11, TEXT_DIM, false);

        ItemStack hoveredStack = ItemStack.EMPTY;

        // Выкройки: 2 столбца
        int hovered = recipeAt(mouseX, mouseY);
        for (int i = 0; i < patterns.size(); i++) {
            int cx = x + RECIPES_X + (i % RECIPE_COLUMNS) * RECIPE;
            int cy = y + TOP + (i / RECIPE_COLUMNS) * RECIPE;
            g.blitSprite(CELL, cx, cy, RECIPE, RECIPE);
            if (i == selected) {
                g.renderOutline(cx, cy, RECIPE, RECIPE, 0xFFFFD27F);
            } else if (i == hovered) {
                g.fill(cx + 2, cy + 2, cx + RECIPE - 2, cy + RECIPE - 2, 0x30FFFFFF);
            }
            ItemStack result = patterns.get(i).makeResult();
            g.renderItem(result, cx + 3, cy + 3);
            g.renderItemDecorations(font, result, cx + 3, cy + 3);
            if (i == current) {
                g.fill(cx + RECIPE - 6, cy + 2, cx + RECIPE - 2, cy + 6, ENOUGH);
            }
            if (i == hovered) {
                hoveredStack = result;
            }
        }

        KitchenRecipes.Recipe pattern = patterns.get(selected);
        Inventory inventory = inventory();

        // Ингредиенты 3×3
        List<Ghost> ingredients = ingredients(pattern, inventory);
        for (int i = 0; i < GRID * GRID; i++) {
            int sx = x + GRID_X + (i % GRID) * SLOT;
            int sy = y + TOP + (i / GRID) * SLOT;
            Ghost ghost = i < ingredients.size() ? ingredients.get(i) : null;
            if (slot(g, ghost, sx, sy, mouseX, mouseY)) {
                hoveredStack = ghost.stack();
            }
        }

        // Стрелка и результат
        g.blitSprite(ARROW, x + ARROW_X, y + TOP + SLOT - 8, 24, 16);
        int rx = x + RESULT_X;
        int ry = y + TOP + (GRID * SLOT - RESULT) / 2;
        g.blitSprite(CELL, rx, ry, RESULT, RESULT);
        ItemStack result = pattern.makeResult();
        g.renderItem(result, rx + 5, ry + 5);
        g.renderItemDecorations(font, result, rx + 5, ry + 5);
        if (mouseX >= rx && mouseX < rx + RESULT && mouseY >= ry && mouseY < ry + RESULT) {
            hoveredStack = result;
        }

        // Добавки — один столбик: вода, соль
        List<Ghost> tools = tools(pattern, inventory);
        for (int i = 0; i < TOOL_COLUMNS * GRID; i++) {
            int sx = x + TOOLS_SLOT_X + (i / GRID) * SLOT;
            int sy = y + TOP + (i % GRID) * SLOT;
            Ghost ghost = i < tools.size() ? tools.get(i) : null;
            if (slot(g, ghost, sx, sy, mouseX, mouseY)) {
                hoveredStack = ghost.stack();
            }
        }

        // Название и шитьё — под слотами
        int infoY = y + TOP + GRID * SLOT + 8;
        Component name = result.getCount() > 1
                ? Component.empty().append(result.getHoverName()).append(" ×" + result.getCount())
                : result.getHoverName();
        g.drawString(font, name, x + GRID_X, infoY, TEXT, false);
        Component sewing = Component.translatable("gui.halgventmod.kitchen.kneads", pattern.kneads());
        g.drawString(font, sewing, x + GRID_X, infoY + 11, TEXT_DIM, false);
        if (current == selected) {
            g.drawString(font, Component.translatable("gui.halgventmod.pattern.current"), x + GRID_X, infoY + 22, ENOUGH, false);
        }

        if (!hoveredStack.isEmpty()) {
            g.renderTooltip(font, hoveredStack, mouseX, mouseY);
        }
    }

    /**
     * Слот с вещью-призраком, как в книге рецептов: блеклая вещь, число — сколько нужно, красный фон — нет в инвентаре.
     * @return мышь над занятым слотом
     */
    private boolean slot(GuiGraphics g, @Nullable Ghost ghost, int sx, int sy, int mouseX, int mouseY) {
        g.blitSprite(CELL, sx, sy, SLOT, SLOT);
        if (ghost == null) {
            return false;
        }
        int ix = sx + 1;
        int iy = sy + 1;
        if (!ghost.have() && !ghost.optional()) {
            g.fill(ix, iy, ix + 16, iy + 16, MISSING_BG);
        }
        g.renderFakeItem(ghost.stack(), ix, iy);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(ix, iy, ix + 16, iy + 16, GHOST_VEIL);
        if (ghost.optional()) {
            g.drawString(font, "+", ix, iy - 1, 0xFFFFD27F, true); // необязательная добавка
        }
        if (ghost.count() > 1) {
            String text = String.valueOf(ghost.count());
            g.drawString(font, text, ix + 17 - font.width(text), iy + 9, ghost.have() ? 0xFFFFFFFF : 0xFFFF6A5A, true);
        }
        g.pose().popPose();
        return mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = recipeAt(mouseX, mouseY);
        if (index >= 0 && button == 0) {
            long now = System.currentTimeMillis();
            boolean twice = index == lastClicked && now - lastClickAt < DOUBLE_CLICK_MS;
            selected = index;
            lastClicked = index;
            lastClickAt = now;
            if (twice) {
                choose();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
