package com.nrdhvnstd.halgventmod.client.map;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.PointEditPayload;
import com.nrdhvnstd.halgventmod.point.MapPoint;
import com.nrdhvnstd.halgventmod.point.MapPoints;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно точки появления у администрации: название, координаты («Где я стою» — взять свои) и описание,
 * которое новый игрок читает в окне создания персонажа. Границы у точки появления нет: персонаж встаёт
 * ровно в неё, а если там не встать — на поверхность над ней (CharacterCreation). Сохраняет и удаляет сервер.
 */
public class SpawnPointEditScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");

    private static final int WIDTH = 240;
    private static final int HEIGHT = 230;
    private static final int INNER = 12;
    private static final int COORD_WIDTH = 66;
    private static final int COORD_GAP = 9;
    private static final int COORDS_TOP = 70;
    private static final int HERE_TOP = 92;
    private static final int HERE_WIDTH = 120;
    private static final int DESCRIPTION_TOP = 128;
    private static final int DESCRIPTION_HEIGHT = 66;
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_DIM = 0xFFB8A888;

    private final Screen parent;
    private final MapPoint point; // черновик: id 0 — новая

    private int left;
    private int top;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private MultiLineEditBox descriptionBox;

    /** Новая точка появления в этом месте. */
    public static SpawnPointEditScreen create(Screen parent, int x, int y, int z) {
        MapPoint point = new MapPoint();
        point.kind = MapPoint.Kind.SPAWN;
        point.x = x;
        point.y = y;
        point.z = z;
        return new SpawnPointEditScreen(parent, point);
    }

    /** draft — копия существующей точки появления (или новая из create). */
    public SpawnPointEditScreen(Screen parent, MapPoint draft) {
        super(Component.translatable(draft.id == 0 ? "gui.halgventmod.spawn.new" : "gui.halgventmod.spawn.edit"));
        this.parent = parent;
        this.point = draft;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int x = left + INNER;
        int fieldWidth = WIDTH - 2 * INNER;

        // Пересоздание окна (смена размера) — значения берутся из прежних полей
        String name = nameBox != null ? nameBox.getValue() : point.name;
        nameBox = new EditBox(font, x, top + 34, fieldWidth, 18, Component.translatable("gui.halgventmod.waypoint.name"));
        nameBox.setMaxLength(MapPoint.NAME_MAX);
        nameBox.setValue(name);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        xBox = coordinate(x, xBox, point.x);
        yBox = coordinate(x + COORD_WIDTH + COORD_GAP, yBox, point.y);
        zBox = coordinate(x + 2 * (COORD_WIDTH + COORD_GAP), zBox, point.z);
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.spawn.here"), button -> standingHere())
                .bounds(x, top + HERE_TOP, HERE_WIDTH, 20).build());

        String description = descriptionBox != null ? descriptionBox.getValue() : point.description;
        descriptionBox = new MultiLineEditBox(font, x, top + DESCRIPTION_TOP, fieldWidth, DESCRIPTION_HEIGHT,
                Component.empty(), Component.translatable("gui.halgventmod.spawn.description"));
        descriptionBox.setCharacterLimit(MapPoint.DESCRIPTION_MAX);
        descriptionBox.setValue(description);
        addRenderableWidget(descriptionBox);

        int buttonsY = top + HEIGHT - 28;
        int buttonWidth = (fieldWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.waypoint.save"), button -> save())
                .bounds(x, buttonsY, buttonWidth, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.waypoint.delete"),
                        button -> delete())
                .bounds(x + buttonWidth + 4, buttonsY, buttonWidth, 20).build());
        delete.active = point.id != 0;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x + 2 * (buttonWidth + 4), buttonsY, buttonWidth, 20).build());
    }

    private EditBox coordinate(int x, @Nullable EditBox old, int value) {
        EditBox box = new EditBox(font, x, top + COORDS_TOP, COORD_WIDTH, 18, Component.empty());
        box.setMaxLength(9);
        box.setFilter(text -> text.matches("-?\\d*"));
        box.setValue(old != null ? old.getValue() : Integer.toString(value));
        addRenderableWidget(box);
        return box;
    }

    /** Координаты — там, где стоит администратор. */
    private void standingHere() {
        if (minecraft.player != null) {
            xBox.setValue(Integer.toString(minecraft.player.getBlockX()));
            yBox.setValue(Integer.toString(minecraft.player.getBlockY()));
            zBox.setValue(Integer.toString(minecraft.player.getBlockZ()));
        }
    }

    private void save() {
        String name = nameBox.getValue().strip();
        point.name = name.isEmpty() ? Component.translatable("gui.halgventmod.spawn.kind").getString() : name;
        point.x = parse(xBox, point.x);
        point.y = parse(yBox, point.y);
        point.z = parse(zBox, point.z);
        point.description = descriptionBox.getValue().strip();
        PacketDistributor.sendToServer(new PointEditPayload(point.id == 0 ? MapPoints.Action.CREATE
                : MapPoints.Action.UPDATE, point));
        onClose();
    }

    private void delete() {
        if (point.id != 0) {
            PacketDistributor.sendToServer(new PointEditPayload(MapPoints.Action.DELETE, point));
        }
        onClose();
    }

    private static int parse(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(PANEL, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left + INNER;
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 10, COLOR_TEXT);
        graphics.drawString(font, Component.translatable("gui.halgventmod.waypoint.name"), x, top + 24, COLOR_DIM, false);
        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < axes.length; i++) {
            graphics.drawString(font, axes[i], x + i * (COORD_WIDTH + COORD_GAP), top + COORDS_TOP - 10, COLOR_DIM, false);
        }
        graphics.drawString(font, Component.translatable("gui.halgventmod.spawn.description"), x,
                top + DESCRIPTION_TOP - 10, COLOR_DIM, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter сохраняет только из однострочных полей: в описании это перевод строки
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && getFocused() instanceof EditBox) {
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
