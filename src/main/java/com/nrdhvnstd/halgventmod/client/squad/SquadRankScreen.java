package com.nrdhvnstd.halgventmod.client.squad;

import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.network.SquadActionPayload;
import com.nrdhvnstd.halgventmod.network.SquadActionPayload.Action;
import com.nrdhvnstd.halgventmod.squad.SquadClientState;
import com.nrdhvnstd.halgventmod.squad.SquadService;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** «Изменить звание»: звание участника — свободный текст; пустое — звание по умолчанию («Участник»). */
public class SquadRankScreen extends Screen implements SquadClientState.Listener {
    private static final int BOX_WIDTH = 200;

    private final Screen parent;
    private final UUID member;
    private final String memberName;
    private final String rank;
    private EditBox box;
    @Nullable
    private Component error;

    public SquadRankScreen(Screen parent, UUID member, String memberName, String rank) {
        super(Component.translatable("gui.halgventmod.squad.menu.rank"));
        this.parent = parent;
        this.member = member;
        this.memberName = memberName;
        this.rank = rank;
    }

    @Override
    protected void init() {
        SquadClientState.listener = this;
        int y = height / 2 - 20;
        String value = box != null ? box.getValue() : rank;
        box = new EditBox(font, (width - BOX_WIDTH) / 2, y, BOX_WIDTH, 18, title);
        box.setMaxLength(SquadService.RANK_MAX);
        box.setValue(value);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                button -> SquadScreen.send(SquadActionPayload.text(Action.SET_RANK, member, box.getValue())))
                .bounds(width / 2 - 104, y + 40, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 4, y + 40, 100, 20).build());
    }

    @Override
    public void squadResult(Action action, @Nullable Component error) {
        if (action != Action.SET_RANK) {
            return;
        }
        if (error == null) {
            onClose();
        } else {
            this.error = error;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.squad.rank_of", memberName),
                width / 2, box.getY() - 14, SquadUi.COLOR_TEXT);
        Component line = error != null ? error : Component.translatable("gui.halgventmod.squad.rank.hint");
        graphics.drawCenteredString(font, line, width / 2, box.getY() + 24,
                error != null ? SquadUi.COLOR_ERROR : SquadUi.COLOR_DIM);
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
