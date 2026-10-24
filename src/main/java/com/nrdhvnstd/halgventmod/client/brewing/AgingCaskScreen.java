package com.nrdhvnstd.halgventmod.client.brewing;

import com.nrdhvnstd.halgventmod.brewing.AgingCaskBlockEntity;
import com.nrdhvnstd.halgventmod.brewing.Brewing;
import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.menu.AgingCaskMenu;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Окно бочки для выдержки ({@link AgingCaskMenu}): слева «влить» и пустые склянки после него, где стоит бочка и как
 * быстро выдерживает; посередине — что налито (значок — каким выйдет сейчас, подсказка при наведении), сколько, когда
 * заложено, сколько ступеней набрало, полоса до следующей и к какому числу; внизу справа — склянки или сосуд
 * и «налитое»; «Вылить».
 */
public class AgingCaskScreen extends StationScreen<AgingCaskMenu> {
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int DRINK_X = 84, DRINK_Y = 18;
    private Button pourButton;

    public AgingCaskScreen(AgingCaskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AgingCaskMenu.WIDTH, AgingCaskMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        pourButton = button(Component.translatable("gui.halgventmod.cask.pour_out"), 8, 110, 70, AgingCaskMenu.BUTTON_POUR_OUT);
        containerTick();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        pourButton.active = menu.get(AgingCaskBlockEntity.D_MEASURES) > 0;
    }

    @Override
    protected void panels(GuiGraphics g) {
        section(g, 4, 4, 80, 104);
        plaque(g, Component.translatable("gui.halgventmod.cask.into"), 8, 6, 76);
        section(g, 82, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.cask.title"), 86, 6, 192);
    }

    /** Налитое, каким оно выйдет сейчас: качество заливки + набранные ступени; пусто — ничего не налито. */
    private ItemStack shown() {
        AgingCaskBlockEntity cask = minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof AgingCaskBlockEntity be ? be : null;
        if (cask == null || cask.drink().isEmpty() || menu.get(AgingCaskBlockEntity.D_MEASURES) <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = cask.drink().copyWithCount(1);
        return FoodQuality.set(stack, FoodQuality.get(stack) + menu.get(AgingCaskBlockEntity.D_STEPS));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        small(g, Component.translatable("gui.halgventmod.cask.in"), x + 32, y + 27, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.cask.empties"), x + 32, y + 53, TEXT_DIM);
        int place = menu.get(AgingCaskBlockEntity.D_PLACE);
        String where = place == AgingCaskBlockEntity.PLACE_EXPOSED ? "exposed"
                : place == AgingCaskBlockEntity.PLACE_CELLAR ? "cellar" : "house";
        int days = place == AgingCaskBlockEntity.PLACE_CELLAR ? Brewing.AGE_DAYS_CELLAR : Brewing.AGE_DAYS;
        smallWrap(g, Component.translatable("gui.halgventmod.cask." + where, days), x + 8, y + 70, 70, y + 102,
                place == AgingCaskBlockEntity.PLACE_EXPOSED ? BAD : TEXT);

        int measures = menu.get(AgingCaskBlockEntity.D_MEASURES);
        ItemStack drink = shown();
        int px = x + DRINK_X;
        if (!drink.isEmpty()) {
            g.renderItem(drink, px, y + DRINK_Y);
            g.renderItemDecorations(font, drink, px, y + DRINK_Y);
            fit(g, drink.getHoverName(), px + 20, y + 18, 92, TEXT);
            small(g, Component.translatable("gui.halgventmod.cask.measures", measures, AgingCaskBlockEntity.CAPACITY),
                    px + 20, y + 28, TEXT_DIM);
            bar(g, px + 1, y + 38, 106, 3, measures / (float) AgingCaskBlockEntity.CAPACITY, 0xFF8A3048);

            int line = y + 45;
            int ago = menu.get(AgingCaskBlockEntity.D_FILLED_AGO);
            if (ago == 0) {
                small(g, Component.translatable("gui.halgventmod.cask.placed_today"), px, line, TEXT_DIM);
                line += 8;
            } else if (ago > 0) {
                small(g, Component.translatable("gui.halgventmod.cask.placed", Calendar.dateText(Calendar.clientDay - ago)),
                        px, line, TEXT_DIM);
                line += 8;
            }
            int steps = menu.get(AgingCaskBlockEntity.D_STEPS);
            if (steps > 0) {
                small(g, Component.translatable("gui.halgventmod.cask.steps", steps), px, line, GOOD);
                line += 8;
            }
            int progress = menu.get(AgingCaskBlockEntity.D_PROGRESS);
            int next = menu.get(AgingCaskBlockEntity.D_NEXT_IN);
            Component when;
            int color;
            if (next >= 0) {
                when = Component.translatable("gui.halgventmod.cask.next", Calendar.dateText(Calendar.clientDay + next));
                color = WARN;
            } else if (progress >= 100) {
                when = Component.translatable("gui.halgventmod.cask.masterwork");
                color = GOOD;
            } else {
                when = Component.translatable("gui.halgventmod.cask.next_never");
                color = BAD;
            }
            small(g, when, px, line, color);
            bar(g, px + 1, y + 70, 106, 2, progress / 100.0F, progress >= 100 ? GOOD : 0xFFC88A4A);
        } else {
            smallWrap(g, Component.translatable("gui.halgventmod.cask.empty"), px, y + 22, 108, y + 70, TEXT_DIM);
        }

        g.blitSprite(ARROW, x + AgingCaskMenu.BOTTLES_X + 22, y + AgingCaskMenu.BOTTLES_Y, 24, 16);
        small(g, Component.translatable("gui.halgventmod.cask.vessel"), x + 100, y + AgingCaskMenu.BOTTLES_Y + 19, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.cask.out"), x + 160, y + AgingCaskMenu.OUT_Y + 19, TEXT_DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ItemStack drink = shown();
        if (!drink.isEmpty() && menu.getCarried().isEmpty() && hovering(DRINK_X, DRINK_Y, 16, 16, mouseX, mouseY)) {
            g.renderTooltip(font, drink, mouseX, mouseY);
        }
    }
}
