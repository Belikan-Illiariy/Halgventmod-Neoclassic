package com.nrdhvnstd.halgventmod.client.squad;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.network.SquadActionPayload;
import com.nrdhvnstd.halgventmod.network.SquadActionPayload.Action;
import com.nrdhvnstd.halgventmod.squad.SquadClientState;
import com.nrdhvnstd.halgventmod.squad.SquadService;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Редактор отряда: при создании — название и знамя, потом отдельно «Переименовать» (только название)
 * и «Изменить знамя» (только знамя).
 * Знамя — как на ткацком станке: основа и до 6 слоёв узоров. Выбираешь цвет, потом узор — добавляется слой этого цвета.
 * Щелчок по строке «Основа» или по слою в списке выделяет её: следующий цвет перекрасит выделенное. ✖ — убрать слой.
 * Окно закрывается, когда сервер ответит «получилось»; при отказе причина показывается внизу.
 */
public class SquadEditScreen extends Screen implements SquadClientState.Listener {
    private enum Mode { CREATE, RENAME, BANNER }

    private static final int WIDTH = 340;
    private static final int NAME_HEIGHT = 34;
    private static final int BANNER_PANEL_HEIGHT = 124;
    private static final int GAP = 12;
    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;

    private static final float PREVIEW_SCALE = 2.0F;    // 40 × 80
    private static final int SWATCH = 13;
    private static final int SWATCH_COLUMNS = 8;
    private static final int PATTERN_W = 13;
    private static final int PATTERN_H = 23;
    private static final int PATTERN_ROWS = 3;
    private static final float PATTERN_SCALE = 0.5F;    // 10 × 20
    private static final int LAYER_ROW = 12;

    private static final int NONE = -2;                 // ничего не выделено
    private static final int BASE = -1;                 // выделена основа

    private final Screen parent;
    private final Mode mode;
    private final String initialName;
    private DyeColor base;
    private final List<BannerPatternLayers.Layer> layers;
    private DyeColor brush = DyeColor.BLACK;
    private int selected = NONE;
    private int patternScroll;
    private List<Holder<BannerPattern>> patterns = List.of();

    @Nullable
    private EditBox nameBox;
    private Button submit;
    @Nullable
    private Component error;
    private boolean waiting;

    // Разметка, посчитанная в init()
    private int left;
    private int bannerTop;

    private SquadEditScreen(Screen parent, Mode mode, String name, DyeColor base, BannerPatternLayers patterns) {
        super(Component.translatable(switch (mode) {
            case CREATE -> "gui.halgventmod.squad.create";
            case RENAME -> "gui.halgventmod.squad.rename";
            case BANNER -> "gui.halgventmod.squad.edit_banner";
        }));
        this.parent = parent;
        this.mode = mode;
        this.initialName = name;
        this.base = base;
        this.layers = new ArrayList<>(patterns.layers());
    }

    public static SquadEditScreen create(Screen parent) {
        return new SquadEditScreen(parent, Mode.CREATE, "", DyeColor.WHITE, BannerPatternLayers.EMPTY);
    }

    public static SquadEditScreen rename(Screen parent, String name) {
        return new SquadEditScreen(parent, Mode.RENAME, name, DyeColor.WHITE, BannerPatternLayers.EMPTY);
    }

    public static SquadEditScreen banner(Screen parent, DyeColor base, BannerPatternLayers patterns) {
        return new SquadEditScreen(parent, Mode.BANNER, "", base, patterns);
    }

    private boolean hasName() {
        return mode != Mode.BANNER;
    }

    private boolean hasBanner() {
        return mode != Mode.RENAME;
    }

