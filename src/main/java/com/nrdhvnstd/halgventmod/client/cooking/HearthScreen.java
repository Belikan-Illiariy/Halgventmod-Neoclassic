package com.nrdhvnstd.halgventmod.client.cooking;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.cooking.Cookery;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.menu.HearthMenu;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Окно очага и костра ({@link HearthMenu}). Слева — огонь: градусник, жар, сколько ещё гореть, полоса горящего
 * полена, ячейки дров, «Разжечь» / «Затушить». Справа — горшок: вода, ячейки продуктов, варка, полоса «мешать»,
 * что черпать; без горшка — куски на углях. С горшком слева от окна — книга рецептов: наведите на блюдо — что класть.
 */
public class HearthScreen extends StationScreen<HearthMenu> {
    private static final int PANEL_WIDTH = 132;
    private static final int PANEL_GAP = 4;
    private static final int ROW = 16;

    /** Блюдо в книге рецептов: значок и ключ описания. */
    private record Recipe(Item icon, String id) {}

    private static List<Recipe> recipes() {
        List<Recipe> list = new ArrayList<>();
        list.add(new Recipe(ModItems.MEAT_STEW.get(), "meat_stew"));
        list.add(new Recipe(ModItems.FISH_SOUP.get(), "fish_soup"));
        list.add(new Recipe(ModItems.WHEAT_PORRIDGE.get(), "porridge"));
        list.add(new Recipe(ModItems.MUSHROOM_SOUP.get(), "mushroom_soup"));
        list.add(new Recipe(ModItems.POTATO_STEW.get(), "potato_stew"));
        list.add(new Recipe(ModItems.VEGETABLE_SOUP.get(), "vegetable_soup"));
        list.add(new Recipe(ModItems.HERBAL_TEA.get(), "herbal_tea"));
        list.add(new Recipe(ModItems.MUST.get(), "wort"));
        list.add(new Recipe(Items.HONEY_BOTTLE, "honey_must"));
        return list;
    }

    private final List<Recipe> recipes = recipes();
    private Button fireButton;
    private Button stirButton;
    private Button potButton;
    private boolean panel;

    public HearthScreen(HearthMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, HearthMenu.WIDTH, HearthMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        panel = menu.hasPot();
        if (panel) {
            leftPos = Math.min(width - imageWidth, leftPos + (PANEL_WIDTH + PANEL_GAP) / 2);
        }
        // Кнопка огня: «Разжечь» или «Затушить» — по тому, горит ли
        fireButton = addRenderableWidget(Button.builder(Component.empty(), button -> click(lit()
                ? HearthMenu.BUTTON_EXTINGUISH : HearthMenu.BUTTON_IGNITE)).bounds(leftPos + 8, topPos + 110, 88, 16).build());
        stirButton = button(Component.translatable("gui.halgventmod.hearth.stir"), 104, 110, 42, HearthMenu.BUTTON_STIR);
        potButton = addRenderableWidget(Button.builder(Component.empty(), button -> click(potEmpty()
                ? HearthMenu.BUTTON_REMOVE_POT : HearthMenu.BUTTON_EMPTY)).bounds(leftPos + 150, topPos + 110, 44, 16).build());
        containerTick();
    }

    private boolean lit() {
        return menu.get(HearthBlockEntity.D_LIT) != 0;
    }

    private boolean potEmpty() {
        return menu.get(HearthBlockEntity.D_WATER) == 0 && menu.slots.subList(HearthMenu.POT_START, HearthMenu.POT_END)
                .stream().noneMatch(slot -> slot.hasItem());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.hasPot() != panel) {
            rebuildWidgets(); // горшок поставили или сняли, пока окно открыто
        }
        fireButton.setMessage(Component.translatable(lit() ? "gui.halgventmod.hearth.extinguish" : "gui.halgventmod.hearth.ignite"));
        stirButton.visible = menu.hasPot();
        potButton.visible = menu.hasPot();
        potButton.setMessage(Component.translatable(potEmpty() ? "gui.halgventmod.hearth.remove_pot" : "gui.halgventmod.hearth.empty"));
        stirButton.active = menu.get(HearthBlockEntity.D_WATER) != 0;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        boolean inPanel = panel && mouseX >= left - PANEL_WIDTH - PANEL_GAP && mouseX < left && mouseY >= top
                && mouseY < top + imageHeight;
        return !inPanel && super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    protected void panels(GuiGraphics g) {
        firePanel(g);
        section(g, 100, 4, 196, 104);
        Component head;
        if (menu.hasPot()) {
            int scorched = menu.get(HearthBlockEntity.D_SCORCHED);
            head = scorched > 0 ? Component.translatable("gui.halgventmod.hearth.pot_scorched", scorched)
                    : Component.translatable("gui.halgventmod.hearth.pot");
        } else {
            head = Component.translatable("gui.halgventmod.hearth.grill");
        }
        plaque(g, head, 104, 6, 192);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        fire(g, x, y);
        if (menu.hasPot()) {
            pot(g, x, y);
            recipeBook(g, mouseX, mouseY);
        } else {
            grill(g, x, y);
        }
    }

