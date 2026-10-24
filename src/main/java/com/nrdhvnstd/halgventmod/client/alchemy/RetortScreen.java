package com.nrdhvnstd.halgventmod.client.alchemy;

import java.util.List;

import com.nrdhvnstd.halgventmod.alchemy.Alchemy;
import com.nrdhvnstd.halgventmod.alchemy.RetortBlockEntity;
import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.menu.RetortMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Окно реторты ({@link RetortMenu}): слева — огонь под ретортой (жар или «нет огня»); справа — колба и добавка, полоса
 * возгонки и что сейчас, ниже — приёмник и остаток; слева от окна — книга: что возгоняют и что выйдет.
 */
public class RetortScreen extends StationScreen<RetortMenu> {
    public RetortScreen(RetortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RetortMenu.WIDTH, RetortMenu.HEIGHT);
    }

    @Override
    protected List<BookEntry> book() {
        return Alchemy.sublimations().stream()
                .map(s -> new BookEntry(s.icon(), "gui.halgventmod.retort.book." + s.id()))
                .toList();
    }

    @Override
    protected Component bookHint() {
        return Component.translatable("gui.halgventmod.retort.book_hint");
    }

    @Override
    protected void panels(GuiGraphics g) {
        section(g, 4, 4, 98, 104);
        plaque(g, Component.translatable("gui.halgventmod.hearth.fire"), 8, 6, 94);
        section(g, 100, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.retort.title"), 104, 6, 192);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        int heat = menu.get(RetortBlockEntity.D_HEAT);
        smallWrap(g, Component.translatable(heat > 0 ? "gui.halgventmod.retort.source_heat" : "gui.halgventmod.retort.source_none",
                heat), x + 8, y + 22, 88, y + 100, heat > 0 ? (heat >= 3 ? WARN : TEXT) : BAD);

        int px = x + 104;
        small(g, Component.translatable("gui.halgventmod.retort.in"), x + RetortMenu.IN_X - 2, y + RetortMenu.IN_Y + 19, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.retort.additive"), x + RetortMenu.ADD_X - 6, y + RetortMenu.ADD_Y + 19,
                TEXT_DIM);
        int state = menu.get(RetortBlockEntity.D_STATE);
        bar(g, px + 1, y + 50, 86, 3, menu.get(RetortBlockEntity.D_PROGRESS) / (float) Alchemy.SUBLIME_TIME,
                heat >= 3 ? BAD : 0xFFD8B040);
        String key = switch (state) {
            case RetortBlockEntity.STATE_UNKNOWN -> "unknown";
            case RetortBlockEntity.STATE_NEEDS_ADDITIVE -> "needs_additive";
            case RetortBlockEntity.STATE_COLD -> "cold";
            case RetortBlockEntity.STATE_FULL -> "full";
            case RetortBlockEntity.STATE_WORKING -> heat >= 3 ? "hot" : "working";
            default -> "empty";
        };
        int color = state == RetortBlockEntity.STATE_WORKING ? (heat >= 3 ? WARN : GOOD)
                : state == RetortBlockEntity.STATE_EMPTY ? TEXT_DIM : WARN;
        smallWrap(g, Component.translatable("gui.halgventmod.retort.state_" + key), px, y + 56, 92, y + 74, color);

        small(g, Component.translatable("gui.halgventmod.retort.out"), x + RetortMenu.OUT_X - 4, y + RetortMenu.OUT_Y + 19, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.retort.residue"), x + RetortMenu.RESIDUE_X - 4,
                y + RetortMenu.RESIDUE_Y + 19, TEXT_DIM);
    }
}
