package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.menu.ArmorEquipSlot;
import com.nrdhvnstd.halgventmod.menu.GearEquipSlot;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.weight.Encumbrance;
import com.nrdhvnstd.halgventmod.weight.ModWeights;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно инвентаря (концепт, docs/INVENTORY_SPEC.md): броня, фигурка, снаряжение, сетка сумок 9×3 (спина, П.1, П.2),
 * шкала нагрузки, карманы со второй рукой и панель предмета справа.
 * Вкладки (MenuTabs) — у верхнего края экрана; календарь и, в творческом режиме, «Каталог» — внизу панели предмета.
 * Фон пока — заглушка из рамок; финальную текстуру нарисует художник.
 */
public class ArmorInventoryScreen extends EffectRenderingInventoryScreen<PlayerArmorMenu> implements MenuTabScreen {
    private static final int FIGURE_SCALE = 30;
    private static final float FIGURE_Y_OFFSET = 0.0625F;

    // Внизу панели предмета: календарь, «Каталог» (только в творческом) и крестик очистки (только в творческом)
    private static final int BUTTONS_Y = PlayerArmorMenu.PANEL_BOTTOM - 22;
    public static final int CALENDAR_X = PlayerArmorMenu.PANEL_LEFT + 2;
    public static final int CALENDAR_Y = BUTTONS_Y;
    private static final int CATALOG_X = CALENDAR_X + 22;
    private static final int CATALOG_Y = BUTTONS_Y;
    private static final int CATALOG_WIDTH = 64;
    private static final int CATALOG_HEIGHT = 20;

    private static final int CLEAR_X = PlayerArmorMenu.PANEL_RIGHT - ClearInventoryButton.SIZE - 2;
    private static final int CLEAR_Y = BUTTONS_Y + 1;

    // Панель предмета: большая иконка и строки подсказки под ней
    private static final int PANEL_PADDING = 4;
    private static final int ICON_SCALE = 4;
    private static final int ICON_SIZE = 16 * ICON_SCALE;

    // Шкала нагрузки
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_GAP = 3; // от текста до полосы

    // Цвета заглушки (как у ванильных окон)
    private static final int WINDOW_FILL = 0xFFC6C6C6;
    private static final int WINDOW_DARK = 0xFF555555;
    private static final int WINDOW_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_LOCKED = 0xFF2B2B2B;
    private static final int PANEL_FILL = 0xFF1E1E1E;
    private static final int LABEL_COLOR = 0x404040;
    private static final int BAR_BACKGROUND = 0xFF3A3A3A;

    @Nullable
    private ClearInventoryButton clearButton; // только в творческом

    public ArmorInventoryScreen(PlayerArmorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PlayerArmorMenu.WIDTH;
        this.imageHeight = PlayerArmorMenu.HEIGHT;
    }

    @Override
    public MenuTab tab() {
        return MenuTab.INVENTORY;
    }

