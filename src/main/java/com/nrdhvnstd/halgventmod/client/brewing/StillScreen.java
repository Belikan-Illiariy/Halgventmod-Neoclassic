package com.nrdhvnstd.halgventmod.client.brewing;

import com.nrdhvnstd.halgventmod.brewing.StillBlockEntity;
import com.nrdhvnstd.halgventmod.client.StationScreen;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.menu.StillMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Окно перегонного куба ({@link StillMenu}). Слева — огонь очага под кубом (дрова, жар, «Разжечь» / «Затушить»);
 * без очага — откуда жар. Справа — куб: сырьё в ячейку, сколько залито, полоса перегонки, сколько порций до крепкого;
 * пустые склянки — в свою ячейку, крепкое наливается в соседнюю само. «Слить» — забрать не перегнанное.
 */
public class StillScreen extends StationScreen<StillMenu> {
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private Button fireButton;
    private Button drainButton;

    public StillScreen(StillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, StillMenu.WIDTH, StillMenu.HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        fireButton = addRenderableWidget(Button.builder(Component.empty(), button -> click(lit()
                ? StillMenu.BUTTON_EXTINGUISH : StillMenu.BUTTON_IGNITE)).bounds(leftPos + 8, topPos + 110, 88, 16).build());
        drainButton = button(Component.translatable("gui.halgventmod.still.drain"), 104, 110, 88, StillMenu.BUTTON_DRAIN);
        containerTick();
    }

    private boolean lit() {
        return menu.fire(HearthBlockEntity.D_LIT) != 0;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        fireButton.visible = menu.hasHearth();
        fireButton.setMessage(Component.translatable(lit() ? "gui.halgventmod.hearth.extinguish" : "gui.halgventmod.hearth.ignite"));
        drainButton.active = menu.still(StillBlockEntity.D_INPUTS) > 0;
    }

    @Override
    protected void panels(GuiGraphics g) {
        firePanel(g);
        section(g, 100, 4, 196, 104);
        plaque(g, Component.translatable("gui.halgventmod.still.title"), 104, 6, 192);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        frame(g);
        int x = leftPos;
        int y = topPos;
        int heat = menu.still(StillBlockEntity.D_HEAT);
        if (menu.hasHearth()) {
            float temperature = menu.fire(HearthBlockEntity.D_TEMPERATURE);
            int fireHeat = menu.fire(HearthBlockEntity.D_HEAT);
            fireSection(g, temperature, HearthBlockEntity.TARGETS[3] + 50, Component.literal(Math.round(temperature) + "°"),
                    Component.translatable("gui.halgventmod.hearth.heat_" + fireHeat), fireHeat == 0 ? TEXT_DIM : WARN, lit(),
                    menu.fire(HearthBlockEntity.D_REMAINING) * 10 * 2, menu.fire(HearthBlockEntity.D_BURN_LEFT),
                    menu.fire(HearthBlockEntity.D_BURN_TOTAL), HearthBlockEntity.HEAT_1, HearthBlockEntity.HEAT_2,
                    HearthBlockEntity.HEAT_3);
        } else {
            smallWrap(g, Component.translatable(heat > 0 ? "gui.halgventmod.still.source_heat" : "gui.halgventmod.still.source_none",
                    heat), x + 8, y + 22, 88, y + 100, heat > 0 ? TEXT : BAD);
        }

        // Куб
        int px = x + 104;
        int inputs = menu.still(StillBlockEntity.D_INPUTS);
        small(g, Component.translatable("gui.halgventmod.still.in"), px + 28, y + 23, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.still.poured", inputs, StillBlockEntity.CAPACITY), px + 28, y + 31,
                inputs > 0 ? TEXT : TEXT_DIM);
        bar(g, px + 1, y + 45, 86, 3, menu.still(StillBlockEntity.D_PROGRESS) / (float) StillBlockEntity.DISTILL_TIME,
                heat >= 3 ? 0xFFE0604A : 0xFF8FB0D0);
        Component state = heat <= 0 ? Component.translatable("gui.halgventmod.still.cold")
                : heat >= 3 ? Component.translatable("gui.halgventmod.still.hot")
                : Component.translatable("gui.halgventmod.still.working", menu.still(StillBlockEntity.D_USED), StillMenu.ratio());
        smallWrap(g, state, px, y + 50, 92, y + 60, heat <= 0 ? TEXT_DIM : heat >= 3 ? BAD : TEXT);

        g.blitSprite(ARROW, x + StillMenu.BOTTLES_X + 22, y + StillMenu.BOTTLES_Y, 24, 16);
        small(g, Component.translatable("gui.halgventmod.still.bottles"), px, y + StillMenu.BOTTLES_Y + 19, TEXT_DIM);
        small(g, Component.translatable("gui.halgventmod.still.spirit", menu.still(StillBlockEntity.D_SPIRITS)),
                x + 150, y + StillMenu.BOTTLES_Y + 28, menu.still(StillBlockEntity.D_SPIRITS) > 0 ? GOOD : TEXT_DIM);
    }
}
