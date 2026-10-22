package com.nrdhvnstd.halgventmod.client.brewing;

import com.nrdhvnstd.halgventmod.brewing.AgingCaskBlockEntity;
import com.nrdhvnstd.halgventmod.brewing.Brewing;
import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.menu.AgingCaskMenu;
import com.nrdhvnstd.halgventmod.world.Calendar;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Окно бочки для выдержки ({@link AgingCaskMenu}): где стоит и как быстро выдерживает, места под склянки (под каждой —
 * сколько ступеней качества набрала и полоса до следующей; в подсказке — когда заложена и когда следующая ступень),
 * что сюда кладут.
 */
public class AgingCaskScreen extends StationScreen<AgingCaskMenu> {
    public AgingCaskScreen(AgingCaskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AgingCaskMenu.WIDTH, AgingCaskMenu.HEIGHT);
    }

    @Override
    protected void panels(GuiGraphics g) {
        section(g, 4, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.cask.title"), 40, 6, 160);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        int place = menu.get(AgingCaskBlockEntity.D_PLACE);
        String where = place == AgingCaskBlockEntity.PLACE_EXPOSED ? "exposed"
                : place == AgingCaskBlockEntity.PLACE_CELLAR ? "cellar" : "house";
        int days = place == AgingCaskBlockEntity.PLACE_CELLAR ? Brewing.AGE_DAYS_CELLAR : Brewing.AGE_DAYS;
        smallWrap(g, Component.translatable("gui.halgventmod.cask." + where, days), x + 10, y + 21, 180, y + 37,
                place == AgingCaskBlockEntity.PLACE_EXPOSED ? BAD : TEXT);

        for (int i = 0; i < AgingCaskBlockEntity.CAPACITY; i++) {
            int sx = x + AgingCaskMenu.BOTTLES_X + i * AgingCaskMenu.BOTTLE_STEP;
            int sy = y + AgingCaskMenu.BOTTLES_Y;
            if (!menu.slots.get(AgingCaskMenu.CASK_START + i).hasItem()) {
                continue;
            }
            int steps = menu.get(AgingCaskBlockEntity.D_STEPS + i);
            bar(g, sx, sy + 19, 16, 2, menu.get(AgingCaskBlockEntity.D_PROGRESS + i) / 100.0F, 0xFFC88A4A);
            if (steps > 0) {
                small(g, Component.literal("+" + steps), sx + 4, sy + 24, GOOD);
            }
        }
        smallWrap(g, Component.translatable("gui.halgventmod.cask.hint"), x + 10, y + 76, 180, y + 102, TEXT_DIM);
    }

    /** Подсказка к склянке в бочке: когда заложена, сколько набрала и к какому числу следующая ступень. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int i = hoveredSlot == null ? -1 : hoveredSlot.index - AgingCaskMenu.CASK_START;
        if (i < 0 || i >= AgingCaskBlockEntity.CAPACITY) {
            return lines;
        }
        int ago = menu.get(AgingCaskBlockEntity.D_PLACED_AGO + i);
        if (ago == 0) {
            lines.add(Component.translatable("gui.halgventmod.cask.placed_today").withStyle(ChatFormatting.GRAY));
        } else if (ago > 0) {
            lines.add(Component.translatable("gui.halgventmod.cask.placed", Calendar.dateText(Calendar.clientDay - ago))
                    .withStyle(ChatFormatting.GRAY));
        }
        int steps = menu.get(AgingCaskBlockEntity.D_STEPS + i);
        if (steps > 0) {
            lines.add(Component.translatable("gui.halgventmod.cask.steps", steps).withStyle(ChatFormatting.GREEN));
        }
        int next = menu.get(AgingCaskBlockEntity.D_NEXT_IN + i);
        if (next >= 0) {
            lines.add(Component.translatable("gui.halgventmod.cask.next", Calendar.dateText(Calendar.clientDay + next))
                    .withStyle(ChatFormatting.GOLD));
        } else if (menu.get(AgingCaskBlockEntity.D_PROGRESS + i) >= 100) {
            lines.add(Component.translatable("gui.halgventmod.cask.masterwork").withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.translatable("gui.halgventmod.cask.next_never").withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}
