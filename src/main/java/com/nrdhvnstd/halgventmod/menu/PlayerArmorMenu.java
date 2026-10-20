package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearRules;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.gear.ModGear;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModMenus;
import com.nrdhvnstd.halgventmod.storage.Quiver;
import com.nrdhvnstd.halgventmod.storage.StorageArea;

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
 * Инвентарь игрока вместо ванильного (концепт инвентаря, docs/INVENTORY_SPEC.md):
 * броня 4×3, фигурка, снаряжение 3×3, хранилище 9×3 на месте бывшего рюкзака
 * (спина 3×5, подсумок 3×2, подсумок 3×2), карманы (= хотбар) со второй рукой
 * и панель предмета справа. Нагрузка — строкой над окном (ArmorInventoryScreen).
 * Открывается сервером по OpenInventoryPayload.
 */
public class PlayerArmorMenu extends AbstractContainerMenu {
    // ===== Раскладка окна (в пикселях от левого верхнего угла; координаты — у предмета, рамка на 1 больше) =====
    public static final int WIDTH = 328;
    public static final int HEIGHT = 168;        // карманы внизу + отступ 8, как у ванильного окна
    public static final int SLOT_STEP = 18;
    public static final int TOP = 8;             // верхний ряд ячеек
    // Броня — сетка 4×3: голова, туловище, ноги (по строке на часть тела, порядок как в ArmorSlot)
    public static final int ARMOR_COLUMNS = 4;
    public static final int ARMOR_X = 8;
    public static final int FIGURE_LEFT = 84;    // фигурка в рамке справа от брони
    public static final int FIGURE_RIGHT = 134;
    public static final int FIGURE_TOP = TOP - 1;
    public static final int FIGURE_BOTTOM = TOP + 3 * SLOT_STEP + 16;
    public static final int GEAR_X = 138;        // снаряжение 3×3 справа от фигурки
    // Хранилище — сетка 9×3 сразу под фигуркой, как ванильный рюкзак (84 от верха)
    public static final int STORAGE_X = 30;
    public static final int STORAGE_Y = FIGURE_BOTTOM + 6;
    // Карманы и вторая рука — под хранилищем с ванильным зазором 4
    public static final int OFFHAND_X = 8;
    public static final int POCKETS_X = 30;
    public static final int POCKETS_Y = STORAGE_Y + 3 * SLOT_STEP + 4;
    // Панель выбранного предмета — справа
    public static final int PANEL_LEFT = 200;
    public static final int PANEL_RIGHT = 320;
    public static final int PANEL_TOP = 8;
    public static final int PANEL_BOTTOM = HEIGHT - 8;

    // ===== Номера ячеек в меню =====
    public static final int STORAGE_START = 0;   // 27 ячеек хранилища в порядке сетки, как бывший рюкзак
    public static final int POCKETS_START = STORAGE_START + StorageArea.INDEX_COUNT;
    public static final int OFFHAND = POCKETS_START + 9;
    public static final int ARMOR_START = OFFHAND + 1;
    public static final int ARMOR_END = ARMOR_START + ArmorSlot.COUNT;
    public static final int GEAR_START = ARMOR_END;
    public static final int GEAR_END = GEAR_START + GearSlot.COUNT;

    private final Player owner;
    private final GearInventory gear;

