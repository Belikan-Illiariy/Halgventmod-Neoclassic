package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.menu.ArmorEquipSlot;
import com.nrdhvnstd.halgventmod.menu.GearEquipSlot;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.menu.StorageSlot;
import com.nrdhvnstd.halgventmod.weight.Encumbrance;
import com.nrdhvnstd.halgventmod.weight.ModWeights;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно инвентаря (концепт, docs/INVENTORY_SPEC.md): броня, фигурка, снаряжение, сетка сумок 9×3 (спина, П.1, П.2),
 * карманы со второй рукой и панель предмета справа; нагрузка — строкой над окном.
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

    // Строка нагрузки над окном: зазор до верхнего края окна
    private static final int LOAD_GAP = 2;

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
        // На маленьком экране (масштаб интерфейса 4 при 1080p) окно и строка нагрузки над ним не должны заезжать на вкладки
        topPos = Math.max(topPos, MenuTabs.TAB_HEIGHT + 2 + font.lineHeight + LOAD_GAP);
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
        renderLoad(graphics);
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

    // Подписей в окне нет: над хранилищем — тоже (сетка стоит вплотную под фигуркой, как в ванили),
    // какая это область, говорит подсказка над пустой ячейкой. Нагрузка — над окном (renderLoad)
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    // «8 / 40 кг ЛЕГКО» над окном у правого края, цветом ступени
    private void renderLoad(GuiGraphics graphics) {
        double kg = ModWeights.inventoryGrams(minecraft.player) / 1000.0;
        double capacity = Encumbrance.capacity(minecraft.player);
        Encumbrance.Tier tier = Encumbrance.tier(kg, capacity);
        int color = switch (tier) {
            case NONE -> 0xFFFFFF;       // белый
            case BURDEN -> 0xFFFF55;     // жёлтый
            case ENCUMBERED -> 0xFFAA00; // оранжевый
            case OVERLOADED -> 0xFF5555; // красный
        };
        Component value = Component.translatable("hud.halgventmod.carry", WeightText.kg(kg), WeightText.kg(capacity))
                .append(" ")
                .append(Component.translatable("gui.halgventmod.load." + tier.name().toLowerCase()));
        int x = leftPos + imageWidth - font.width(value);
        int y = topPos - font.lineHeight - LOAD_GAP;
        graphics.drawString(font, value, x, y, color, true);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Стиль Conquest Reforged: железная плашка, мшистые ячейки сундука, тёмные вставки под фигурку и панель предмета
        ConquestUi.window(graphics, x, y, x + imageWidth, y + imageHeight);
        ConquestUi.inset(graphics, x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM);
        ConquestUi.inset(graphics, x + PlayerArmorMenu.PANEL_LEFT, y + PlayerArmorMenu.PANEL_TOP,
                x + PlayerArmorMenu.PANEL_RIGHT, y + PlayerArmorMenu.PANEL_BOTTOM);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ConquestUi.slot(graphics, x + slot.x, y + slot.y, slot instanceof StorageSlot storage && !storage.isOpen());
            }
        }
        StorageSlotOverlay.drawDividers(graphics, this, x, y); // черта между спиной и подсумками
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                x + PlayerArmorMenu.FIGURE_LEFT, y + PlayerArmorMenu.FIGURE_TOP,
                x + PlayerArmorMenu.FIGURE_RIGHT, y + PlayerArmorMenu.FIGURE_BOTTOM,
                FIGURE_SCALE, FIGURE_Y_OFFSET, mouseX, mouseY, minecraft.player);
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
        // Строка признаков с качеством — с плашкой качества справа (QualityBadge)
        List<FormattedCharSequence> lines = new ArrayList<>();
        int badgeLine = -1;
        GearQuality.Grade badge = null;
        for (Component line : getTooltipFromContainerItem(stack)) {
            GearQuality.Grade grade = QualityBadge.gradeFor(line);
            if (grade != null) {
                badge = grade;
                badgeLine = lines.size();
            }
            List<FormattedCharSequence> wrapped = font.split(line, width);
            lines.addAll(wrapped.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : wrapped);
        }
        int y = iconY + ICON_SIZE + PANEL_PADDING;
        graphics.enableScissor(left, top, right, bottom);
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            if (y + font.lineHeight > topPos + BUTTONS_Y) {
                break;
            }
            graphics.drawString(font, line, left + PANEL_PADDING, y, 0xFFFFFF, true);
            if (i == badgeLine) {
                int text = font.width(line);
                QualityBadge.render(graphics, font, left + PANEL_PADDING + text + (text > 0 ? 4 : 0), y,
                        QualityBadge.label(stack, badge), badge);
            }
            y += font.lineHeight + (i == badgeLine ? 3 : 1);
        }
        graphics.disableScissor();
    }
}
