package com.nrdhvnstd.halgventmod.client.sound;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.network.SoundEmitterEditPayload;
import com.nrdhvnstd.halgventmod.sound.SoundData;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlockEntity;
import com.nrdhvnstd.halgventmod.sound.SoundScene;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Настройка источника звука (администрация в творческом): сцена из присланных сервером и свой радиус
 * (пусто — из сцены, 1–64). Сохраняет сервер (SoundEmitterEditPayload). Сцены, которой нет, — предупреждение.
 */
public class SoundEmitterScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 222;
    private static final int PAD = 10;
    private static final int LIST_TOP = 34;
    private static final int ROW = 12;
    private static final int ROWS = 9;
    private static final int BUTTON_WIDTH = 100;

    private static final int COLOR_PANEL = 0xF0241C14;
    private static final int COLOR_FRAME = 0xFF6E6250;
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_DIM = 0xFFB8A888;
    private static final int COLOR_SELECTED = 0xFF6E6250;
    private static final int COLOR_HOVER = 0x406E6250;
    private static final int COLOR_LIST = 0x60000000;
    private static final int COLOR_WARNING = 0xFFE06050;

    private final BlockPos pos;
    private final List<String> scenes = new ArrayList<>(); // "" — без сцены
    private String scene;
    private final int initialRadius;
    private int scroll;
    private int left;
    private int top;
    private EditBox radiusBox;

    public SoundEmitterScreen(BlockPos pos, String scene, int radius) {
        super(Component.translatable("gui.halgventmod.sound_emitter.title"));
        this.pos = pos;
        this.scene = scene;
        this.initialRadius = radius;
        scenes.add("");
        for (ResourceLocation id : SoundData.clientScenes.keySet()) {
            scenes.add(id.toString());
        }
        int selected = scenes.indexOf(scene);
        scroll = Mth.clamp(selected - ROWS / 2, 0, maxScroll());
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String old = radiusBox != null ? radiusBox.getValue() : initialRadius > 0 ? Integer.toString(initialRadius) : "";
        radiusBox = new EditBox(font, left + PAD, top + LIST_TOP + ROWS * ROW + 34, 50, 18, Component.empty());
        radiusBox.setMaxLength(2);
        radiusBox.setFilter(text -> text.matches("\\d*"));
        radiusBox.setValue(old);
        addRenderableWidget(radiusBox);

        int buttonsY = top + HEIGHT - 28;
        int buttonsX = left + (WIDTH - 2 * BUTTON_WIDTH - 4) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(buttonsX, buttonsY, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(buttonsX + BUTTON_WIDTH + 4, buttonsY, BUTTON_WIDTH, 20).build());
    }

    private void save() {
        int radius = 0;
        if (!radiusBox.getValue().isEmpty()) {
            radius = Mth.clamp(Integer.parseInt(radiusBox.getValue()), 1, SoundEmitterBlockEntity.MAX_RADIUS);
        }
        PacketDistributor.sendToServer(new SoundEmitterEditPayload(pos, scene, radius));
        onClose();
    }

    private int maxScroll() {
        return Math.max(0, scenes.size() - ROWS);
    }

    private int rowAt(double mouseX, double mouseY) {
        int listTop = top + LIST_TOP;
        if (mouseX < left + PAD || mouseX >= left + WIDTH - PAD || mouseY < listTop || mouseY >= listTop + ROWS * ROW) {
            return -1;
        }
        int row = scroll + (int) ((mouseY - listTop) / ROW);
        return row < scenes.size() ? row : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            scene = scenes.get(row);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (rowAt(mouseX, mouseY) >= 0 || scenes.size() > ROWS) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        int x = left + PAD;
        int listTop = top + LIST_TOP;
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 8, COLOR_TEXT);
        graphics.drawString(font, Component.translatable("gui.halgventmod.sound_emitter.scene"), x, listTop - 11, COLOR_DIM, false);

        graphics.fill(x, listTop, left + WIDTH - PAD, listTop + ROWS * ROW, COLOR_LIST);
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS && scroll + i < scenes.size(); i++) {
            int index = scroll + i;
            String id = scenes.get(index);
            int y = listTop + i * ROW;
            if (id.equals(scene)) {
                graphics.fill(x, y, left + WIDTH - PAD, y + ROW, COLOR_SELECTED);
            } else if (index == hovered) {
                graphics.fill(x, y, left + WIDTH - PAD, y + ROW, COLOR_HOVER);
            }
            Component label = id.isEmpty() ? Component.translatable("gui.halgventmod.sound_emitter.no_scene")
                    : Component.literal(id);
            graphics.drawString(font, label, x + 3, y + 2, COLOR_TEXT, false);
        }
        if (scenes.size() > ROWS) {
            graphics.drawString(font, (scroll + 1) + "–" + Math.min(scenes.size(), scroll + ROWS) + " / " + scenes.size(),
                    x, listTop + ROWS * ROW + 2, COLOR_DIM, false);
        }

        // Сцены нет среди присланных — источник молчит
        int infoY = listTop + ROWS * ROW + 12;
        SoundScene chosen = scene.isEmpty() ? null : sceneOf(scene);
        if (!scene.isEmpty() && chosen == null) {
            graphics.drawString(font, Component.translatable("gui.halgventmod.sound_emitter.missing", scene),
                    x, infoY, COLOR_WARNING, false);
        }
        graphics.drawString(font, Component.translatable("gui.halgventmod.sound_emitter.radius",
                chosen != null ? chosen.radius() : "—"), x, radiusBox.getY() - 10, COLOR_DIM, false);
    }

    private static SoundScene sceneOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null ? SoundData.clientScenes.get(location) : null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
