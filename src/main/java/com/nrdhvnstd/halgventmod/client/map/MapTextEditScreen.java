package com.nrdhvnstd.halgventmod.client.map;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Окно новой надписи на карте или правки старой: текст, размер, цвет и как она будет выглядеть. */
public class MapTextEditScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");

    private static final int WIDTH = 228;
    private static final int HEIGHT = 196;
    private static final int INNER = 12;
    private static final int SWATCH = 16;
    private static final int PREVIEW_TOP = 120;
    private static final int PREVIEW_HEIGHT = 40;
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_DIM = 0xFFB8A888;
    private static final int COLOR_PREVIEW = 0x60000000;

    // Размер на экране и цвет последней сохранённой надписи — для следующей новой
    private static float lastScreenScale = 2.0F;

    // Размер на экране, которым правит ползунок: от четверти до 12 обычных шрифтов
    private static final float MIN_SCREEN_SCALE = 0.25F;
    private static final float MAX_SCREEN_SCALE = 12.0F;
    private static int lastColor = MapScreen.INKS[0];

    private final Screen parent;
    private final MapText editing; // null — новая надпись
    private final int x;
    private final int z;
    private final float zoom;   // масштаб карты, при котором открыли окно: размер правится «как видно сейчас»
    private float screenScale; // размер на экране; на карту записывается screenScale / zoom
    private final float rotation; // поворот и изгиб правятся рамкой на карте; здесь — только для предпросмотра
    private final float arc;
    private int color;

    private int left;
    private int top;
    private EditBox textBox;

    /** Новая надпись серединой в этой точке мира. */
    public static MapTextEditScreen create(Screen parent, int x, int z, float zoom) {
        return new MapTextEditScreen(parent, null, x, z, zoom, lastScreenScale, 0, 0, lastColor);
    }

    public static MapTextEditScreen edit(Screen parent, MapText text, float zoom) {
        return new MapTextEditScreen(parent, text, text.x, text.z, zoom, text.screenScale(zoom), text.rotation, text.arc, text.color);
    }

    private MapTextEditScreen(Screen parent, MapText editing, int x, int z, float zoom, float screenScale,
                              float rotation, float arc, int color) {
        super(Component.translatable(editing == null ? "gui.halgventmod.map.text.new" : "gui.halgventmod.map.text.edit"));
        this.parent = parent;
        this.editing = editing;
        this.x = x;
        this.z = z;
        this.zoom = zoom;
        this.screenScale = screenScale;
        this.rotation = rotation;
        this.arc = arc;
        this.color = color;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int fieldX = left + INNER;
        int fieldWidth = WIDTH - 2 * INNER;

        String text = textBox != null ? textBox.getValue() : editing != null ? editing.text : "";
        textBox = new EditBox(font, fieldX, top + 34, fieldWidth, 18, Component.translatable("gui.halgventmod.map.text.text"));
        textBox.setMaxLength(MapText.MAX_LENGTH);
        textBox.setValue(text);
        addRenderableWidget(textBox);
        setInitialFocus(textBox);

        // Размер на экране при нынешнем масштабе карты — шагами в четверть шрифта, подпись — в процентах обычного
        addRenderableWidget(new MapWidgets.IntSlider(fieldX, top + 58, fieldWidth, 16,
                Math.round(MIN_SCREEN_SCALE * 4), Math.round(MAX_SCREEN_SCALE * 4), Math.round(screenScale * 4),
                value -> screenScale = value / 4.0F,
                value -> Component.translatable("gui.halgventmod.map.text.size", value * 25 + "%")));

        for (int i = 0; i < MapScreen.INKS.length; i++) {
            int ink = MapScreen.INKS[i];
            addRenderableWidget(new MapWidgets.SwatchButton(fieldX + i * (SWATCH + 4), top + 94, SWATCH, ink,
                    () -> color == ink, () -> color = ink));
        }

        int buttonsY = top + HEIGHT - 28;
        int buttonWidth = (fieldWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.waypoint.save"), button -> save())
                .bounds(fieldX, buttonsY, buttonWidth, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.waypoint.delete"),
                        button -> delete())
                .bounds(fieldX + buttonWidth + 4, buttonsY, buttonWidth, 20).build());
        delete.active = editing != null;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(fieldX + 2 * (buttonWidth + 4), buttonsY, buttonWidth, 20).build());
    }

    private void save() {
        String text = textBox.getValue().strip();
        if (text.isEmpty()) {
            return; // пустую надпись не сохраняем
        }
        if (editing == null) {
            MapTexts.add(new MapText(text, x, z, mapScale(), color));
        } else {
            editing.text = text;
            editing.scale = mapScale();
            editing.color = color;
            MapTexts.changed();
        }
        lastScreenScale = Mth.clamp(screenScale, MIN_SCREEN_SCALE, MAX_SCREEN_SCALE);
        lastColor = color;
        onClose();
    }

    // Размер на карте: блоков на пиксель шрифта
    private float mapScale() {
        return Mth.clamp(screenScale / zoom, MapText.MIN_SCALE, MapText.MAX_SCALE);
    }

    private void delete() {
        if (editing != null) {
            MapTexts.remove(editing);
        }
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(PANEL, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int fieldX = left + INNER;
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 10, COLOR_TEXT);
        graphics.drawString(font, Component.translatable("gui.halgventmod.map.text.text"), fieldX, top + 24, COLOR_DIM, false);
        graphics.drawString(font, Component.translatable("gui.halgventmod.map.text.color"), fieldX, top + 83, COLOR_DIM, false);

        // Так надпись будет выглядеть на карте; длинная крупная — обрезается рамкой
        int previewBottom = top + PREVIEW_TOP + PREVIEW_HEIGHT;
        graphics.fill(fieldX, top + PREVIEW_TOP, left + WIDTH - INNER, previewBottom, COLOR_PREVIEW);
        String text = textBox.getValue().strip();
        if (!text.isEmpty()) {
            graphics.enableScissor(fieldX, top + PREVIEW_TOP, left + WIDTH - INNER, previewBottom);
            MapText.draw(graphics, font, text, color, screenScale, rotation, arc, left + WIDTH / 2.0F,
                    top + PREVIEW_TOP + PREVIEW_HEIGHT / 2.0F);
            graphics.flush();
            graphics.disableScissor();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
