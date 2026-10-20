package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModMenus;
import com.nrdhvnstd.halgventmod.storage.StorageArea;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно стойки для брони: 12 ячеек брони стойки (сетка 4×3, как в инвентаре), фигурка стойки,
 * под ними — хранилище, карманы и вторая рука игрока. Раскладка и номера ячеек — как в PlayerArmorMenu,
 * только без снаряжения и панели предмета. Открывается Shift+ПКМ по стойке (ArmorStandEvents).
 */
public class ArmorStandMenu extends AbstractContainerMenu {
    public static final int WIDTH = PlayerArmorMenu.POCKETS_X + 9 * PlayerArmorMenu.SLOT_STEP + 8;
    public static final int HEIGHT = PlayerArmorMenu.HEIGHT;
    private static final double REACH_BUFFER = 4.0; // как у ванильных окон: дальше досягаемости на 4 блока — закрыть

    private final ArmorStand stand;
    private final ArmorInventory armor;

    // Клиент: стойка — по номеру сущности из пакета открытия
    public static ArmorStandMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        Entity entity = inventory.player.level().getEntity(buf.readVarInt());
        if (!(entity instanceof ArmorStand stand)) {
            throw new IllegalStateException("Armor stand for the menu is not loaded on the client");
        }
        return new ArmorStandMenu(containerId, inventory, stand);
    }

    public ArmorStandMenu(int containerId, Inventory inventory, ArmorStand stand) {
        super(ModMenus.ARMOR_STAND.get(), containerId);
        this.stand = stand;
        this.armor = stand.getData(ModAttachments.ARMOR);
        Player owner = inventory.player;

        for (int index = 0; index < StorageArea.INDEX_COUNT; index++) {
            int column = index % StorageArea.COLUMNS;
            int row = index / StorageArea.COLUMNS;
            addSlot(new StorageSlot(owner, index, PlayerArmorMenu.STORAGE_X + column * PlayerArmorMenu.SLOT_STEP,
                    PlayerArmorMenu.STORAGE_Y + row * PlayerArmorMenu.SLOT_STEP));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PlayerArmorMenu.POCKETS_X + col * PlayerArmorMenu.SLOT_STEP,
                    PlayerArmorMenu.POCKETS_Y));
        }
        addSlot(new PlayerArmorMenu.OffhandSlot(inventory, owner));

        for (ArmorSlot slot : ArmorSlot.values()) {
            int col = slot.ordinal() % PlayerArmorMenu.ARMOR_COLUMNS;
            int row = slot.ordinal() / PlayerArmorMenu.ARMOR_COLUMNS;
            addSlot(new ArmorEquipSlot(armor, slot, stand, PlayerArmorMenu.ARMOR_X + col * PlayerArmorMenu.SLOT_STEP,
                    PlayerArmorMenu.TOP + row * PlayerArmorMenu.SLOT_STEP));
        }
    }

    public ArmorStand stand() {
        return stand;
    }

    @Override
    public boolean stillValid(Player player) {
        return stand.isAlive() && player.canInteractWithEntity(stand, REACH_BUFFER);
    }

    // Изменения брони стойки видят и другие игроки — рассылаем вложение вместе с обычной синхронизацией окна
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!stand.level().isClientSide && armor.consumeDirty()) {
            stand.syncData(ModAttachments.ARMOR);
        }
    }

    // Клик по ячейке брони: если правило не пускает — сообщение над хотбаром (сам запрет делают mayPlace / mayPickup)
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof ArmorEquipSlot armorSlot) {
            Component refusal = refusal(armorSlot, button, clickType, player);
            if (refusal != null) {
                ArmorRules.tell(player, refusal);
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Nullable
    private Component refusal(ArmorEquipSlot slot, int button, ClickType clickType, Player player) {
        ItemStack incoming = switch (clickType) {
            case PICKUP -> getCarried();
            case SWAP -> player.getInventory().getItem(button);
            default -> ItemStack.EMPTY;
        };
        boolean takesOff = switch (clickType) {
            case PICKUP, QUICK_MOVE, SWAP, THROW -> true;
            default -> false;
        };
        if (takesOff && slot.hasItem() && slot.takeOffRefusal() != null) {
            return slot.takeOffRefusal();
        }
        return ModArmor.slotOf(incoming) == slot.armorSlot() ? slot.refusal(incoming) : null;
    }

    // Shift+клик: броня игрока — на стойку в свою ячейку; снятое со стойки — в карманы, затем в подсумки и на спину;
    // остальное — между карманами и сумками, как в инвентаре
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index >= PlayerArmorMenu.ARMOR_START) {
            moved = moveItemStackTo(stack, PlayerArmorMenu.POCKETS_START, PlayerArmorMenu.OFFHAND, false);
            if (!stack.isEmpty()) {
                moved |= moveToStorage(stack);
            }
        } else {
            Slot equip = equipTarget(player, stack);
            if (equip != null) {
                moved = moveItemStackTo(stack, equip.index, equip.index + 1, false);
            } else if (index >= PlayerArmorMenu.POCKETS_START && index < PlayerArmorMenu.OFFHAND) {
                moved = moveToStorage(stack);
            } else {
                moved = moveItemStackTo(stack, PlayerArmorMenu.POCKETS_START, PlayerArmorMenu.OFFHAND, false);
            }
        }
        if (!moved) {
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

    // Свободная ячейка брони стойки, куда эту вещь можно надеть, или null
    @Nullable
    private Slot equipTarget(Player player, ItemStack stack) {
        ArmorSlot armorSlot = ModArmor.slotOf(stack);
        if (armorSlot == null) {
            return null;
        }
        ArmorEquipSlot target = (ArmorEquipSlot) slots.get(PlayerArmorMenu.ARMOR_START + armorSlot.ordinal());
        if (target.hasItem()) {
            return null;
        }
        Component refusal = target.refusal(stack);
        if (refusal != null) {
            ArmorRules.tell(player, refusal);
            return null;
        }
        return target;
    }

    // Как в PlayerArmorMenu: подсумок 1, подсумок 2, спина; сначала дополнить такие же стопки, потом свободные ячейки
    private boolean moveToStorage(ItemStack stack) {
        boolean moved = false;
        for (boolean topUp : new boolean[] {true, false}) {
            for (StorageArea area : StorageArea.FILL_ORDER) {
                for (int local = 0; local < area.size() && !stack.isEmpty(); local++) {
                    int index = PlayerArmorMenu.STORAGE_START + area.gridIndex(local);
                    if (slots.get(index).hasItem() == topUp) {
                        moved |= moveItemStackTo(stack, index, index + 1, false);
                    }
                }
            }
        }
        return moved;
    }
}
