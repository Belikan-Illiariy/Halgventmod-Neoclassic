package com.nrdhvnstd.halgventmod.client.npc;

import com.nrdhvnstd.halgventmod.client.ConquestUi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Окно НПС (docs/INTERACTION_E_SPEC.md, 3.4): пока только раздел «Информация» — имя и профессия.
 * Диалоги и прочее — позже, по Геймдоку 12. Закрывается Esc.
 */
public class NpcInfoScreen extends Screen {
    private static final int WIDTH = 180;
    private static final int HEIGHT = 70;
    private static final int PADDING = 10;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_LABEL = 0xFFB8B0A0;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private final Component name;
    private final Component profession;

    private NpcInfoScreen(Component name, Component profession) {
        super(Component.translatable("gui.halgventmod.npc.info"));
        this.name = name;
        this.profession = profession;
    }

    /** NpcInfoPayload. */
    public static void open(Component name, Component profession) {
        Minecraft.getInstance().setScreen(new NpcInfoScreen(name, profession));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        ConquestUi.window(graphics, left, top, left + WIDTH, top + HEIGHT);
        int x = left + PADDING;
        int y = top + PADDING;
        graphics.drawString(font, title, x, y, COLOR_TITLE, true);
        y += font.lineHeight + 6;
        field(graphics, Component.translatable("gui.halgventmod.npc.name"), name, x, y);
        field(graphics, Component.translatable("gui.halgventmod.npc.profession"), profession, x, y + font.lineHeight + 4);
    }

    // «Имя:» серым, значение белым следом
    private void field(GuiGraphics graphics, Component label, Component value, int x, int y) {
        graphics.drawString(font, label, x, y, COLOR_LABEL, false);
        graphics.drawString(font, value, x + font.width(label) + 4, y, COLOR_TEXT, false);
    }
}
