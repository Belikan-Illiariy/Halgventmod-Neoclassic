package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.menu.ForgeMenu;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.smithing.Crucible;
import com.nrdhvnstd.halgventmod.smithing.ForgeBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.Melt;
import com.nrdhvnstd.halgventmod.smithing.Metal;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Окно кузнечного горна ({@link ForgeMenu}). Слева — огонь: жар, сколько угля, ячейка угля. Справа — тигель: ячейка
 * тигля и шихты, состав (доли металлов), что выйдет и насколько состав хорош, жидкий ли расплав. «Мехи» — качнуть,
 * «Плавить» — мини-игра плавки. Слева от окна — какие сплавы как составить.
 */
public class ForgeScreen extends StationScreen<ForgeMenu> {
    private Button meltButton;
    private Button pumpButton;

    public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ForgeMenu.WIDTH, ForgeMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        pumpButton = button(Component.translatable("gui.halgventmod.forge.pump"), 4, 110, 94, ForgeMenu.BUTTON_PUMP);
        meltButton = button(Component.translatable("gui.halgventmod.forge.melt"), 100, 110, 96, ForgeMenu.BUTTON_MELT);
        containerTick();
    }

    @Override
    protected List<BookEntry> book() {
        return List.of(
                new BookEntry(new ItemStack(SmithingItems.BRONZE_INGOT.get()), "gui.halgventmod.forge.book.bronze"),
                new BookEntry(new ItemStack(SmithingItems.BRASS_INGOT.get()), "gui.halgventmod.forge.book.brass"),
                new BookEntry(new ItemStack(SmithingItems.PEWTER_INGOT.get()), "gui.halgventmod.forge.book.pewter"),
                new BookEntry(new ItemStack(SmithingItems.GOLD_INGOT.get()), "gui.halgventmod.forge.book.gold"),
                new BookEntry(new ItemStack(SmithingItems.COPPER_INGOT.get()), "gui.halgventmod.forge.book.pure"),
                new BookEntry(new ItemStack(ModItems.CLAY_CRUCIBLE.get()), "gui.halgventmod.forge.book.remelt"));
    }

    @Override
    protected Component bookHint() {
        return Component.translatable("gui.halgventmod.forge.book_hint", Crucible.CAPACITY / Crucible.PER_INGOT,
                Crucible.COOL_TICKS / 20);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean lit = menu.get(ForgeBlockEntity.D_LIT) != 0;
        pumpButton.active = lit;
        ItemStack crucible = menu.crucible();
        meltButton.active = lit && Crucible.is(crucible) && Crucible.total(Crucible.melt(crucible)) > 0
                && minecraft != null && minecraft.level != null && !Crucible.liquid(crucible, minecraft.level);
    }

    @Override
    protected void panels(GuiGraphics g) {
        firePanel(g);
        section(g, 100, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.forge.crucible"), 104, 6, 192);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        boolean lit = menu.get(ForgeBlockEntity.D_LIT) != 0;
        int degrees = menu.get(ForgeBlockEntity.D_HEAT);
        int fuelTicks = menu.get(ForgeBlockEntity.D_FUEL) * 20;
        boolean hot = degrees >= ForgeBlockEntity.WORKABLE * 10;
        Component state = Component.translatable(!lit ? "gui.halgventmod.forge.out"
                : hot ? "gui.halgventmod.forge.hot" : "gui.halgventmod.forge.warming");
        fireSection(g, degrees, 1000, Component.literal(degrees + "°"), state, !lit ? TEXT_DIM : hot ? GOOD : WARN, lit,
                fuelTicks, fuelTicks, ForgeBlockEntity.MAX_FUEL, ForgeBlockEntity.WORKABLE * 10);
        if (menu.get(ForgeBlockEntity.D_COAL) != 0) {
            small(g, Component.translatable("gui.halgventmod.forge.coal"), x + 48, y + 85, WARN);
        }

        // Тигель и шихта
        ItemStack crucible = menu.crucible();
        Melt melt = Crucible.melt(crucible);
        int total = Crucible.total(melt);
        small(g, Component.translatable("gui.halgventmod.forge.crucible_slot"), x + 126, y + 21, TEXT_DIM);
        if (Crucible.is(crucible)) {
            small(g, Component.translatable("gui.halgventmod.forge.amount", ForgeBlockEntity.ingots(total),
                    Crucible.CAPACITY / Crucible.PER_INGOT), x + 126, y + 29, total > 0 ? TEXT : TEXT_DIM);
        } else {
            small(g, Component.translatable("gui.halgventmod.forge.no_crucible"), x + 126, y + 29, BAD);
        }
        small(g, Component.translatable("gui.halgventmod.forge.charge"), x + 126, y + 43, TEXT_DIM);
        int reject = menu.get(ForgeBlockEntity.D_REJECT);
        if (!menu.charge().isEmpty() && reject > 0 && reject < ForgeBlockEntity.REJECTS.length) {
            smallWrap(g, Component.translatable("message.halgventmod.smithing." + ForgeBlockEntity.REJECTS[reject]),
                    x + 126, y + 51, 68, y + 62, BAD);
        } else {
            small(g, Component.translatable("gui.halgventmod.forge.charge_hint"), x + 126, y + 51, TEXT_DIM);
        }
        if (total <= 0) {
            smallWrap(g, Component.translatable(melt != null && melt.lid() ? "gui.halgventmod.forge.only_lid"
                    : "gui.halgventmod.forge.empty"), x + 104, y + 66, 88, y + 102, TEXT_DIM);
            return;
        }
        // Состав
        MutableComponent parts = Component.empty();
        boolean first = true;
        for (Map.Entry<Metal, Integer> entry : Crucible.parts(melt).entrySet()) {
            if (!first) {
                parts.append(" · ");
            }
            parts.append(Component.translatable(entry.getKey().langKey())).append(" " + Math.round(100F * entry.getValue() / total) + "%");
            first = false;
        }
        smallWrap(g, parts, x + 104, y + 64, 88, y + 78, TEXT);
        // Что выйдет
        Crucible.Alloy alloy = Crucible.willGive(melt);
        List<Component> notes = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (alloy == null) {
            notes.add(Component.translatable("gui.halgventmod.forge.no_alloy"));
            colors.add(BAD);
        } else {
            notes.add(Component.translatable("gui.halgventmod.forge.gives", Component.translatable(alloy.metal().langKey())));
            colors.add(alloy.delta() < 0 ? WARN : GOOD);
            if (alloy.minor() != null && !melt.molten()) {
                int have = Math.round(alloy.fraction() * 100);
                int ideal = Math.round(alloy.ideal() * 100);
                String key = alloy.delta() == 0 ? "gui.halgventmod.forge.ratio_good"
                        : alloy.fraction() < alloy.ideal() ? "gui.halgventmod.forge.ratio_low" : "gui.halgventmod.forge.ratio_high";
                notes.add(Component.translatable(key, Component.translatable(alloy.minor().langKey()), have, ideal));
                colors.add(alloy.delta() == 0 ? GOOD : WARN);
            }
        }
        if (!melt.lid() && Crucible.parts(melt).containsKey(Metal.ZINC) && !melt.molten()) {
            notes.add(Component.translatable("gui.halgventmod.forge.no_lid"));
            colors.add(WARN);
        }
        if (minecraft != null && minecraft.level != null) {
            if (!melt.molten()) {
                notes.add(Component.translatable("gui.halgventmod.forge.unmelted"));
                colors.add(TEXT_DIM);
            } else if (Crucible.liquid(crucible, minecraft.level)) {
                notes.add(Component.translatable("gui.halgventmod.forge.liquid", time(Crucible.liquidSeconds(crucible,
                        minecraft.level) * 20)));
                colors.add(GOOD);
            } else {
                notes.add(Component.translatable("gui.halgventmod.forge.solid"));
                colors.add(WARN);
            }
        }
        int line = y + 74;
        for (int i = 0; i < notes.size() && line < y + 100; i++) {
            smallWrap(g, notes.get(i), x + 104, line, 88, y + 103, colors.get(i));
            line += 7;
        }
    }
}
