package com.nrdhvnstd.halgventmod.client.squad;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.nrdhvnstd.halgventmod.network.SquadActionPayload;
import com.nrdhvnstd.halgventmod.network.SquadActionPayload.Action;
import com.nrdhvnstd.halgventmod.network.SquadStatePayload;
import com.nrdhvnstd.halgventmod.squad.SquadClientState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Звания, которым видны товарищи на карте в режиме «Избранные звания» (ПКМ лидера по значку карты).
 * В списке — «Участник» и все звания участников отряда (и отмеченные раньше, даже если их больше ни у кого нет).
 * Щелчок по строке отмечает или снимает звание. Лидеру товарищи видны всегда.
 */
public class SquadMapRanksScreen extends Screen implements SquadClientState.Listener {
    private static final int WIDTH = 240;
    private static final int ROW = 14;
    private static final int MAX_ROWS = 10;

    private final Screen parent;
    private int left;
    private int top;
    private List<String> ranks = List.of(); // "" — «Участник»

    public SquadMapRanksScreen(Screen parent) {
        super(Component.translatable("gui.halgventmod.squad.map.ranks_title"));
        this.parent = parent;
    }

    private int panelHeight() {
        return SquadUi.TITLE_H + Math.max(1, Math.min(MAX_ROWS, ranks.size())) * ROW + SquadUi.INNER + 12;
    }

    @Override
    protected void init() {
        SquadClientState.listener = this;
        SquadStatePayload.SquadView squad = SquadClientState.squad;
        Set<String> found = new LinkedHashSet<>();
        found.add("");
        if (squad != null) {
            for (SquadStatePayload.MemberView member : squad.members()) {
                if (!member.id().equals(squad.leader())) {
                    found.add(member.rank());
                }
            }
            found.addAll(squad.mapRanks());
        }
        ranks = new ArrayList<>(found);
        left = (width - WIDTH) / 2;
        top = Math.max(12, (height - panelHeight() - 30) / 2);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - 200) / 2, top + panelHeight() + 8, 200, 20).build());
    }

    @Override
    public void squadStateChanged() {
        SquadStatePayload.SquadView squad = SquadClientState.squad;
        if (squad == null || minecraft.player == null || !squad.leader().equals(minecraft.player.getUUID())) {
            onClose();
            return;
        }
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        SquadUi.panel(graphics, font, left, top, WIDTH, panelHeight(), title);
        SquadStatePayload.SquadView squad = SquadClientState.squad;
        int y = top + SquadUi.TITLE_H;
        for (int i = 0; i < Math.min(MAX_ROWS, ranks.size()); i++) {
            String rank = ranks.get(i);
            boolean included = squad != null && squad.mapRanks().contains(rank);
            int rowY = y + i * ROW;
            if (SquadUi.inside(mouseX, mouseY, left + 4, rowY - 3, WIDTH - 8, ROW)) {
                graphics.fill(left + 4, rowY - 3, left + WIDTH - 4, rowY - 3 + ROW, SquadUi.COLOR_HOVER_ROW);
            }
            graphics.drawString(font, included ? "✔" : "·", left + SquadUi.INNER, rowY,
                    included ? SquadUi.COLOR_ACCEPT : SquadUi.COLOR_ICON, false);
            Component name = rank.isEmpty() ? Component.translatable("gui.halgventmod.squad.rank.member")
                    : Component.literal(rank);
            graphics.drawString(font, name, left + SquadUi.INNER + 14, rowY,
                    included ? SquadUi.COLOR_TEXT : SquadUi.COLOR_DIM, false);
        }
        graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.squad.map.ranks_hint"),
                left + WIDTH / 2, top + panelHeight() - SquadUi.INNER - 10, SquadUi.COLOR_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SquadStatePayload.SquadView squad = SquadClientState.squad;
        if (button == 0 && squad != null) {
            int y = top + SquadUi.TITLE_H;
            for (int i = 0; i < Math.min(MAX_ROWS, ranks.size()); i++) {
                if (SquadUi.inside(mouseX, mouseY, left + 4, y + i * ROW - 3, WIDTH - 8, ROW)) {
                    String rank = ranks.get(i);
                    SquadScreen.send(SquadActionPayload.textFlag(Action.MAP_RANK, rank, !squad.mapRanks().contains(rank)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (SquadClientState.listener == this) {
            SquadClientState.listener = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
