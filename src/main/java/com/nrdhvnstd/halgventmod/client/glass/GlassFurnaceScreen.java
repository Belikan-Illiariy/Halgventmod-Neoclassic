package com.nrdhvnstd.halgventmod.client.glass;

import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.glass.GlassFurnaceBlockEntity;
import com.nrdhvnstd.halgventmod.glass.GlassItems;
import com.nrdhvnstd.halgventmod.glass.Glassmaking;
import com.nrdhvnstd.halgventmod.menu.GlassFurnaceMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Окно стекловаренной печи ({@link GlassFurnaceMenu}) — всё делается отсюда:
 * <ul>
 *   <li>слева — что выдувать: изделия (каким выйдет — с цветом добавки), сколько порций; щелчок — выбрать;
 *       «Выдувать» — мини-игра (нужна трубка при себе), после неё окно открывается снова;</li>
 *   <li>посередине — огонь: градусник с отметками «можно выдувать» и «варится», сколько гореть, дрова,
 *       «Разжечь» / «Затушить»;</li>
 *   <li>справа — ячейка шихты и что в печи, стекломасса и её качество, полоса варки, ячейка цвета, камера отжига
 *       с полосами и «Забрать отожжённое».</li>
 * </ul>
 */
public class GlassFurnaceScreen extends StationScreen<GlassFurnaceMenu> {
    private static final int FORMS_X = 8;
    private static final int FORMS_Y = 20;
    private static final int FORM_ROW = 20;
    private static final int SELECTED = 0xFFFFD27F;
    private Button fireButton;
    private Button blowButton;
    private Button takeButton;

    public GlassFurnaceScreen(GlassFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GlassFurnaceMenu.WIDTH, GlassFurnaceMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        blowButton = button(Component.translatable("gui.halgventmod.glass.blow_button"), 8, 110, 70, GlassFurnaceMenu.BUTTON_BLOW);
        fireButton = addRenderableWidget(Button.builder(Component.empty(), button -> click(lit()
                ? GlassFurnaceMenu.BUTTON_EXTINGUISH : GlassFurnaceMenu.BUTTON_IGNITE))
                .bounds(leftPos + 84, topPos + 110, 72, 16).build());
        takeButton = button(Component.translatable("gui.halgventmod.glass.take"), 162, 110, 88, GlassFurnaceMenu.BUTTON_TAKE);
        containerTick();
    }

    private boolean lit() {
        return menu.get(GlassFurnaceBlockEntity.D_LIT) != 0;
    }

    private Glassmaking.Form form() {
        return Glassmaking.Form.byIndex(menu.get(GlassFurnaceBlockEntity.D_FORM));
    }

    private boolean hasPipe() {
        return minecraft != null && minecraft.player != null
                && minecraft.player.getInventory().countItem(GlassItems.BLOWPIPE.get()) > 0;
    }

