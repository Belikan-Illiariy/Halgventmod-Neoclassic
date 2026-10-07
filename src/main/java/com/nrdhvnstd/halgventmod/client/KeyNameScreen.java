package com.nrdhvnstd.halgventmod.client;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.network.RenameKeyPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Окошко подписи ключа: поле ввода, «Готово» и «Отмена». Пустое название убирает подпись. */
public class KeyNameScreen extends Screen {
    private final String initial;
    private EditBox nameBox;

    public KeyNameScreen(String initial) {
        super(Component.translatable("lock.halgventmod.name_screen"));
        this.initial = initial;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 20;
        nameBox = new EditBox(font, x, y, 200, 20, title);
        nameBox.setMaxLength(RenameKeyPayload.MAX_LENGTH);
        nameBox.setValue(initial);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> confirm())
                .bounds(x, y + 26, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x + 102, y + 26, 98, 20).build());
    }

    private void confirm() {
        PacketDistributor.sendToServer(new RenameKeyPayload(nameBox.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 36, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}