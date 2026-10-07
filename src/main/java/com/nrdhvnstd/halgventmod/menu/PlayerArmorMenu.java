package com.nrdhvnstd.halgventmod.menu;

import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

import com.mojang.datafixers.util.Pair;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Инвентарь игрока вместо ванильного: рюкзак, хотбар, вторая рука и 12 ячеек брони.
 * Открывается сервером по OpenInventoryPayload. Справа от фигурки — пусто (место под будущий крафт).
 */
public class PlayerArmorMenu extends AbstractContainerMenu {
    // ===== Раскладка окна (в пикселях от левого верхнего угла) =====
    public static final int WIDTH = 202;
    public static final int HEIGHT = 166;
    public static final int SLOT_STEP = 18;
    // Вторая рука — левее хотбара, отделена перегородкой
    public static final int OFFHAND_X = 8;
    public static final int SEPARATOR_X = 29;    // перегородка между второй рукой и хотбаром
    public static final int INVENTORY_X = 34;    // левый край рюкзака, хотбара и сетки брони
    // Броня — сетка 4×3: голова, туловище, ноги (по строке на часть тела, порядок как в ArmorSlot)
    public static final int ARMOR_COLUMNS = 4;
    public static final int ARMOR_X = INVENTORY_X;
    public static final int ARMOR_Y = 8;
    public static final int BACKPACK_Y = 84;
    public static final int HOTBAR_Y = 142;
    public static final int OFFHAND_Y = HOTBAR_Y;
    public static final int FIGURE_LEFT = 107;   // фигурка — справа от сетки, снизу упирается в рюкзак
    public static final int FIGURE_RIGHT = 157;
    public static final int FIGURE_TOP = 7;
    public static final int FIGURE_BOTTOM = 83;
    // Правее фигурки окно пустое

    // ===== Номера ячеек в меню =====
    public static final int BACKPACK_START = 0;  // 27 ячеек рюкзака
    public static final int HOTBAR_START = 27;   // 9 ячеек хотбара
    public static final int OFFHAND = 36;
    public static final int ARMOR_START = 37;    // 12 ячеек брони в порядке ArmorSlot
    public static final int ARMOR_END = ARMOR_START + ArmorSlot.COUNT;
    private static final int INVENTORY_END = OFFHAND; // рюкзак + хотбар

    private final Player owner;

    // Конструктор для клиента (MenuType) и сервера
    public PlayerArmorMenu(int containerId, Inventory inventory) {
        super(ModMenus.PLAYER_INVENTORY.get(), containerId);
        this.owner = inventory.player;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * SLOT_STEP, BACKPACK_Y + row * SLOT_STEP));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * SLOT_STEP, HOTBAR_Y));
        }
        addSlot(new OffhandSlot(inventory, owner));

        ArmorInventory armor = owner.getData(ModAttachments.ARMOR);
        for (ArmorSlot slot : ArmorSlot.values()) {
            int col = slot.ordinal() % ARMOR_COLUMNS;
            int row = slot.ordinal() / ARMOR_COLUMNS;
            addSlot(new ArmorEquipSlot(armor, slot, ARMOR_X + col * SLOT_STEP, ARMOR_Y + row * SLOT_STEP));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // Клик по ячейке брони вещью с курсора или обмен с хотбаром цифрой: если правило не пускает —
    // сообщение над хотбаром (сам запрет делает ArmorEquipSlot.mayPlace)
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= ARMOR_START && slotId < ARMOR_END) {
            ItemStack incoming = switch (clickType) {
                case PICKUP -> getCarried();
                case SWAP -> player.getInventory().getItem(button);
                default -> ItemStack.EMPTY;
            };
            ArmorEquipSlot slot = (ArmorEquipSlot) slots.get(slotId);
            if (ModArmor.slotOf(incoming) == slot.armorSlot()) {
                Component refusal = slot.refusal(incoming);
                if (refusal != null) {
                    ArmorRules.tell(player, refusal);
                }
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    // Shift+клик: броня — в свою ячейку; снятая броня и вторая рука — в рюкзак;
    // остальное — между рюкзаком и хотбаром
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        ArmorSlot armorSlot = ModArmor.slotOf(stack);

        if (index >= ARMOR_START || index == OFFHAND) {
            if (!moveItemStackTo(stack, BACKPACK_START, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (armorSlot != null && !slots.get(ARMOR_START + armorSlot.ordinal()).hasItem()) {
            int target = ARMOR_START + armorSlot.ordinal();
            Component refusal = ((ArmorEquipSlot) slots.get(target)).refusal(stack);
            if (refusal != null) {
                ArmorRules.tell(player, refusal);
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < HOTBAR_START) {
            if (!moveItemStackTo(stack, HOTBAR_START, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, BACKPACK_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, original);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    // Вторая рука — как в ванильном инвентаре
    private static class OffhandSlot extends Slot {
        private final Player owner;

        OffhandSlot(Inventory inventory, Player owner) {
            super(inventory, Inventory.SLOT_OFFHAND, OFFHAND_X, OFFHAND_Y);
            this.owner = owner;
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            owner.onEquipItem(EquipmentSlot.OFFHAND, oldStack, newStack);
            super.setByPlayer(newStack, oldStack);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
        }
    }
}
