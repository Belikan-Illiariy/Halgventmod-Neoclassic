package com.nrdhvnstd.halgventmod.client.smithing;

import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.menu.CementationMenu;
import com.nrdhvnstd.halgventmod.smithing.CementationBoxBlockEntity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Окно цементационной печи ({@link CementationMenu}). Слева — камера: слитки и уголь. Справа — томление: шкала суток с
 * отметками «готово», «в самый раз», «пережжено», что сейчас и сколько ждать. Внизу — «Запечатать», «Разжечь»,
 * «Вскрыть».
 */
public class CementationScreen extends StationScreen<CementationMenu> {
    private static final float SCALE_DAYS = 3.5F;
    private Button sealButton;
    private Button lightButton;
    private Button openButton;

    public CementationScreen(CementationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CementationMenu.WIDTH, CementationMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        sealButton = button(Component.translatable("gui.halgventmod.cementation.seal"), 8, 110, 58, CementationMenu.BUTTON_SEAL);
        lightButton = button(Component.translatable("gui.halgventmod.cementation.light"), 70, 110, 58, CementationMenu.BUTTON_LIGHT);
        openButton = button(Component.translatable("gui.halgventmod.cementation.open"), 132, 110, 60, CementationMenu.BUTTON_OPEN);
        containerTick();
    }

    private boolean lit() {
        return menu.get(CementationBoxBlockEntity.D_LIT) != 0;
    }

    private boolean started() {
        return menu.get(CementationBoxBlockEntity.D_ELAPSED) > 0 || lit();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int ingots = menu.get(CementationBoxBlockEntity.D_INGOTS);
        int charcoal = menu.get(CementationBoxBlockEntity.D_CHARCOAL);
        sealButton.active = !menu.sealed() && ingots > 0 && charcoal >= ingots * CementationBoxBlockEntity.CHARCOAL_PER;
        lightButton.active = menu.sealed() && !started();
        openButton.active = menu.sealed();
    }

    @Override
    protected void panels(GuiGraphics g) {
        section(g, 4, 4, 96, 104);
        plaque(g, Component.translatable("gui.halgventmod.cementation.chamber"), 8, 6, 92);
        section(g, 98, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.cementation.soak"), 102, 6, 192);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        int ingots = menu.get(CementationBoxBlockEntity.D_INGOTS);
        int charcoal = menu.get(CementationBoxBlockEntity.D_CHARCOAL);
        int need = ingots * CementationBoxBlockEntity.CHARCOAL_PER;
        small(g, Component.translatable("gui.halgventmod.cementation.charcoal"), x + 32, y + 66, TEXT_DIM);
        small(g, Component.literal(charcoal + " / " + need), x + 32, y + 74, charcoal >= need && need > 0 ? GOOD : WARN);
        if (menu.sealed()) {
            for (int i = 0; i < CementationBoxBlockEntity.CAPACITY; i++) {
                int sx = x + CementationMenu.INGOTS_X + (i % 4) * 18;
                int sy = y + CementationMenu.INGOTS_Y + (i / 4) * 18;
                g.fill(sx, sy, sx + 16, sy + 16, 0x80502818);
            }
            g.fill(x + CementationMenu.FUEL_X, y + CementationMenu.FUEL_Y, x + CementationMenu.FUEL_X + 16,
                    y + CementationMenu.FUEL_Y + 16, 0x80502818);
            small(g, Component.translatable("gui.halgventmod.cementation.sealed"), x + 8, y + 88, WARN);
        }

        // Томление: шкала суток
        int px = x + 102;
        int bw = 90;
        float days = menu.get(CementationBoxBlockEntity.D_ELAPSED) / 100F;
        int barY = y + 40;
        bar(g, px, barY, bw, 5, Math.min(1, days / SCALE_DAYS), days > CementationBoxBlockEntity.OVER ? BAD
                : days >= CementationBoxBlockEntity.DONE ? GOOD : 0xFFC88A4A);
        mark(g, px, barY, CementationBoxBlockEntity.DONE, GOOD);
        mark(g, px, barY, CementationBoxBlockEntity.BEST, GOOD);
        mark(g, px, barY, CementationBoxBlockEntity.OVER, BAD);
        small(g, Component.translatable("gui.halgventmod.cementation.days", String.format("%.1f", days)), px, y + 22, TEXT);
        Component state;
        int color;
        if (!menu.sealed()) {
            state = Component.translatable(ingots == 0 ? "gui.halgventmod.cementation.empty"
                    : charcoal < need ? "gui.halgventmod.cementation.need_charcoal" : "gui.halgventmod.cementation.ready_seal",
                    need);
            color = TEXT_DIM;
        } else if (!started()) {
            state = Component.translatable("gui.halgventmod.cementation.ready_light");
            color = WARN;
        } else if (days < CementationBoxBlockEntity.DONE) {
            state = Component.translatable("gui.halgventmod.cementation.soaking",
                    menu.get(CementationBoxBlockEntity.D_LEFT_MINUTES));
            color = TEXT;
        } else if (days <= CementationBoxBlockEntity.BEST) {
            state = Component.translatable("gui.halgventmod.cementation.best");
            color = GOOD;
        } else if (days <= CementationBoxBlockEntity.OVER) {
            state = Component.translatable("gui.halgventmod.cementation.done");
            color = GOOD;
        } else {
            state = Component.translatable("gui.halgventmod.cementation.over");
            color = BAD;
        }
        smallWrap(g, state, px, y + 52, 92, y + 100, color);
    }

    /** Отметка на шкале суток. */
    private void mark(GuiGraphics g, int x, int y, double days, int color) {
        int mx = x + Math.round((float) (days / SCALE_DAYS) * 90);
        g.fill(mx, y - 3, mx + 1, y + 8, color);
    }
}
