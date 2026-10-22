package com.nrdhvnstd.halgventmod.client.brewing;

import com.nrdhvnstd.halgventmod.brewing.FermentingBarrelBlockEntity;
import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.menu.FermentingBarrelMenu;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Окно бродильной бочки ({@link FermentingBarrelMenu}): слева ячейки «влить» и «добавка»; посередине — что налито,
 * сколько, какие добавки, полоса брожения и до какого числа ждать (готово — когда скиснет); внизу справа — склянки или
 * корчага и «готовое»; «Вылить».
 */
public class FermentingBarrelScreen extends StationScreen<FermentingBarrelMenu> {
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private Button pourButton;
    private Button sealButton;

    public FermentingBarrelScreen(FermentingBarrelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FermentingBarrelMenu.WIDTH, FermentingBarrelMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        pourButton = button(Component.translatable("gui.halgventmod.barrel.pour_out"), 8, 110, 70,
                FermentingBarrelMenu.BUTTON_POUR_OUT);
        sealButton = button(Component.empty(), 84, 110, 108, FermentingBarrelMenu.BUTTON_SEAL);
        containerTick();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean any = menu.get(FermentingBarrelBlockEntity.D_PORTIONS) > 0;
        pourButton.active = any && menu.open();
        sealButton.setMessage(Component.translatable(menu.open() ? "gui.halgventmod.barrel.seal" : "gui.halgventmod.barrel.unseal"));
        sealButton.active = any || !menu.open();
    }

    @Override
    protected void panels(GuiGraphics g) {
        section(g, 4, 4, 80, 104);
        plaque(g, Component.translatable("gui.halgventmod.barrel.into"), 8, 6, 76);
        section(g, 82, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.barrel.title"), 86, 6, 192);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        small(g, Component.translatable("gui.halgventmod.barrel.in"), x + 32, y + 27, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.barrel.flavour"), x + 32, y + 53, TEXT_DIM);

        FermentingBarrelBlockEntity barrel = minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof FermentingBarrelBlockEntity be ? be : null;
        int portions = menu.get(FermentingBarrelBlockEntity.D_PORTIONS);
        int state = menu.get(FermentingBarrelBlockEntity.D_STATE);
        int px = x + 84;
        if (barrel != null && portions > 0 && !barrel.base().isEmpty()) {
            g.renderItem(barrel.base(), px, y + 18);
            fit(g, barrel.base().getHoverName(), px + 20, y + 18, 92, TEXT);
            small(g, Component.translatable("gui.halgventmod.barrel.portions", portions, FermentingBarrelBlockEntity.MAX_PORTIONS),
                    px + 20, y + 28, TEXT_DIM);
            MutableComponent flavours = Component.empty();
            for (String flavour : barrel.flavours()) {
                flavours.append(Component.translatable("drink.halgventmod.flag." + flavour));
            }
            if (!barrel.flavours().isEmpty()) {
                smallWrap(g, Component.translatable("gui.halgventmod.barrel.with").append(flavours), px, y + 37, 108, y + 48, GOOD);
            }
        } else {
            fit(g, Component.translatable("gui.halgventmod.barrel.empty"), px, y + 22, 108, TEXT_DIM);
        }

        float progress = menu.get(FermentingBarrelBlockEntity.D_PROGRESS) / 100.0F;
        int left = menu.get(FermentingBarrelBlockEntity.D_DAYS_LEFT);
        // Срок — календарная дата, как у еды: сегодня + сколько суток осталось
        Component date = Calendar.dateText(Calendar.clientDay + Math.max(0, left));
        Component line;
        int color;
        switch (state) {
            case FermentingBarrelBlockEntity.STATE_NEEDS_FLAVOUR -> {
                line = Component.translatable("gui.halgventmod.barrel.needs_flavour");
                color = WARN;
            }
            case FermentingBarrelBlockEntity.STATE_FERMENTING -> {
                line = Component.translatable("gui.halgventmod.barrel.fermenting", date, left);
                color = TEXT;
            }
            case FermentingBarrelBlockEntity.STATE_READY -> {
                line = left == FermentingBarrelBlockEntity.NO_DAY ? Component.translatable("gui.halgventmod.barrel.ready_tincture")
                        : menu.open() ? Component.translatable("gui.halgventmod.barrel.ready_open")
                        : Component.translatable("gui.halgventmod.barrel.ready", date);
                color = GOOD;
            }
            case FermentingBarrelBlockEntity.STATE_SOUR -> {
                line = Component.translatable("gui.halgventmod.barrel.sour");
                color = BAD;
            }
            default -> {
                line = Component.empty();
                color = TEXT_DIM;
            }
        }
        if (portions > 0 && menu.open() && state < FermentingBarrelBlockEntity.STATE_READY) {
            line = Component.translatable("gui.halgventmod.barrel.open_hint");
            color = WARN;
        }
        if (portions > 0) {
            bar(g, px + 1, y + 50, 106, 3, state >= FermentingBarrelBlockEntity.STATE_READY ? 1 : progress,
                    state == FermentingBarrelBlockEntity.STATE_SOUR ? BAD : state == FermentingBarrelBlockEntity.STATE_READY ? GOOD
                            : 0xFFC88A4A);
            smallWrap(g, line, px, y + 56, 108, y + 72, color);
        }

        g.blitSprite(ARROW, x + FermentingBarrelMenu.BOTTLES_X + 22, y + FermentingBarrelMenu.BOTTLES_Y, 24, 16);
        small(g, Component.translatable("gui.halgventmod.barrel.vessel"), x + 100, y + FermentingBarrelMenu.BOTTLES_Y + 19, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.barrel.out"), x + 163, y + FermentingBarrelMenu.OUT_Y + 19, TEXT_DIM);
        if (!menu.open()) {
            // Крышка: ячейки бочки закрыты
            int[][] cells = {{FermentingBarrelMenu.IN_X, FermentingBarrelMenu.IN_Y}, {FermentingBarrelMenu.FLAVOUR_X, FermentingBarrelMenu.FLAVOUR_Y},
                    {FermentingBarrelMenu.BOTTLES_X, FermentingBarrelMenu.BOTTLES_Y}};
            for (int[] cell : cells) {
                g.fill(x + cell[0], y + cell[1], x + cell[0] + 16, y + cell[1] + 16, 0x80201008);
            }
            smallWrap(g, Component.translatable("gui.halgventmod.barrel.sealed_hint"), x + 8, y + 72, 70, y + 102, TEXT_DIM);
        }
    }
}
