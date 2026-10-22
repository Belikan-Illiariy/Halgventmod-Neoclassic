package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.leather.LeatherPatterns;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно шитейного стола: слева выкройки, посередине ячейки стола 3×3 (что в них лежит — видно на столе), справа —
 * что выйдет и инструменты; внизу — сумки, карманы и вторая рука игрока. Кнопки «Раскроить» и «Шить»
 * (clickMenuButton) запускают мини-игры; выбор выкройки — тоже кнопкой, по её номеру.
 */
public class SewingTableMenu extends AbstractContainerMenu {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 212;
    public static final int STEP = 18;
    // Выкройки: сетка 5 столбцов, 5 строк видно (колесо мыши — листать)
    public static final int PATTERNS_X = 8;
    public static final int PATTERNS_Y = 18;
    public static final int PATTERN_COLUMNS = 5;
    public static final int PATTERN_ROWS = 5;
    // Ячейки стола 3×3, места предметов
    public static final int GRID_X = 109;
    public static final int GRID_Y = 19;
    public static final int RESULT_X = 196;
    public static final int RESULT_Y = 37;
    public static final int TOOLS_X = 233;
    public static final int NEEDS_Y = 86;
    public static final int BUTTONS_Y = 106;
    // Инвентарь игрока
    public static final int POCKETS_X = 47;
    public static final int STORAGE_Y = 130;
    public static final int POCKETS_Y = STORAGE_Y + 3 * STEP + 4;
    public static final int OFFHAND_X = POCKETS_X - 22;

    public static final int PLAYER_END = 37;      // 27 ячеек сумок, 9 карманов, вторая рука
    public static final int TABLE_START = PLAYER_END;
    public static final int TABLE_END = TABLE_START + LeatherTableBlockEntity.SLOTS;
    public static final int BUTTON_CUT = 1000;
    public static final int BUTTON_SEW = 1001;

    private final Container table;
    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final LeatherTableBlockEntity station; // только на сервере

    // Клиент: ячейки стола приходят обычной синхронизацией окна
    public static SewingTableMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new SewingTableMenu(containerId, inventory, new SimpleContainer(LeatherTableBlockEntity.SLOTS),
                new SimpleContainerData(LeatherTableBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public SewingTableMenu(int containerId, Inventory inventory, LeatherTableBlockEntity station) {
        this(containerId, inventory, station.materials(), station.data(), station.getBlockPos(), station);
    }

    private SewingTableMenu(int containerId, Inventory inventory, Container table, ContainerData data, BlockPos pos,
                            @Nullable LeatherTableBlockEntity station) {
        super(ModMenus.SEWING_TABLE.get(), containerId);
        this.table = table;
        this.data = data;
        this.pos = pos;
        this.station = station;
        // Ячейки 9–35 инвентаря становятся ячейками сумок (AbstractContainerMenuMixin)
        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(inventory, 9 + i, POCKETS_X + (i % 9) * STEP, STORAGE_Y + (i / 9) * STEP));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, POCKETS_X + col * STEP, POCKETS_Y));
        }
        addSlot(new Slot(inventory, Inventory.SLOT_OFFHAND, OFFHAND_X, POCKETS_Y));
        for (int i = 0; i < LeatherTableBlockEntity.SLOTS; i++) {
            addSlot(new Slot(table, i, GRID_X + (i % 3) * STEP, GRID_Y + (i / 3) * STEP));
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    /** Выбранная выкройка, −1 — никакая. */
    public int pattern() {
        int index = data.get(0);
        return index >= 0 && index < LeatherPatterns.all().size() ? index : -1;
    }

    /** Уже раскроено: ждёт шитья. */
    public boolean isCut() {
        return data.get(1) != 0;
    }

    /** Сколько такой вещи лежит в ячейках стола. */
    public int onTable(LeatherPatterns.Need need) {
        int count = 0;
        for (int i = 0; i < table.getContainerSize(); i++) {
            ItemStack stack = table.getItem(i);
            if (need.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (station == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == BUTTON_CUT) {
            station.requestCut(serverPlayer);
        } else if (id == BUTTON_SEW) {
            station.requestSew(serverPlayer);
        } else if (id >= 0 && id < LeatherPatterns.all().size()) {
            station.choosePattern(serverPlayer, id);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return station == null || Container.stillValidBlockEntity(station, player);
    }

    // Shift+клик: со стола — в карманы и сумки, из инвентаря — на стол
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index >= TABLE_START
                ? moveItemStackTo(stack, 0, PLAYER_END, false)
                : moveItemStackTo(stack, TABLE_START, TABLE_END, false);
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
}
