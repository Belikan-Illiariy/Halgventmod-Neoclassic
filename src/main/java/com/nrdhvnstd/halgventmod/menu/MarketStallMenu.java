package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.network.StallSyncPayload;
import com.nrdhvnstd.halgventmod.storage.StorageArea;
import com.nrdhvnstd.halgventmod.trade.MarketStallBlockEntity;
import com.nrdhvnstd.halgventmod.trade.StallKind;
import com.nrdhvnstd.halgventmod.trade.TradeBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно прилавка для хозяина: товар сверху (раскладка по виду прилавка), ниже — сумки, карманы и вторая рука хозяина,
 * справа — панель товаров и запросов (client/trade/MarketStallScreen). Оружейная стойка — 4 крюка в ряд, витрина —
 * 8 мест (4×2), бочонок — 8 ячеек под кружки торговца (4×2), манекен — 12 слотов брони сеткой 4×3, как у игрока
 * (каждая ячейка — свой слот), прилавок товаров — 9×3.
 * Цены и запросы приходят отдельным пакетом (StallSyncPayload) при открытии и после изменений.
 */
public class MarketStallMenu extends AbstractContainerMenu {
    public static final int WIDTH = PlayerArmorMenu.WIDTH;
    public static final int HEIGHT = PlayerArmorMenu.HEIGHT;
    public static final int STOCK_START = PlayerArmorMenu.OFFHAND + 1;
    private static final double REACH_BUFFER = 4.0;

    private final BlockPos pos;
    private final StallKind kind;
    private final Player owner;
    @Nullable
    private final MarketStallBlockEntity stall; // только на сервере
    private int sentVersion = -1;

    // Клиент: товар приходит обычной синхронизацией окна, остальное — StallSyncPayload
    public static MarketStallMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        StallKind kind = StallKind.values()[buf.readVarInt()];
        return new MarketStallMenu(containerId, inventory, clientStock(kind), pos, kind, null);
    }

    // Те же правила ячеек, что на сервере: подсказка, куда можно положить, не расходится с правдой
    private static Container clientStock(StallKind kind) {
        return new SimpleContainer(kind.slots()) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return kind.accepts(stack, slot);
            }

            @Override
            public int getMaxStackSize() {
                return kind.perSlot();
            }
        };
    }

    public MarketStallMenu(int containerId, Inventory inventory, Container stock, BlockPos pos, StallKind kind,
                           @Nullable MarketStallBlockEntity stall) {
        super(TradeBlocks.MARKET_STALL_MENU.get(), containerId);
        this.pos = pos;
        this.kind = kind;
        this.owner = inventory.player;
        this.stall = stall;

        for (int index = 0; index < StorageArea.INDEX_COUNT; index++) {
            int column = index % StorageArea.COLUMNS;
            int row = index / StorageArea.COLUMNS;
            addSlot(new StorageSlot(owner, index, PlayerArmorMenu.STORAGE_X + column * PlayerArmorMenu.SLOT_STEP,
                    PlayerArmorMenu.STORAGE_Y + row * PlayerArmorMenu.SLOT_STEP));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PlayerArmorMenu.POCKETS_X + col * PlayerArmorMenu.SLOT_STEP, PlayerArmorMenu.POCKETS_Y));
        }
        addSlot(new PlayerArmorMenu.OffhandSlot(inventory, owner));
        for (int i = 0; i < kind.slots(); i++) {
            addSlot(new StockSlot(stock, i, stockX(kind, i), stockY(kind, i), kind));
        }
    }

    /** Где ячейка товара: по виду прилавка. */
    public static int stockX(StallKind kind, int i) {
        int step = PlayerArmorMenu.SLOT_STEP;
        return switch (kind) {
            case WEAPONS -> PlayerArmorMenu.STORAGE_X + (9 - 4) * step / 2 + i * step;
            case FOOD, TAP, GOODS -> PlayerArmorMenu.STORAGE_X + (9 - 4) * step / 2 + (i % 4) * step;
            case ARMOR -> PlayerArmorMenu.ARMOR_X + (i % PlayerArmorMenu.ARMOR_COLUMNS) * step;
            case STORAGE -> PlayerArmorMenu.STORAGE_X + (i % 9) * step;
        };
    }

    public static int stockY(StallKind kind, int i) {
        int step = PlayerArmorMenu.SLOT_STEP;
        return switch (kind) {
            case WEAPONS -> PlayerArmorMenu.TOP + step;
            case FOOD, TAP -> PlayerArmorMenu.TOP + step / 2 + (i / 4) * step;
            case GOODS -> PlayerArmorMenu.TOP + (i / 4) * step;
            case ARMOR -> PlayerArmorMenu.TOP + (i / PlayerArmorMenu.ARMOR_COLUMNS) * step;
            case STORAGE -> PlayerArmorMenu.TOP + (i / 9) * step;
        };
    }

    public BlockPos pos() {
        return pos;
    }

    public StallKind kind() {
        return kind;
    }

    public int stockEnd() {
        return STOCK_START + kind.slots();
    }

    @Nullable
    public MarketStallBlockEntity stall() {
        return stall;
    }

    @Override
    public boolean stillValid(Player player) {
        if (stall == null) {
            return true;
        }
        return !stall.isRemoved() && (stall.isOwner(player) || Permissions.isAdmin(player))
                && player.canInteractWithBlock(pos, REACH_BUFFER);
    }

    // Цены и запросы — хозяину при открытии и после любых изменений прилавка
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (stall != null && owner instanceof ServerPlayer player && stall.version() != sentVersion) {
            sentVersion = stall.version();
            PacketDistributor.sendToPlayer(player, StallSyncPayload.of(stall));
        }
    }

    // Shift+клик: из инвентаря — в товар (в подходящую ячейку); из товара — в карманы, потом в сумки
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index >= STOCK_START) {
            moved = moveItemStackTo(stack, PlayerArmorMenu.POCKETS_START, PlayerArmorMenu.OFFHAND, false);
            if (!stack.isEmpty()) {
                moved |= moveToStorage(stack);
            }
        } else {
            moved = moveItemStackTo(stack, STOCK_START, stockEnd(), false);
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

    /** Ячейка товара: принимает только то, что принимает прилавок; у манекена пустая показывает силуэт своего слота. */
    public static final class StockSlot extends Slot {
        private final StallKind kind;

        StockSlot(Container container, int index, int x, int y, StallKind kind) {
            super(container, index, x, y);
            this.kind = kind;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(kind.perSlot(), stack.getMaxStackSize());
        }

        /** Слот брони этой ячейки манекена или null. */
        @Nullable
        public ArmorSlot armorSlot() {
            return kind == StallKind.ARMOR ? ArmorSlot.values()[getContainerSlot()] : null;
        }

        @Nullable
        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            ArmorSlot slot = armorSlot();
            if (slot == null) {
                return null;
            }
            ResourceLocation icon = switch (slot.bodyPart()) {
                case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case TORSO -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case LEGS -> slot == ArmorSlot.BOOTS ? InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS : InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            };
            return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
        }
    }
}