    /** Какой цвет даст добавка в ячейке (лесное — без неё). */
    private Glassmaking.Tint tint() {
        Glassmaking.Tint tint = Glassmaking.Tint.of(menu.slots.get(GlassFurnaceMenu.COLORANT_SLOT).getItem());
        return tint == null ? Glassmaking.Tint.FOREST : tint;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        fireButton.setMessage(Component.translatable(lit() ? "gui.halgventmod.hearth.extinguish" : "gui.halgventmod.hearth.ignite"));
        blowButton.active = hasPipe() && menu.get(GlassFurnaceBlockEntity.D_MOLTEN) >= form().cost()
                && menu.get(GlassFurnaceBlockEntity.D_TEMPERATURE) >= Glassmaking.WORK_TEMP;
        boolean annealed = false;
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            annealed |= menu.slots.get(GlassFurnaceMenu.LEHR_START + i).hasItem()
                    && menu.get(GlassFurnaceBlockEntity.D_ANNEAL + i) >= 100;
        }
        takeButton.active = annealed;
    }

    @Override
    protected void panels(GuiGraphics g) {
        section(g, 4, 4, 80, 104);
        plaque(g, Component.translatable("gui.halgventmod.glass.forms"), 8, 6, 76);
        section(g, 82, 4, 158, 104);
        plaque(g, Component.translatable("gui.halgventmod.glass.fire"), 86, 6, 154);
        section(g, 160, 4, 252, 104);
        plaque(g, Component.translatable("gui.halgventmod.glass.title"), 164, 6, 248);
    }

    /** Изделие под мышью в списке слева или −1. */
    private int formAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - FORMS_X + 2;
        double ry = mouseY - topPos - FORMS_Y + 2;
        if (rx < 0 || rx >= 72 || ry < 0) {
            return -1;
        }
        int i = (int) ry / FORM_ROW;
        return i < Glassmaking.Form.values().length ? i : -1;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        float temperature = menu.get(GlassFurnaceBlockEntity.D_TEMPERATURE);
        int molten = menu.get(GlassFurnaceBlockEntity.D_MOLTEN);

        // Что выдувать
        int hovered = formAt(mouseX, mouseY);
        for (Glassmaking.Form f : Glassmaking.Form.values()) {
            int fy = y + FORMS_Y + f.ordinal() * FORM_ROW;
            if (f == form()) {
                g.renderOutline(x + FORMS_X - 2, fy - 2, 72, FORM_ROW, SELECTED);
            } else if (f.ordinal() == hovered) {
                g.fill(x + FORMS_X - 1, fy - 1, x + FORMS_X + 69, fy + FORM_ROW - 3, 0x30FFFFFF);
            }
            ItemStack icon = Glassmaking.make(f, f.tinted() ? tint() : Glassmaking.Tint.FOREST, 2);
            g.renderItem(icon, x + FORMS_X, fy);
            smallFit(g, Component.translatable(f.langKey()), x + FORMS_X + 18, fy + 1, 50, f == form() ? TEXT : TEXT_DIM);
            small(g, Component.translatable("gui.halgventmod.glass.cost", f.cost()), x + FORMS_X + 18, fy + 9,
                    molten >= f.cost() ? GOOD : TEXT_DIM);
        }
        if (!hasPipe()) {
            smallFit(g, Component.translatable("gui.halgventmod.glass.no_pipe"), x + 8, y + 99, 70, BAD);
        }

        // Огонь
        thermometer(g, x + 90, y + 21, 6, 54, temperature, Glassmaking.MAX_TEMP + 50);
        mark(g, x + 90, y + 21, 6, 54, Glassmaking.WORK_TEMP, Glassmaking.MAX_TEMP + 50, 0xC0FFFFFF);
        mark(g, x + 90, y + 21, 6, 54, Glassmaking.MELT_TEMP, Glassmaking.MAX_TEMP + 50, 0xC0FFFFFF);
        g.drawString(font, Math.round(temperature) + "°", x + 102, y + 21, TEXT, true);
        Component state = temperature >= Glassmaking.MELT_TEMP ? Component.translatable("gui.halgventmod.glass.melting")
                : temperature >= Glassmaking.WORK_TEMP ? Component.translatable("gui.halgventmod.glass.workable")
                : Component.translatable("gui.halgventmod.glass.cold", Math.round(Glassmaking.MELT_TEMP));
        smallWrap(g, state, x + 102, y + 32, 52, y + 48, temperature >= Glassmaking.WORK_TEMP ? GOOD : WARN);
        // Единица горения — раз в два тика
        smallFit(g, Component.translatable(lit() ? "gui.halgventmod.hearth.burns" : "gui.halgventmod.hearth.out",
                time(menu.get(GlassFurnaceBlockEntity.D_REMAINING) * 10 * 2)), x + 102, y + 50, 52, lit() ? TEXT : TEXT_DIM);
        float burn = menu.get(GlassFurnaceBlockEntity.D_BURN_LEFT) / (float) Math.max(1, menu.get(GlassFurnaceBlockEntity.D_BURN_TOTAL));
        bar(g, x + 102, y + 60, 50, 3, burn, lit() ? 0xFFE08A2A : 0xFF6A5A48);
        smallFit(g, Component.translatable("gui.halgventmod.hearth.fuel"), x + 88, y + 72, 66, TEXT_DIM);

        // Стекломасса
        int px = x + 182;
        smallFit(g, Component.translatable("gui.halgventmod.glass.batch_1", menu.get(GlassFurnaceBlockEntity.D_SAND),
                menu.get(GlassFurnaceBlockEntity.D_POTASH)), px, y + 21, 68, TEXT);
        smallFit(g, Component.translatable("gui.halgventmod.glass.batch_2", menu.get(GlassFurnaceBlockEntity.D_FLUX),
                menu.get(GlassFurnaceBlockEntity.D_CULLET)), px, y + 29, 68, TEXT_DIM);
        fit(g, Component.translatable("gui.halgventmod.glass.molten", molten, Glassmaking.MAX_MOLTEN), x + 163, y + 40, 86,
                molten > 0 ? TEXT : TEXT_DIM);
        if (molten > 0) {
            smallFit(g, Component.translatable("gui.halgventmod.smith_work.quality", Component.translatable(
                    "gui.halgventmod.bench_work.grade_" + menu.get(GlassFurnaceBlockEntity.D_QUALITY))), x + 163, y + 50, 86, TEXT_DIM);
        }
        bar(g, x + 163, y + 58, 86, 2, menu.get(GlassFurnaceBlockEntity.D_MELT) / 100.0F, 0xFFE8862A);
        smallWrap(g, Component.translatable("gui.halgventmod.glass.tint", Component.translatable(tint().langKey())),
                px, y + 64, 68, y + 80, TEXT_DIM);
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            int sx = x + GlassFurnaceMenu.LEHR_X + i * 18;
            if (menu.slots.get(GlassFurnaceMenu.LEHR_START + i).hasItem()) {
                int done = menu.get(GlassFurnaceBlockEntity.D_ANNEAL + i);
                bar(g, sx, y + GlassFurnaceMenu.LEHR_Y + 18, 16, 2, done / 100.0F, done >= 100 ? GOOD : 0xFFC88A4A);
            }
        }
        smallFit(g, Component.translatable("gui.halgventmod.glass.lehr"), x + 236, y + GlassFurnaceMenu.LEHR_Y + 5, 14, TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = formAt(mouseX, mouseY);
        if (i >= 0 && button == 0) {
            click(GlassFurnaceMenu.BUTTON_FORM + i);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int i = formAt(mouseX, mouseY);
        if (i >= 0 && menu.getCarried().isEmpty()) {
            Glassmaking.Form f = Glassmaking.Form.byIndex(i);
            g.renderTooltip(font, Glassmaking.make(f, f.tinted() ? tint() : Glassmaking.Tint.FOREST, 2), mouseX, mouseY);
        }
    }
}