    @Override
    protected void init() {
        super.init();
        // На маленьком экране (масштаб интерфейса 4 при 1080p) окно не должно заезжать на вкладки
        topPos = Math.max(topPos, MenuTabs.TAB_HEIGHT + 2);
        for (MenuTabButton button : MenuTabs.createButtons(font, leftPos, imageWidth, MenuTab.INVENTORY)) {
            addRenderableWidget(button);
        }
        if (minecraft.gameMode != null && minecraft.gameMode.hasInfiniteItems()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.catalog"),
                            button -> InventoryOverride.openCatalog())
                    .bounds(leftPos + CATALOG_X, topPos + CATALOG_Y, CATALOG_WIDTH, CATALOG_HEIGHT)
                    .build());
            clearButton = addRenderableWidget(new ClearInventoryButton(menu, leftPos + CLEAR_X, topPos + CLEAR_Y));
        } else {
            clearButton = null;
        }
    }

    // Вещь перетащили на крестик и отпустили — удаляем её. Без этого игра выбросила бы её:
    // кнопка лежит за краем окна. Начатую раскладку по ячейкам (перетаскивание через ячейки) отменяем
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (clearButton != null && !menu.getCarried().isEmpty() && clearButton.isMouseOver(mouseX, mouseY)) {
            isQuickCrafting = false;
            quickCraftSlots.clear();
            clearButton.destroyCarried();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderItemPanel(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    // Подсказки вещей не нужны — всё показывает панель предмета.
    // Над пустой ячейкой брони или снаряжения — название ячейки, над пустой ячейкой хранилища — область
    // (спина, подсумок 1, подсумок 2), над закрытой — чем она открывается
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty() || hoveredSlot == null || hoveredSlot.hasItem()) {
            return;
        }
        Component hint = null;
        if (hoveredSlot instanceof ArmorEquipSlot slot) {
            hint = Component.translatable(slot.armorSlot().langKey());
        } else if (hoveredSlot instanceof GearEquipSlot slot) {
            hint = Component.translatable(slot.gearSlot().langKey());
        } else if (hoveredSlot instanceof StorageSlot slot) {
            hint = slot.hint();
        }
        if (hint != null) {
            graphics.renderTooltip(font, hint, mouseX, mouseY);
        }
    }

    // Над хранилищем подписей нет (сетка стоит вплотную под фигуркой, как в ванили) —
    // какая это область, говорит подсказка над пустой ячейкой. Здесь только шкала нагрузки
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        renderLoad(graphics);
    }

    // «Нагрузка  8/40 кг ЛЕГКО» и полоса; полная полоса = грузоподъёмность, сверх неё — полная, цветом ступени
    private void renderLoad(GuiGraphics graphics) {
        double kg = ModWeights.inventoryGrams(minecraft.player) / 1000.0;
        double capacity = Encumbrance.capacity(minecraft.player);
        Encumbrance.Tier tier = Encumbrance.tier(kg, capacity);
        int color = switch (tier) {
            case NONE -> 0xFF3F9F3F;       // зелёный
            case BURDEN -> 0xFFE0C020;     // жёлтый
            case ENCUMBERED -> 0xFFE08020; // оранжевый
            case OVERLOADED -> 0xFFD03030; // красный
        };
        int left = PlayerArmorMenu.ARMOR_X;
        int right = PlayerArmorMenu.LOAD_RIGHT;
        int y = PlayerArmorMenu.LOAD_Y;

        graphics.drawString(font, Component.translatable("gui.halgventmod.load"), left, y, LABEL_COLOR, false);
        Component value = Component.translatable("hud.halgventmod.carry", WeightText.kg(kg), WeightText.kg(capacity))
                .append(" ")
                .append(Component.translatable("gui.halgventmod.load." + tier.name().toLowerCase()));
        graphics.drawString(font, value, right - font.width(value), y, color, false);

        int barY = y + font.lineHeight + BAR_GAP;
        graphics.fill(left, barY, right, barY + BAR_HEIGHT, BAR_BACKGROUND);
        float ratio = capacity > 0 ? (float) Mth.clamp(kg / capacity, 0, 1) : 1;
        graphics.fill(left, barY, left + Math.round((right - left) * ratio), barY + BAR_HEIGHT, color);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        bevel(graphics, x, y, x + imageWidth, y + imageHeight, WINDOW_FILL, WINDOW_LIGHT, WINDOW_DARK);
        // Рамка фигурки и панели предмета
        graphics.fill(x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM, 0xFF000000);
        graphics.fill(x + PlayerArmorMenu.PANEL_LEFT, y + PlayerArmorMenu.PANEL_TOP,
                x + PlayerArmorMenu.PANEL_RIGHT, y + PlayerArmorMenu.PANEL_BOTTOM, PANEL_FILL);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                boolean locked = slot instanceof StorageSlot storage && !storage.isOpen();
                bevel(graphics, x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17,
                        locked ? SLOT_LOCKED : SLOT_FILL, SLOT_SHADOW, WINDOW_LIGHT);
            }
        }
        StorageSlotOverlay.drawDividers(graphics, this, x, y); // черта между спиной и подсумками
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM,
                FIGURE_SCALE, FIGURE_Y_OFFSET, mouseX, mouseY, minecraft.player);
    }

    // Прямоугольник с объёмной рамкой: светлая сверху-слева, тёмная снизу-справа (или наоборот — у ячеек)
    private static void bevel(GuiGraphics graphics, int left, int top, int right, int bottom,
                              int fill, int topLeft, int bottomRight) {
        graphics.fill(left, top, right, bottom, fill);
        graphics.fill(left, top, right - 1, top + 1, topLeft);
        graphics.fill(left, top, left + 1, bottom - 1, topLeft);
        graphics.fill(left + 1, bottom - 1, right, bottom, bottomRight);
        graphics.fill(right - 1, top + 1, right, bottom, bottomRight);
    }

    // Панель предмета: вещь под курсором, а если курсор ни над чем — вещь в руке
    private void renderItemPanel(GuiGraphics graphics) {
        ItemStack stack = hoveredSlot != null && hoveredSlot.hasItem() ? hoveredSlot.getItem() : minecraft.player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        int left = leftPos + PlayerArmorMenu.PANEL_LEFT;
        int right = leftPos + PlayerArmorMenu.PANEL_RIGHT;
        int top = topPos + PlayerArmorMenu.PANEL_TOP;
        int bottom = topPos + PlayerArmorMenu.PANEL_BOTTOM;
        int width = right - left - 2 * PANEL_PADDING;

        // Большая иконка: та же картинка, увеличенная без сглаживания
        int iconX = left + (right - left - ICON_SIZE) / 2;
        int iconY = top + PANEL_PADDING;
        graphics.pose().pushPose();
        graphics.pose().translate(iconX, iconY, 0);
        graphics.pose().scale(ICON_SCALE, ICON_SCALE, 1);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();

        // Имя, признаки, описание, характеристики — те же строки, что в обычной подсказке
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : getTooltipFromContainerItem(stack)) {
            List<FormattedCharSequence> wrapped = font.split(line, width);
            lines.addAll(wrapped.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : wrapped);
        }
        int y = iconY + ICON_SIZE + PANEL_PADDING;
        graphics.enableScissor(left, top, right, bottom);
        for (FormattedCharSequence line : lines) {
            if (y + font.lineHeight > topPos + BUTTONS_Y) {
                break;
            }
            graphics.drawString(font, line, left + PANEL_PADDING, y, 0xFFFFFF, true);
            y += font.lineHeight + 1;
        }
        graphics.disableScissor();
    }
}