    @Override
    protected void init() {
        SquadClientState.listener = this;
        if (minecraft.level != null) {
            patterns = new ArrayList<>(minecraft.level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN)
                    .holders().toList());
        }
        int contentHeight = (hasName() ? NAME_HEIGHT + GAP : 0) + (hasBanner() ? BANNER_PANEL_HEIGHT + GAP : 0)
                + 12 + BUTTON_HEIGHT;
        left = (width - WIDTH) / 2;
        int top = Math.max(8, (height - contentHeight) / 2);
        int y = top;
        if (hasName()) {
            String value = nameBox != null ? nameBox.getValue() : initialName;
            nameBox = new EditBox(font, (width - 200) / 2, y + 12, 200, 18, Component.translatable("gui.halgventmod.squad.name"));
            nameBox.setMaxLength(SquadService.NAME_MAX);
            nameBox.setValue(value);
            nameBox.setResponder(text -> updateSubmit());
            addRenderableWidget(nameBox);
            setInitialFocus(nameBox);
            y += NAME_HEIGHT + GAP;
        }
        bannerTop = y;
        if (hasBanner()) {
            y += BANNER_PANEL_HEIGHT + GAP;
        }
        y += 12; // строка ошибки или подсказки
        Component submitLabel = Component.translatable(mode == Mode.CREATE ? "gui.halgventmod.squad.create"
                : "gui.halgventmod.squad.save");
        submit = addRenderableWidget(Button.builder(submitLabel, button -> submit())
                .bounds(width / 2 - BUTTON_WIDTH - 4, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 4, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        updateSubmit();
    }

    private void updateSubmit() {
        if (submit != null) {
            submit.active = !waiting && (!hasName() || nameBox != null && !nameBox.getValue().isBlank());
        }
    }

    private void submit() {
        BannerPatternLayers built = new BannerPatternLayers(List.copyOf(layers));
        String name = nameBox != null ? nameBox.getValue() : "";
        SquadScreen.send(switch (mode) {
            case CREATE -> SquadActionPayload.banner(Action.CREATE, name, base, built);
            case RENAME -> SquadActionPayload.text(Action.RENAME, name);
            case BANNER -> SquadActionPayload.banner(Action.SET_BANNER, "", base, built);
        });
        waiting = true;
        error = null;
        updateSubmit();
    }

    @Override
    public void squadResult(Action action, @Nullable Component error) {
        if (action != Action.CREATE && action != Action.RENAME && action != Action.SET_BANNER) {
            return;
        }
        waiting = false;
        if (error == null) {
            onClose();
        } else {
            this.error = error;
            updateSubmit();
        }
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

    // ===== Разметка редактора знамени =====

    private int previewX() {
        return left + SquadUi.INNER + 2;
    }

    private int paletteX() {
        return previewX() + Math.round(SquadUi.BANNER_W * PREVIEW_SCALE) + 12;
    }

    private int paletteY() {
        return bannerTop + SquadUi.TITLE_H;
    }

    private int patternsY() {
        return paletteY() + 2 * SWATCH + 6;
    }

    private int layersX() {
        return paletteX() + SWATCH_COLUMNS * SWATCH + 12;
    }

    private int layersWidth() {
        return left + WIDTH - SquadUi.INNER - layersX();
    }

    private int patternRows() {
        return (patterns.size() + SWATCH_COLUMNS - 1) / SWATCH_COLUMNS;
    }

    // ===== Рисование =====

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (hasName() && nameBox != null) {
            graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.squad.name"), width / 2,
                    nameBox.getY() - 12, SquadUi.COLOR_TEXT);
        }
        if (hasBanner()) {
            renderBannerEditor(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, SquadUi.COLOR_TEXT);
        int lineY = submit.getY() - 12;
        if (error != null) {
            graphics.drawCenteredString(font, error, width / 2, lineY, SquadUi.COLOR_ERROR);
        } else if (hasBanner()) {
            graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.squad.banner.hint"), width / 2,
                    lineY, SquadUi.COLOR_DIM);
        }
        if (hasBanner()) {
            renderPatternTooltip(graphics, mouseX, mouseY);
        }
    }

    private void renderBannerEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        SquadUi.panel(graphics, font, left, bannerTop, WIDTH, BANNER_PANEL_HEIGHT,
                Component.translatable("gui.halgventmod.squad.banner"));
        BannerPatternLayers built = new BannerPatternLayers(List.copyOf(layers));
        SquadUi.banner(graphics, previewX(), paletteY() + 2, PREVIEW_SCALE, base, built);

