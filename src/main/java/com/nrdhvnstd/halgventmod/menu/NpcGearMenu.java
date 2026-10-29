package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModMenus;
import com.nrdhvnstd.halgventmod.storage.StorageArea;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Снаряжение НПС как инвентарь (Shift+E по НПС → вкладка «Снаряжение», только администрация): 12 ячеек брони в
 * сетке 4×3 и две руки НПС (под бронёй), фигурка НПС, под ними — хранилище, карманы и вторая рука администратора.
 * Раскладка и номера ячеек — как в ArmorStandMenu, руки НПС добавлены в конец. Вещи перетаскиваются настоящие
 * (не копии), как с ванильной стойкой; правила надевания брони — те же, что у игрока (ArmorRules).
 */
public class NpcGearMenu extends AbstractContainerMenu {
    public static final int WIDTH = ArmorStandMenu.WIDTH;
    public static final int HEIGHT = ArmorStandMenu.HEIGHT;
    private static final double REACH_BUFFER = 4.0;
    /** Руки НПС — в ряду под бронёй. */
    public static final int HANDS_Y = PlayerArmorMenu.TOP + 3 * PlayerArmorMenu.SLOT_STEP;
    public static final int HANDS_START = PlayerArmorMenu.ARMOR_END;

    private final HalgNpc npc;
    private final ArmorInventory armor;

    // Клиент: НПС — по номеру сущности из пакета открытия
    public static NpcGearMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        Entity entity = inventory.player.level().getEntity(buf.readVarInt());
        if (!(entity instanceof HalgNpc npc)) {
            throw new IllegalStateException("NPC for the gear menu is not loaded on the client");
        }
        return new NpcGearMenu(containerId, inventory, npc);
    }

    public NpcGearMenu(int containerId, Inventory inventory, HalgNpc npc) {
        super(ModMenus.NPC_GEAR.get(), containerId);
        this.npc = npc;
        this.armor = npc.getData(ModAttachments.ARMOR);
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
            addSlot(new ArmorEquipSlot(armor, slot, npc, PlayerArmorMenu.ARMOR_X + col * PlayerArmorMenu.SLOT_STEP,
                    PlayerArmorMenu.TOP + row * PlayerArmorMenu.SLOT_STEP));
        }

        Container hands = new Hands(npc);
        addSlot(new HandSlot(hands, 0, PlayerArmorMenu.ARMOR_X, HANDS_Y));
        addSlot(new HandSlot(hands, 1, PlayerArmorMenu.ARMOR_X + PlayerArmorMenu.SLOT_STEP, HANDS_Y));
    }

    public HalgNpc npc() {
        return npc;
    }

    @Override
    public boolean stillValid(Player player) {
        return npc.isAlive() && Permissions.isAdmin(player) && player.canInteractWithEntity(npc, REACH_BUFFER);
    }

    // Броня НПС видна и другим игрокам — вложение уходит вместе с обычной синхронизацией окна
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!npc.level().isClientSide && armor.consumeDirty()) {
            npc.syncData(ModAttachments.ARMOR);
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

    // Shift+клик: броня администратора — на НПС в свою ячейку; снятое с НПС — в карманы, затем в подсумки и на спину;
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

    // Свободная ячейка брони НПС, куда эту вещь можно надеть, или null
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

    /** Ячейка руки НПС: что угодно, по одной вещи. */
    private static final class HandSlot extends Slot {
        HandSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Руки НПС как контейнер из двух ячеек: 0 — основная, 1 — вторая. Оружие стражи не выпадает. */
    private static final class Hands implements Container {
        private final HalgNpc npc;

        Hands(HalgNpc npc) {
            this.npc = npc;
        }

        private static EquipmentSlot hand(int slot) {
            return slot == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        }

        @Override
        public int getContainerSize() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return npc.getMainHandItem().isEmpty() && npc.getOffhandItem().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return npc.getItemBySlot(hand(slot));
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            return stack.isEmpty() ? ItemStack.EMPTY : removeItemNoUpdate(slot);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            npc.setItemSlot(hand(slot), stack);
            npc.setDropChance(hand(slot), 0.0F);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return npc.isAlive();
        }

        @Override
        public void clearContent() {
            setItem(0, ItemStack.EMPTY);
            setItem(1, ItemStack.EMPTY);
        }
    }
}
