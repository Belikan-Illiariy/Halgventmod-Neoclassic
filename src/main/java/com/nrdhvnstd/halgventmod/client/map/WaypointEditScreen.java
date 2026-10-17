package com.nrdhvnstd.halgventmod.client.map;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/**
 * Окно новой метки или правки старой: название, значок, цвет флага, координаты, видимость на компасе.
 * Метки — личные, у игрока; окно точки карты у администрации — PointEditScreen.
 */
public class WaypointEditScreen extends Screen {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/panel");

    private static final int WIDTH = 228;
    private static final int HEIGHT = 244;
    private static final int INNER = 12;
    private static final int ICON_CELL = 20;
    private static final int ICONS_PER_ROW = 10;
    private static final int ICONS_TOP = 68;
    private static final int DYE_SWATCH = 11;
    private static final int DYE_STEP = 12;
    private static final int COORD_WIDTH = 62;
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_DIM = 0xFFB8A888;
    private static final int COLOR_SELECTED = 0xFFFFE8A0;
    private static final int COLOR_HOVER = 0xFF6E6250;
    private static final int COLOR_CELL = 0x60000000;

    // Значок и цвет последней сохранённой метки — для следующей новой
    private static MapIcon lastIcon = MapIcon.FLAG;
    private static DyeColor lastDye = DyeColor.RED;

    private final Screen parent;
    private final Waypoint editing; // null — новая метка
    private final int startX;
    private final int startY;
    private final int startZ;
    private MapIcon icon;
    private DyeColor dye;

    private int left;
    private int top;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private Checkbox compassBox;

    /** Новая метка в этой точке. */
    public static WaypointEditScreen create(Screen parent, int x, int y, int z) {
        return new WaypointEditScreen(parent, null, x, y, z, lastIcon, lastDye);
    }

    /** Правка существующей метки. */
    public static WaypointEditScreen edit(Screen parent, Waypoint waypoint) {
        return new WaypointEditScreen(parent, waypoint, waypoint.x, waypoint.y, waypoint.z, waypoint.icon(), waypoint.dye());
    }

    private WaypointEditScreen(Screen parent, Waypoint editing, int x, int y, int z, MapIcon icon, DyeColor dye) {
        super(Component.translatable(editing == null ? "gui.halgventmod.waypoint.new" : "gui.halgventmod.waypoint.edit"));
        this.parent = parent;
        this.editing = editing;
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.icon = icon;
        this.dye = dye;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int x = left + INNER;
        int fieldWidth = WIDTH - 2 * INNER;

        String name = nameBox != null ? nameBox.getValue() : editing != null ? editing.name : "";
        nameBox = new EditBox(font, x, top + 34, fieldWidth, 18, Component.translatable("gui.halgventmod.waypoint.name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(name);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        int coordsY = top + 170;
        xBox = coordinate(x, coordsY, xBox, startX);
        yBox = coordinate(x + COORD_WIDTH + 9, coordsY, yBox, startY);
        zBox = coordinate(x + 2 * (COORD_WIDTH + 9), coordsY, zBox, startZ);

        boolean onCompass = compassBox != null ? compassBox.selected() : editing != null && editing.compass;
        compassBox = addRenderableWidget(Checkbox.builder(Component.translatable("gui.halgventmod.waypoint.compass"), font)
                .pos(x, top + 194)
                .selected(onCompass)
                .build());

        int buttonsY = top + HEIGHT - 28;
        int buttonWidth = (fieldWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.waypoint.save"), button -> save())
                .bounds(x, buttonsY, buttonWidth, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.waypoint.delete"),
                        button -> delete())
                .bounds(x + buttonWidth + 4, buttonsY, buttonWidth, 20).build());
        delete.active = editing != null;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x + 2 * (buttonWidth + 4), buttonsY, buttonWidth, 20).build());
    }

    private EditBox coordinate(int x, int y, EditBox old, int value) {
        EditBox box = new EditBox(font, x, y, COORD_WIDTH, 18, Component.empty());
        box.setMaxLength(9);
        box.setFilter(text -> text.matches("-?\\d*"));
        box.setValue(old != null ? old.getValue() : Integer.toString(value));
        addRenderableWidget(box);
        return box;
    }

