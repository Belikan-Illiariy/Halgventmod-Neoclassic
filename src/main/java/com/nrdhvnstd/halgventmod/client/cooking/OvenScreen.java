package com.nrdhvnstd.halgventmod.client.cooking;

import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.cooking.OvenBlockEntity;
import com.nrdhvnstd.halgventmod.menu.OvenMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно печи ({@link OvenMenu}): слева жар (до полного — прогрев, отметка — с какого печётся), сколько ещё
 * гореть, полено, ячейки дров, «Разжечь» / «Затушить»; справа — три места: что печётся, полоса пропечённости
 * (зелёная зона — в самый раз) и сколько ждать. Вынутое из ячейки — то, что успело испечься.
 */
public class OvenScreen extends StationScreen<OvenMenu> {
    private Button fireButton;

    public OvenScreen(OvenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, OvenMenu.WIDTH, OvenMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        fireButton = addRenderableWidget(Button.builder(Component.empty(), button -> click(lit()
                ? OvenMenu.BUTTON_EXTINGUISH : OvenMenu.BUTTON_IGNITE)).bounds(leftPos + 8, topPos + 110, 88, 16).build());
        containerTick();
    }

    private boolean lit() {
        return menu.get(OvenBlockEntity.D_LIT) != 0;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        fireButton.setMessage(Component.translatable(lit() ? "gui.halgventmod.hearth.extinguish" : "gui.halgventmod.hearth.ignite"));
    }

    @Override
    protected void panels(GuiGraphics g) {
        firePanel(g);
        section(g, 100, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.oven.inside"), 104, 6, 192);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        float temperature = menu.get(OvenBlockEntity.D_TEMPERATURE) / 10.0F; // жар 0–100

        boolean ready = temperature >= OvenBlockEntity.MIN_TEMPERATURE;
        Component state = ready ? Component.translatable("gui.halgventmod.oven.hot")
                : Component.translatable("gui.halgventmod.oven.warming",
                        Math.round(100 * temperature / OvenBlockEntity.MIN_TEMPERATURE));
        // Единица горения — раз в два тика
        fireSection(g, temperature, 100, Component.literal(Math.round(temperature * OvenBlockEntity.DEGREES) + "°"), state,
                ready ? GOOD : WARN, lit(), menu.get(OvenBlockEntity.D_REMAINING) * 10 * 2,
                menu.get(OvenBlockEntity.D_BURN_LEFT), menu.get(OvenBlockEntity.D_BURN_TOTAL), OvenBlockEntity.MIN_TEMPERATURE);

        // Что печётся
        for (int i = 0; i < OvenBlockEntity.SLOTS; i++) {
            Slot slot = menu.slots.get(OvenMenu.BAKE_START + i);
            int bx = x + OvenMenu.BAKE_X + 20;
            int by = y + OvenMenu.BAKE_Y + i * OvenMenu.BAKE_STEP;
            if (!slot.hasItem()) {
                small(g, Component.translatable("gui.halgventmod.oven.empty_place"), bx, by + 5, TEXT_DIM);
                continue;
            }
            float done = menu.get(OvenBlockEntity.D_PROGRESS + i) / 100.0F;
            bake(g, bx, by, done, temperature, slot.getItem());
        }
    }

    /** Полоса пропечённости до «горелого»: зелёная зона — в самый раз; под ней — что сейчас и сколько ждать. */
    private void bake(GuiGraphics g, int x, int y, float done, float temperature, ItemStack raw) {
        int width = 58;
        float max = OvenBlockEntity.BURNT;
        bar(g, x, y + 2, width, 4, done / max, done >= OvenBlockEntity.BURNT ? 0xFF2A2420
                : done >= OvenBlockEntity.OVERBAKED ? BAD : done >= 1.0F ? GOOD : WARN);
        int z0 = x + Math.round(width * 1.0F / max);
        int z1 = x + Math.round(width * OvenBlockEntity.OVERBAKED / max);
        g.fill(z0, y + 7, z1, y + 8, 0xFF3E7A2E);
        Component line;
        int color;
        if (done >= OvenBlockEntity.BURNT) {
            line = Component.translatable("gui.halgventmod.oven.burnt");
            color = BAD;
        } else if (done >= OvenBlockEntity.OVERBAKED) {
            line = Component.translatable("gui.halgventmod.oven.over");
            color = BAD;
        } else if (done >= 1.0F) {
            line = Component.translatable("gui.halgventmod.oven.ready");
            color = GOOD;
        } else if (temperature < OvenBlockEntity.MIN_TEMPERATURE) {
            line = Component.translatable("gui.halgventmod.oven.cold");
            color = TEXT_DIM;
        } else {
            int ticks = Math.round((1.0F - done) * OvenBlockEntity.BAKE_TIME * 100 / temperature);
            line = Component.translatable("gui.halgventmod.oven.left", time(ticks));
            color = TEXT;
        }
        small(g, line, x, y + 10, color);
    }
}