        // Палитра: 16 красителей, 2 ряда по 8; выбранный — в белой рамке
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length; i++) {
            int x = paletteX() + (i % SWATCH_COLUMNS) * SWATCH;
            int y = paletteY() + (i / SWATCH_COLUMNS) * SWATCH;
            int frame = colors[i] == brush ? 0xFFFFFFFF
                    : SquadUi.inside(mouseX, mouseY, x, y, SWATCH, SWATCH) ? 0xFFB0B0B0 : 0xFF202020;
            graphics.fill(x, y, x + SWATCH - 1, y + SWATCH - 1, frame);
            graphics.fill(x + 1, y + 1, x + SWATCH - 2, y + SWATCH - 2, 0xFF000000 | colors[i].getTextureDiffuseColor());
        }

        // Узоры: белый узор на сером, как на станке; прокрутка колесом
        boolean full = layers.size() >= SquadService.BANNER_MAX_LAYERS;
        patternScroll = Mth.clamp(patternScroll, 0, Math.max(0, patternRows() - PATTERN_ROWS));
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < SWATCH_COLUMNS; col++) {
                int index = (patternScroll + row) * SWATCH_COLUMNS + col;
                if (index >= patterns.size()) {
                    break;
                }
                int x = paletteX() + col * PATTERN_W;
                int y = patternsY() + row * PATTERN_H;
                boolean hovered = SquadUi.inside(mouseX, mouseY, x, y, PATTERN_W, PATTERN_H);
                graphics.fill(x, y, x + PATTERN_W - 1, y + PATTERN_H - 1, hovered && !full ? 0xFFB0B0B0 : 0xFF303030);
                SquadUi.banner(graphics, x + 1.5F, y + 1, PATTERN_SCALE, DyeColor.GRAY,
                        new BannerPatternLayers.Builder().add(patterns.get(index), DyeColor.WHITE).build());
                if (full) {
                    graphics.fill(x, y, x + PATTERN_W - 1, y + PATTERN_H - 1, 0x90000000);
                }
            }
        }
        if (patternRows() > PATTERN_ROWS) {
            // Полоса прокрутки справа от узоров
            int trackX = paletteX() + SWATCH_COLUMNS * PATTERN_W + 1;
            int trackH = PATTERN_ROWS * PATTERN_H;
            int thumbH = Math.max(8, trackH * PATTERN_ROWS / patternRows());
            int thumbY = patternsY() + (trackH - thumbH) * patternScroll / Math.max(1, patternRows() - PATTERN_ROWS);
            graphics.fill(trackX, patternsY(), trackX + 2, patternsY() + trackH, 0xFF303030);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFB8A888);
        }

        // Слои: основа и узоры сверху вниз; выделенное подсвечено; ✖ — убрать слой
        int x = layersX();
        int y = paletteY();
        graphics.drawString(font, Component.translatable("gui.halgventmod.squad.banner.layers", layers.size(),
                SquadService.BANNER_MAX_LAYERS), x, y, SquadUi.COLOR_DIM, false);
        y += LAYER_ROW;
        layerRow(graphics, x, y, BASE, Component.translatable("gui.halgventmod.squad.banner.base",
                Component.translatable("color.minecraft." + base.getName())), mouseX, mouseY);
        for (int i = 0; i < layers.size(); i++) {
            layerRow(graphics, x, y + (i + 1) * LAYER_ROW, i, layers.get(i).description(), mouseX, mouseY);
        }
    }

    private void layerRow(GuiGraphics graphics, int x, int y, int index, Component label, int mouseX, int mouseY) {
        int w = layersWidth();
        if (index == selected) {
            graphics.fill(x - 2, y - 2, x + w, y + LAYER_ROW - 2, 0x50FFFFFF);
        } else if (SquadUi.inside(mouseX, mouseY, x - 2, y - 2, w + 2, LAYER_ROW)) {
            graphics.fill(x - 2, y - 2, x + w, y + LAYER_ROW - 2, SquadUi.COLOR_HOVER_ROW);
        }
        int textWidth = index == BASE ? w : w - 12;
        graphics.drawString(font, SquadUi.fit(font, label.getString(), textWidth), x, y, SquadUi.COLOR_TEXT, false);
        if (index != BASE) {
            boolean overRemove = SquadUi.inside(mouseX, mouseY, x + w - 10, y - 2, 10, LAYER_ROW);
            graphics.drawString(font, "✖", x + w - 9, y, overRemove ? SquadUi.COLOR_DECLINE : SquadUi.COLOR_ICON, false);
        }
    }

    private void renderPatternTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = patternAt(mouseX, mouseY);
        if (index >= 0) {
            Component tooltip = layers.size() >= SquadService.BANNER_MAX_LAYERS
                    ? Component.translatable("squad.halgventmod.banner_too_many", SquadService.BANNER_MAX_LAYERS)
                    : new BannerPatternLayers.Layer(patterns.get(index), brush).description();
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    // ===== Мышь =====

    private int patternAt(double mouseX, double mouseY) {
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < SWATCH_COLUMNS; col++) {
                int index = (patternScroll + row) * SWATCH_COLUMNS + col;
                if (index < patterns.size() && SquadUi.inside(mouseX, mouseY, paletteX() + col * PATTERN_W,
                        patternsY() + row * PATTERN_H, PATTERN_W, PATTERN_H)) {
                    return index;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasBanner() && button == 0 && clickBanner(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickBanner(double mouseX, double mouseY) {
        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length; i++) {
            if (SquadUi.inside(mouseX, mouseY, paletteX() + (i % SWATCH_COLUMNS) * SWATCH,
                    paletteY() + (i / SWATCH_COLUMNS) * SWATCH, SWATCH, SWATCH)) {
                brush = colors[i];
                if (selected == BASE) {
                    base = brush;
                } else if (selected >= 0) {
                    layers.set(selected, new BannerPatternLayers.Layer(layers.get(selected).pattern(), brush));
                }
                return true;
            }
        }
        int pattern = patternAt(mouseX, mouseY);
        if (pattern >= 0) {
            if (layers.size() < SquadService.BANNER_MAX_LAYERS) {
                layers.add(new BannerPatternLayers.Layer(patterns.get(pattern), brush));
                selected = NONE;
            }
            return true;
        }
        int x = layersX();
        int w = layersWidth();
        for (int index = BASE; index < layers.size(); index++) {
            int y = paletteY() + LAYER_ROW + (index + 1) * LAYER_ROW;
            if (!SquadUi.inside(mouseX, mouseY, x - 2, y - 2, w + 2, LAYER_ROW)) {
                continue;
            }
            if (index != BASE && SquadUi.inside(mouseX, mouseY, x + w - 10, y - 2, 10, LAYER_ROW)) {
                layers.remove(index);
                selected = NONE;
            } else {
                selected = selected == index ? NONE : index;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hasBanner()) {
            patternScroll -= (int) Math.signum(scrollY);
            patternScroll = Mth.clamp(patternScroll, 0, Math.max(0, patternRows() - PATTERN_ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