    private void save() {
        String name = nameBox.getValue().strip();
        if (name.isEmpty()) {
            name = icon.title().getString();
        }
        int x = parse(xBox, startX);
        int y = parse(yBox, startY);
        int z = parse(zBox, startZ);
        lastIcon = icon;
        lastDye = dye;
        if (editing == null) {
            Waypoint waypoint = new Waypoint(name, x, y, z, icon, dye);
            waypoint.compass = compassBox.selected();
            Waypoints.add(waypoint);
        } else {
            editing.compass = compassBox.selected();
            editing.name = name;
            editing.x = x;
            editing.y = y;
            editing.z = z;
            editing.icon = icon.id();
            editing.dye = dye.getSerializedName();
            Waypoints.changed();
        }
        onClose();
    }

    private void delete() {
        if (editing != null) {
            Waypoints.remove(editing);
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

    // ===== Значки: сетка; цвета флага: ряд квадратиков красителей =====

    private int iconX(int index) {
        return left + INNER + 2 + index % ICONS_PER_ROW * ICON_CELL;
    }

    private int iconY(int index) {
        return top + ICONS_TOP + index / ICONS_PER_ROW * ICON_CELL;
    }

    private int dyeX(int index) {
        return left + INNER + 6 + index * DYE_STEP;
    }

    private int dyeY() {
        return top + 142;
    }

    private MapIcon iconAt(double mouseX, double mouseY) {
        MapIcon[] icons = MapIcon.values();
        for (int i = 0; i < icons.length; i++) {
            if (mouseX >= iconX(i) && mouseX < iconX(i) + ICON_CELL && mouseY >= iconY(i) && mouseY < iconY(i) + ICON_CELL) {
                return icons[i];
            }
        }
        return null;
    }

    private DyeColor dyeAt(double mouseX, double mouseY) {
        if (icon != MapIcon.FLAG) {
            return null;
        }
        for (DyeColor each : DyeColor.values()) {
            int x = dyeX(each.getId());
            if (mouseX >= x && mouseX < x + DYE_SWATCH && mouseY >= dyeY() && mouseY < dyeY() + DYE_SWATCH) {
                return each;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MapIcon clickedIcon = iconAt(mouseX, mouseY);
        if (clickedIcon != null) {
            icon = clickedIcon;
            return true;
        }
        DyeColor clickedDye = dyeAt(mouseX, mouseY);
        if (clickedDye != null) {
            dye = clickedDye;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        graphics.drawString(font, Component.translatable("gui.halgventmod.waypoint.icon", icon.title()),
                x, top + 58, COLOR_DIM, false);

        MapIcon hoveredIcon = iconAt(mouseX, mouseY);
        MapIcon[] icons = MapIcon.values();
        for (int i = 0; i < icons.length; i++) {
            int ix = iconX(i);
            int iy = iconY(i);
            int frame = icons[i] == icon ? COLOR_SELECTED : icons[i] == hoveredIcon ? COLOR_HOVER : 0;
            if (frame != 0) {
                graphics.fill(ix, iy, ix + ICON_CELL, iy + ICON_CELL, frame);
            }
            graphics.fill(ix + 1, iy + 1, ix + ICON_CELL - 1, iy + ICON_CELL - 1, COLOR_CELL);
            MapIcon.draw(graphics, icons[i].cell(dye), ix + 2, iy + 2, 16);
        }

        if (icon == MapIcon.FLAG) {
            graphics.drawString(font, Component.translatable("gui.halgventmod.waypoint.flag_color",
                    Component.translatable("color.minecraft." + dye.getName())), x, top + 132, COLOR_DIM, false);
            for (DyeColor each : DyeColor.values()) {
                int sx = dyeX(each.getId());
                int sy = dyeY();
                graphics.fill(sx - 1, sy - 1, sx + DYE_SWATCH + 1, sy + DYE_SWATCH + 1,
                        each == dye ? 0xFFFFFFFF : 0xFF000000);
                graphics.fill(sx, sy, sx + DYE_SWATCH, sy + DYE_SWATCH, 0xFF000000 | each.getTextureDiffuseColor());
            }
        }

        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < axes.length; i++) {
            graphics.drawString(font, axes[i], x + i * (COORD_WIDTH + 9), top + 160, COLOR_DIM, false);
        }
        if (hoveredIcon != null) {
            graphics.renderTooltip(font, hoveredIcon.title(), mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
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