    // Конструктор для клиента (MenuType) и сервера
    public PlayerArmorMenu(int containerId, Inventory inventory) {
        super(ModMenus.PLAYER_INVENTORY.get(), containerId);
        this.owner = inventory.player;
        this.gear = owner.getData(ModAttachments.GEAR);

        for (int index = 0; index < StorageArea.INDEX_COUNT; index++) {
            int column = index % StorageArea.COLUMNS;
            int row = index / StorageArea.COLUMNS;
            addSlot(new StorageSlot(owner, index, STORAGE_X + column * SLOT_STEP, STORAGE_Y + row * SLOT_STEP));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, POCKETS_X + col * SLOT_STEP, POCKETS_Y));
        }
        addSlot(new OffhandSlot(inventory, owner));

        ArmorInventory armor = owner.getData(ModAttachments.ARMOR);
        for (ArmorSlot slot : ArmorSlot.values()) {
            int col = slot.ordinal() % ARMOR_COLUMNS;
            int row = slot.ordinal() / ARMOR_COLUMNS;
            addSlot(new ArmorEquipSlot(armor, slot, owner, ARMOR_X + col * SLOT_STEP, TOP + row * SLOT_STEP));
        }
        for (GearSlot slot : GearSlot.values()) {
            int col = slot.ordinal() % GearSlot.COLUMNS;
            int row = slot.ordinal() / GearSlot.COLUMNS;
            addSlot(new GearEquipSlot(gear, slot, owner, GEAR_X + col * SLOT_STEP, TOP + row * SLOT_STEP));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // Клик по ячейке брони или снаряжения: если правило не пускает — сообщение над хотбаром
    // (сам запрет делают mayPlace / mayPickup).
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            if (clickType == ClickType.PICKUP && slot instanceof GearEquipSlot gearSlot
                    && gearSlot.gearSlot() == GearSlot.QUIVER && slot.hasItem() && quiverClick(button, player)) {
                return;
            }
            Component refusal = refusal(slot, button, clickType, player);
            if (refusal != null) {
                ArmorRules.tell(player, refusal);
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    // Надетый колчан: стрелы с курсора — в колчан; ПКМ пустым курсором — вынуть стрелы. true — клик обработан
    private boolean quiverClick(int button, Player player) {
        ItemStack carried = getCarried();
        if (Quiver.accepts(carried)) {
            Quiver.insert(player, carried); // другие стрелы не лезут — клик всё равно наш, колчан не подменится
            return true;
        }
        if (carried.isEmpty() && button == 1 && !Quiver.arrows(player).isEmpty()) {
            setCarried(Quiver.takeAll(player));
            return true;
        }
        return false;
    }

    @Nullable
    private Component refusal(Slot slot, int button, ClickType clickType, Player player) {
        ItemStack incoming = switch (clickType) {
            case PICKUP -> getCarried();
            case SWAP -> player.getInventory().getItem(button);
            default -> ItemStack.EMPTY;
        };
        boolean takesOff = switch (clickType) {
            case PICKUP, QUICK_MOVE, SWAP, THROW -> true;
            default -> false;
        };
        if (slot instanceof ArmorEquipSlot armorSlot) {
            if (takesOff && armorSlot.hasItem() && armorSlot.takeOffRefusal() != null) {
                return armorSlot.takeOffRefusal();
            }
            return ModArmor.slotOf(incoming) == armorSlot.armorSlot() ? armorSlot.refusal(incoming) : null;
        }
        if (slot instanceof GearEquipSlot gearSlot) {
            if (takesOff && gearSlot.hasItem() && gearSlot.takeOffRefusal() != null) {
                return gearSlot.takeOffRefusal();
            }
            return ModGear.typeOf(incoming) == gearSlot.gearSlot().type() ? gearSlot.refusal(incoming) : null;
        }
        return null;
    }

    // Shift+клик: стрелы — в колчан; броня и снаряжение — в свою ячейку; снятое, вторая рука и сумки — в карманы;
    // из карманов — в подсумки, затем на спину
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean fromPockets = index >= POCKETS_START && index < OFFHAND;

        if (fromPockets) {
            boolean moved = Quiver.insert(player, stack); // стрелы — сначала в колчан
            Slot equip = stack.isEmpty() ? null : equipTarget(player, stack);
            if (equip != null) {
                moved |= moveItemStackTo(stack, equip.index, equip.index + 1, false);
            } else if (!stack.isEmpty()) {
                moved |= moveToStorage(stack);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else if (index < POCKETS_START) {
            if (!moveItemStackTo(stack, POCKETS_START, OFFHAND, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = moveItemStackTo(stack, POCKETS_START, OFFHAND, false);
            if (!stack.isEmpty()) {
                moved |= moveToStorage(stack);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
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

    // Подсумок 1, подсумок 2, спина: сначала дополнить такие же стопки, потом свободные ячейки.
    // Ячейки области в меню идут не подряд (сетка по строкам), поэтому — по одной
    private boolean moveToStorage(ItemStack stack) {
        boolean moved = false;
        for (boolean topUp : new boolean[] {true, false}) {
            for (StorageArea area : StorageArea.FILL_ORDER) {
                for (int local = 0; local < area.size() && !stack.isEmpty(); local++) {
                    int index = STORAGE_START + area.gridIndex(local);
                    if (slots.get(index).hasItem() == topUp) {
                        moved |= moveItemStackTo(stack, index, index + 1, false);
                    }
                }
            }
        }
        return moved;
    }

    // Свободная ячейка брони или снаряжения, куда эту вещь можно надеть, или null
    @Nullable
    private Slot equipTarget(Player player, ItemStack stack) {
        ArmorSlot armorSlot = ModArmor.slotOf(stack);
        if (armorSlot != null) {
            ArmorEquipSlot target = (ArmorEquipSlot) slots.get(ARMOR_START + armorSlot.ordinal());
            return equipIfAllowed(player, target, target.hasItem() ? null : target.refusal(stack));
        }
        GearSlot gearSlot = GearRules.slotFor(gear, stack);
        if (gearSlot != null) {
            GearEquipSlot target = (GearEquipSlot) slots.get(GEAR_START + gearSlot.ordinal());
            return equipIfAllowed(player, target, target.hasItem() ? null : target.refusal(stack));
        }
        return null;
    }

    @Nullable
    private static Slot equipIfAllowed(Player player, Slot target, @Nullable Component refusal) {
        if (target.hasItem()) {
            return null; // занято — вещь пойдёт в хранилище
        }
        if (refusal != null) {
            ArmorRules.tell(player, refusal);
            return null;
        }
        return target;
    }

    // Вторая рука — как в ванильном инвентаре (и в окне стойки для брони)
    static class OffhandSlot extends Slot {
        private final Player owner;

        OffhandSlot(Inventory inventory, Player owner) {
            super(inventory, Inventory.SLOT_OFFHAND, OFFHAND_X, POCKETS_Y);
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
