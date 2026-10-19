package com.nrdhvnstd.halgventmod.client.banner;

import com.nrdhvnstd.halgventmod.banner.BannerEntry;
import com.nrdhvnstd.halgventmod.network.BannerActionPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Правка знамени из реестра: название и описание (многострочное). Слева — само знамя.
 * Сохраняет сервер (BannerActionPayload.EDIT); пустое название не сохранить.
 */
public class BannerEditScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 170;
    private static final int PAD = 8;
    private static final int BANNER_WIDTH = 40;
    private static final int FIELDS_LEFT = PAD + BANNER_WIDTH + 10;
    private static final int DESCRIPTION_HEIGHT = 72;

    private static final int COLOR_PANEL = 0xF0241C14;
    private static final int COLOR_FRAME = 0xFF6E6250;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_DIM = 0xFFB8A888;

    private final Screen parent;
    private final BannerEntry entry;
    private int left;
    private int top;
    private EditBox nameBox;
    private MultiLineEditBox descriptionBox;
    private Button saveButton;

    public BannerEditScreen(Screen parent, BannerEntry entry) {
        super(Component.translatable("gui.halgventmod.banners.edit_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int x = left + FIELDS_LEFT;
        int fieldWidth = WIDTH - FIELDS_LEFT - PAD;

        String name = nameBox != null ? nameBox.getValue() : entry.name();
        nameBox = new EditBox(font, x, top + 34, fieldWidth, 18, Component.translatable("gui.halgventmod.banners.name"));
        nameBox.setMaxLength(BannerEntry.NAME_MAX);
        nameBox.setValue(name);
        nameBox.setResponder(value -> updateSave());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        String description = descriptionBox != null ? descriptionBox.getValue() : entry.description();
        descriptionBox = new MultiLineEditBox(font, x, top + 66, fieldWidth, DESCRIPTION_HEIGHT, Component.empty(),
                Component.translatable("gui.halgventmod.banners.description"));
        descriptionBox.setCharacterLimit(BannerEntry.DESCRIPTION_MAX);
        descriptionBox.setValue(description);
        addRenderableWidget(descriptionBox);

        int buttonsY = top + HEIGHT - 20 - PAD + 2;
        int buttonWidth = (fieldWidth - 4) / 2;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.waypoint.save"),
                        button -> save())
                .bounds(x, buttonsY, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x + buttonWidth + 4, buttonsY, buttonWidth, 18).build());
        updateSave();
    }

    private void updateSave() {
        if (saveButton != null) {
            saveButton.active = !nameBox.getValue().isBlank();
        }
    }

    private void save() {
        if (nameBox.getValue().isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new BannerActionPayload(BannerActionPayload.Action.EDIT, entry.id(),
                nameBox.getValue().strip(), descriptionBox.getValue().strip()));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, COLOR_PANEL);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, COLOR_FRAME);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + PAD, top + 8, COLOR_TITLE, false);
        graphics.drawString(font, entry.id(), left + WIDTH - PAD - font.width(entry.id()), top + 8, COLOR_DIM, false);
        BannerDraw.draw(graphics, entry, left + PAD, top + 24, BANNER_WIDTH);
        int x = left + FIELDS_LEFT;
        graphics.drawString(font, Component.translatable("gui.halgventmod.banners.name"), x, top + 24, COLOR_DIM, false);
        graphics.drawString(font, Component.translatable("gui.halgventmod.banners.description"), x, top + 56, COLOR_DIM, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