    private void fire(GuiGraphics g, int x, int y) {
        float temperature = menu.get(HearthBlockEntity.D_TEMPERATURE);
        int heat = menu.get(HearthBlockEntity.D_HEAT);
        // Запас — в единицах горения, они идут через тик (под дождём — втрое быстрее)
        fireSection(g, temperature, HearthBlockEntity.TARGETS[3] + 50, Component.literal(Math.round(temperature) + "°"),
                Component.translatable("gui.halgventmod.hearth.heat_" + heat), heat == 0 ? TEXT_DIM : WARN, lit(),
                menu.get(HearthBlockEntity.D_REMAINING) * 10 * 2, menu.get(HearthBlockEntity.D_BURN_LEFT),
                menu.get(HearthBlockEntity.D_BURN_TOTAL), HearthBlockEntity.HEAT_1, HearthBlockEntity.HEAT_2,
                HearthBlockEntity.HEAT_3);
    }

    private void pot(GuiGraphics g, int x, int y) {
        int px = x + 104;
        boolean water = menu.get(HearthBlockEntity.D_WATER) != 0;
        fit(g, Component.translatable(water ? "gui.halgventmod.hearth.water" : "gui.halgventmod.hearth.no_water"),
                px, y + 21, 90, water ? TEXT : BAD);

        int portions = menu.get(HearthBlockEntity.D_PORTIONS);
        int progress = menu.get(HearthBlockEntity.D_PROGRESS);
        int due = menu.get(HearthBlockEntity.D_DUE);
        if (portions > 0) {
            int result = menu.get(HearthBlockEntity.D_RESULT);
            if (result >= 0) {
                Cookery.Result dish = Cookery.Result.values()[result];
                ItemStack shown = new ItemStack(dish.item());
                g.renderItem(shown, px, y + 76);
                g.drawString(font, "×" + portions, px + 18, y + 78, TEXT, false);
                Component with = Component.translatable(dish.container() == Items.BOWL
                        ? "gui.halgventmod.hearth.ladle_bowl" : "gui.halgventmod.hearth.ladle_bottle");
                fit(g, with, px + 34, y + 78, 56, GOOD);
            }
        } else {
            int percent = Math.min(100, 100 * progress / HearthBlockEntity.COOK_TIME);
            bar(g, px + 1, y + 78, 86, 4, progress / (float) HearthBlockEntity.COOK_TIME, 0xFF8FB0D0);
            // Сколько ещё варить: при нынешнем жаре (единиц жара за тик)
            int heat = menu.get(HearthBlockEntity.D_HEAT);
            String left = heat > 0 ? time((HearthBlockEntity.COOK_TIME - progress) / heat) : "—";
            small(g, Component.translatable("gui.halgventmod.hearth.cooking", percent, left), px, y + 84,
                    progress > 0 ? TEXT : TEXT_DIM);
        }
        // Мешать: полоса наполняется к «пора»; красная — пригорает
        if (water && (progress > 0 || portions > 0)) {
            boolean urgent = due >= 0;
            float share = urgent ? 1 - due / (float) HearthBlockEntity.STIR_GRACE
                    : menu.get(HearthBlockEntity.D_STIR) / (float) HearthBlockEntity.STIR_EVERY;
            boolean blink = urgent && (System.currentTimeMillis() / 300) % 2 == 0;
            bar(g, px + 1, y + 94, 86, 3, share, urgent ? (blink ? BAD : 0xFF9A3A2A) : 0xFF6A9A4A);
            if (urgent) {
                small(g, Component.translatable("gui.halgventmod.hearth.stir_now"), px, y + 99, BAD);
            } else {
                int ticks = HearthBlockEntity.STIR_EVERY - menu.get(HearthBlockEntity.D_STIR);
                small(g, Component.translatable("gui.halgventmod.hearth.stir_in", time(ticks)), px, y + 99, TEXT_DIM);
            }
        }
    }

