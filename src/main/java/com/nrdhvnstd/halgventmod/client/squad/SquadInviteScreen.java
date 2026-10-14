package com.nrdhvnstd.halgventmod.client.squad;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.network.SquadActionPayload;
import com.nrdhvnstd.halgventmod.network.SquadActionPayload.Action;
import com.nrdhvnstd.halgventmod.network.SquadStatePayload;
import com.nrdhvnstd.halgventmod.squad.SquadClientState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * «Пригласить участников»: список игроков в сети без отряда. ЛКМ по нику выделяет игрока,
 * кнопка внизу «Пригласить в отряд» отправляет приглашение. Кому оно уже отправлено и ждёт ответа — помечены «приглашён».
 * Список присылает сервер и обновляет сам (вошёл, вышел, вступил в отряд).
 */
public class SquadInviteScreen extends Screen implements SquadClientState.Listener {
    private static final int WIDTH = 260;
    private static final int ROWS = 10;
    private static final int ROW = 14;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private int scroll;
    private int left;
    private int top;
    @Nullable
    private UUID selected;
    private Button inviteButton;
    @Nullable
    private Component error;

    public SquadInviteScreen(Screen parent) {
        super(Component.translatable("gui.halgventmod.squad.invite_members"));
        this.parent = parent;
    }

    private int panelHeight() {
        return SquadUi.TITLE_H + ROWS * ROW + SquadUi.INNER;
    }

    @Nullable
    private SquadStatePayload.PlayerView selectedPlayer() {
        for (SquadStatePayload.PlayerView player : SquadClientState.invitable) {
            if (player.id().equals(selected)) {
                return player;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        SquadClientState.listener = this;
        left = (width - WIDTH) / 2;
        top = Math.max(12, (height - panelHeight() - 2 * BUTTON_HEIGHT - 24) / 2);
        int y = top + panelHeight() + 16;
        inviteButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.squad.invite_to_squad"),
                button -> {
                    if (selected != null) {
                        SquadScreen.send(SquadActionPayload.target(Action.INVITE, selected));
                    }
                }).bounds((width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds((width - BUTTON_WIDTH) / 2, y + BUTTON_HEIGHT + 4, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        updateInviteButton();
    }

    private void updateInviteButton() {
        SquadStatePayload.PlayerView player = selectedPlayer();
        if (player == null) {
            selected = null;
        }
        inviteButton.active = player != null && !player.invited();
        inviteButton.setMessage(Component.translatable(player != null && player.invited()
                ? "gui.halgventmod.squad.invited" : "gui.halgventmod.squad.invite_to_squad"));
    }

    @Override
    public void squadStateChanged() {
        if (SquadClientState.squad == null || minecraft.player == null
                || !minecraft.player.getUUID().equals(SquadClientState.squad.leader())) {
            onClose(); // отряд распущен или лидерство передано
            return;
        }
        updateInviteButton();
    }

    @Override
    public void squadResult(Action action, @Nullable Component error) {
        if (action == Action.INVITE) {
            this.error = error;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        SquadUi.panel(graphics, font, left, top, WIDTH, panelHeight(), title);
        List<SquadStatePayload.PlayerView> players = SquadClientState.invitable;
        int y = top + SquadUi.TITLE_H;
        if (players.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.squad.nobody_to_invite"),
                    left + WIDTH / 2, y + 4, SquadUi.COLOR_DIM);
            return;
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, players.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < players.size(); i++) {
            SquadStatePayload.PlayerView player = players.get(scroll + i);
            int rowY = y + i * ROW;
            if (player.id().equals(selected)) {
                graphics.fill(left + 4, rowY - 3, left + WIDTH - 4, rowY - 3 + ROW, 0x50FFFFFF);
            } else if (SquadUi.inside(mouseX, mouseY, left + 4, rowY - 3, WIDTH - 8, ROW)) {
                graphics.fill(left + 4, rowY - 3, left + WIDTH - 4, rowY - 3 + ROW, SquadUi.COLOR_HOVER_ROW);
            }
            graphics.drawString(font, player.name(), left + SquadUi.INNER, rowY,
                    player.invited() ? SquadUi.COLOR_DIM : SquadUi.COLOR_TEXT, false);
            if (player.invited()) {
                Component mark = Component.translatable("gui.halgventmod.squad.invited_mark");
                graphics.drawString(font, mark, left + WIDTH - SquadUi.INNER - font.width(mark), rowY,
                        SquadUi.COLOR_DIM, false);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (error != null) {
            graphics.drawCenteredString(font, error, width / 2, top + panelHeight() + 4, SquadUi.COLOR_ERROR);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<SquadStatePayload.PlayerView> players = SquadClientState.invitable;
            int y = top + SquadUi.TITLE_H;
            for (int i = 0; i < ROWS && scroll + i < players.size(); i++) {
                if (SquadUi.inside(mouseX, mouseY, left + 4, y + i * ROW - 3, WIDTH - 8, ROW)) {
                    selected = players.get(scroll + i).id();
                    error = null;
                    updateInviteButton();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
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
