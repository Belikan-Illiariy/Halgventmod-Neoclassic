package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.menu.BloomeryMenu;
import com.nrdhvnstd.halgventmod.smithing.BloomeryBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.Crucible;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Окно сыродутного горна ({@link BloomeryMenu}). Слева — закладка: руда, уголь, флюс, сколько чего в горне, и тигель
 * (медь, олово, серебро стекают в него).
 * Справа — плавка: какая руда, сколько закладок, что выйдет и что скажется на качестве; «Плавить» — окно мехов,
 * «Выгрести» — всё назад. Слева от окна — что из какой руды выходит.
 */
public class BloomeryScreen extends StationScreen<BloomeryMenu> {
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private Button smeltButton;
    private Button takeButton;

    public BloomeryScreen(BloomeryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BloomeryMenu.WIDTH, BloomeryMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        takeButton = button(Component.translatable("gui.halgventmod.bloomery.take"), 8, 110, 70, BloomeryMenu.BUTTON_TAKE_ALL);
        smeltButton = button(Component.translatable("gui.halgventmod.bloomery.smelt"), 84, 110, 108, BloomeryMenu.BUTTON_SMELT);
        containerTick();
    }

    @Override
    protected List<BookEntry> book() {
        List<BookEntry> list = new ArrayList<>();
        for (BloomeryBlockEntity.Smelt smelt : BloomeryBlockEntity.Smelt.values()) {
            list.add(new BookEntry(new ItemStack(smelt.ore()), "gui.halgventmod.bloomery.book." + smelt.name().toLowerCase()));
        }
        return list;
    }

    @Override
    protected Component bookHint() {
        return Component.translatable("gui.halgventmod.bloomery.book_hint", BloomeryBlockEntity.FUEL_PER_BATCH,
                BloomeryBlockEntity.MAX_BATCHES);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean lit = menu.lit();
        smeltButton.active = !lit && menu.get(BloomeryBlockEntity.D_BATCHES) > 0;
        takeButton.active = !lit && (menu.get(BloomeryBlockEntity.D_ORE) + menu.get(BloomeryBlockEntity.D_CHARCOAL)
                + menu.get(BloomeryBlockEntity.D_COAL) + menu.get(BloomeryBlockEntity.D_FLUX)) > 0;
    }

    @Override
    protected void panels(GuiGraphics g) {
        section(g, 4, 4, 80, 104);
        plaque(g, Component.translatable("gui.halgventmod.bloomery.charge"), 8, 6, 76);
        section(g, 82, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.bloomery.title"), 86, 6, 192);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        int ore = menu.get(BloomeryBlockEntity.D_ORE);
        int roasted = menu.get(BloomeryBlockEntity.D_ROASTED);
        int charcoal = menu.get(BloomeryBlockEntity.D_CHARCOAL);
        int coal = menu.get(BloomeryBlockEntity.D_COAL);
        int flux = menu.get(BloomeryBlockEntity.D_FLUX);
        int batches = menu.get(BloomeryBlockEntity.D_BATCHES);
        int smeltIndex = menu.get(BloomeryBlockEntity.D_SMELT);
        BloomeryBlockEntity.Smelt smelt = smeltIndex >= 0 && smeltIndex < BloomeryBlockEntity.Smelt.values().length
                ? BloomeryBlockEntity.Smelt.values()[smeltIndex] : null;

        // Закладка: подписи и сколько в горне
        small(g, Component.translatable("gui.halgventmod.bloomery.ore"), x + 32, y + 20, TEXT_DIM);
        small(g, Component.literal(ore + " / " + BloomeryBlockEntity.MAX_ORE), x + 32, y + 28, ore > 0 ? TEXT : TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.bloomery.fuel"), x + 32, y + 40, TEXT_DIM);
        small(g, Component.literal((charcoal + coal) + " / " + BloomeryBlockEntity.MAX_FUEL), x + 32, y + 48,
                coal > 0 ? WARN : charcoal > 0 ? TEXT : TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.bloomery.flux"), x + 32, y + 60, TEXT_DIM);
        small(g, Component.literal(flux + " / " + BloomeryBlockEntity.MAX_FLUX), x + 32, y + 68, flux > 0 ? TEXT : TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.bloomery.crucible"), x + 32, y + 82, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.bloomery.crucible_for"), x + 32, y + 90, TEXT_DIM);

        // Плавка
        int px = x + 86;
        if (smelt == null || ore <= 0) {
            smallWrap(g, Component.translatable(menu.lit() ? "gui.halgventmod.bloomery.burning"
                    : "gui.halgventmod.bloomery.empty"), px, y + 20, 106, y + 100, TEXT_DIM);
            return;
        }
        g.renderItem(new ItemStack(smelt.ore()), px, y + 18);
        fit(g, new ItemStack(smelt.ore()).getHoverName(), px + 19, y + 22, 88, TEXT);
        small(g, Component.translatable("gui.halgventmod.bloomery.per_batch", smelt.perBatch(),
                BloomeryBlockEntity.FUEL_PER_BATCH), px, y + 37, TEXT_DIM);
        // Что выйдет
        g.blitSprite(ARROW, px + 4, y + 47, 24, 16);
        ItemStack result = new ItemStack(smelt.result(), Math.max(1, batches));
        g.renderItem(result, px + 34, y + 47);
        g.renderItemDecorations(font, result, px + 34, y + 47);
        fit(g, Component.translatable("gui.halgventmod.bloomery.batches", batches, BloomeryBlockEntity.MAX_BATCHES),
                px + 54, y + 51, 54, batches > 0 ? GOOD : BAD);
        // Что скажется на качестве
        int line = y + 68;
        List<Component> notes = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (batches <= 0) {
            notes.add(Component.translatable("gui.halgventmod.bloomery.not_enough", smelt.perBatch(),
                    BloomeryBlockEntity.FUEL_PER_BATCH));
            colors.add(BAD);
        }
        if (smelt == BloomeryBlockEntity.Smelt.BOG || smelt == BloomeryBlockEntity.Smelt.COPPER) {
            boolean all = roasted >= ore;
            notes.add(Component.translatable(all ? "gui.halgventmod.bloomery.roasted" : "gui.halgventmod.bloomery.unroasted",
                    roasted, ore));
            colors.add(all ? GOOD : WARN);
        }
        if (smelt.metal() != null && batches > 0) {
            String why = Crucible.canPour(menu.crucible(), smelt.metal(), batches * Crucible.PER_INGOT);
            notes.add(why == null ? Component.translatable("gui.halgventmod.bloomery.to_crucible")
                    : Component.translatable("message.halgventmod.smithing." + why));
            colors.add(why == null ? GOOD : BAD);
        }
        if (coal > 0) {
            notes.add(Component.translatable("gui.halgventmod.bloomery.coal"));
            colors.add(BAD);
        }
        if (batches > 0 && flux < batches) {
            notes.add(Component.translatable("gui.halgventmod.bloomery.no_flux", batches - flux));
            colors.add(WARN);
        }
        for (int i = 0; i < notes.size() && line < y + 100; i++) {
            smallWrap(g, notes.get(i), px, line, 106, y + 104, colors.get(i));
            line += 9;
        }
    }
}