    /** Без горшка: куски на углях и как прожарены. */
    private void grill(GuiGraphics g, int x, int y) {
        int px = x + 104;
        HearthBlockEntity hearth = minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof HearthBlockEntity be ? be : null;
        for (int cell = 0; cell < HearthBlockEntity.CELLS; cell++) {
            int cx = px + 4 + (cell % 2) * 44;
            int cy = y + 22 + (cell / 2) * 34;
            ConquestUi.slot(g, cx, cy, false);
            if (hearth == null || hearth.piece(cell).isEmpty()) {
                continue;
            }
            g.renderItem(hearth.piece(cell), cx, cy);
            sideBar(g, cx + 19, cy + 2, hearth.downSide(cell));
            sideBar(g, cx + 19, cy + 9, hearth.upSide(cell));
        }
        smallWrap(g, Component.translatable("gui.halgventmod.hearth.grill_hint"), px, y + 90, 90, y + 108, TEXT_DIM);
    }

    private void sideBar(GuiGraphics g, int x, int y, float done) {
        int color = done >= HearthBlockEntity.BURNT ? 0xFF2A2420 : done >= HearthBlockEntity.OVER ? BAD
                : done >= HearthBlockEntity.READY ? GOOD : WARN;
        bar(g, x, y, 20, 3, done / HearthBlockEntity.READY, color);
    }

    /** Книга рецептов горшка: слева от окна. */
    private void recipeBook(GuiGraphics g, int mouseX, int mouseY) {
        int left = leftPos - PANEL_WIDTH - PANEL_GAP;
        int top = topPos;
        ConquestUi.window(g, left, top, left + PANEL_WIDTH, top + imageHeight);
        ConquestUi.inset(g, left + 4, top + 4, left + PANEL_WIDTH - 4, top + 18 + recipes.size() * ROW + 2);
        plaque(g, Component.translatable("gui.halgventmod.recipes.title"), -PANEL_WIDTH - PANEL_GAP + 8, 6, -PANEL_GAP - 8);
        for (int i = 0; i < recipes.size(); i++) {
            Recipe recipe = recipes.get(i);
            int ry = top + 18 + i * ROW;
            boolean hover = mouseX >= left + 4 && mouseX < left + PANEL_WIDTH - 4 && mouseY >= ry && mouseY < ry + ROW;
            if (hover) {
                g.fill(left + 4, ry, left + PANEL_WIDTH - 4, ry + ROW, 0x30FFFFFF);
            }
            g.renderItem(new ItemStack(recipe.icon()), left + 6, ry);
            fit(g, Component.translatable("gui.halgventmod.recipes." + recipe.id()), left + 25, ry + 4, PANEL_WIDTH - 31,
                    hover ? TEXT : TEXT_DIM);
        }
        int hintsY = top + 18 + recipes.size() * ROW + 4;
        smallWrap(g, Component.translatable("gui.halgventmod.recipes.hint", HearthBlockEntity.COOK_TIME / 2 / 20,
                HearthBlockEntity.STIR_EVERY / 20), left + 8, hintsY, PANEL_WIDTH - 14, top + imageHeight - 4, TEXT_DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!panel || !menu.getCarried().isEmpty()) {
            return;
        }
        int left = leftPos - PANEL_WIDTH - PANEL_GAP;
        for (int i = 0; i < recipes.size(); i++) {
            int ry = topPos + 18 + i * ROW;
            if (mouseX >= left + 4 && mouseX < left + PANEL_WIDTH - 4 && mouseY >= ry && mouseY < ry + ROW) {
                String key = "gui.halgventmod.recipes." + recipes.get(i).id();
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable(key).withStyle(ChatFormatting.GOLD).getVisualOrderText());
                lines.addAll(font.split(Component.translatable(key + ".how"), 200));
                lines.addAll(font.split(Component.translatable(key + ".serve").withStyle(ChatFormatting.GRAY), 200));
                g.renderTooltip(font, lines, mouseX, mouseY);
            }
        }
    }
}
