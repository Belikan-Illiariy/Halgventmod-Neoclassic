package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.carpentry.BenchProducts;
import com.nrdhvnstd.halgventmod.carpentry.CarpenterBenchBlockEntity;
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
 * Окно верстака плотника — раскладка как у шитейного стола: слева изделия, посередине ячейки верстака 3×3
 * (что в них лежит — видно на верстаке), справа — что выйдет и инструменты этапов; внизу — сумки, карманы и вторая рука.
 * Кнопка «Делать» (clickMenuButton) запускает этапы работы; выбор изделия — тоже кнопкой, по его номеру.
 */
public class CarpenterBenchMenu extends AbstractContainerMenu {
    public static final int WIDTH = SewingTableMenu.WIDTH;
    public static final int HEIGHT = SewingTableMenu.HEIGHT;
    public static final int STEP = SewingTableMenu.STEP;
    public static final int PRODUCTS_X = SewingTableMenu.PATTERNS_X;
    public static final int PRODUCTS_Y = SewingTableMenu.PATTERNS_Y;
    public static final int PRODUCT_COLUMNS = SewingTableMenu.PATTERN_COLUMNS;
    public static final int PRODUCT_ROWS = SewingTableMenu.PATTERN_ROWS;
    public static final int GRID_X = SewingTableMenu.GRID_X;
    public static final int GRID_Y = SewingTableMenu.GRID_Y;
    public static final int RESULT_X = SewingTableMenu.RESULT_X;
    public static final int RESULT_Y = SewingTableMenu.RESULT_Y;
    public static final int TOOLS_X = SewingTableMenu.TOOLS_X;
    public static final int NEEDS_Y = SewingTableMenu.NEEDS_Y;
    public static final int BUTTONS_Y = SewingTableMenu.BUTTONS_Y;
    public static final int POCKETS_X = SewingTableMenu.POCKETS_X;
    public static final int STORAGE_Y = SewingTableMenu.STORAGE_Y;
    public static final int POCKETS_Y = SewingTableMenu.POCKETS_Y;
    public static final int OFFHAND_X = SewingTableMenu.OFFHAND_X;

    public static final int PLAYER_END = 37;      // 27 ячеек сумок, 9 карманов, вторая рука
    public static final int BENCH_START = PLAYER_END;
    public static final int BENCH_END = BENCH_START + CarpenterBenchBlockEntity.SLOTS;
    public static final int BUTTON_WORK = 1000;

    private final Container bench;
    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final CarpenterBenchBlockEntity station; // только на сервере

    // Клиент: ячейки верстака приходят обычной синхронизацией окна
    public static CarpenterBenchMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new CarpenterBenchMenu(containerId, inventory, new SimpleContainer(CarpenterBenchBlockEntity.SLOTS),
                new SimpleContainerData(CarpenterBenchBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public CarpenterBenchMenu(int containerId, Inventory inventory, CarpenterBenchBlockEntity station) {
        this(containerId, inventory, station.materials(), station.data(), station.getBlockPos(), station);
    }

    private CarpenterBenchMenu(int containerId, Inventory inventory, Container bench, ContainerData data, BlockPos pos,
                               @Nullable CarpenterBenchBlockEntity station) {
        super(ModMenus.CARPENTER_BENCH.get(), containerId);
        this.bench = bench;
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
        for (int i = 0; i < CarpenterBenchBlockEntity.SLOTS; i++) {
            addSlot(new Slot(bench, i, GRID_X + (i % 3) * STEP, GRID_Y + (i / 3) * STEP));
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    /** Выбранное изделие, −1 — никакое. */
    public int product() {
        int index = data.get(0);
        return index >= 0 && index < BenchProducts.all().size() ? index : -1;
    }

    /** Сколько такой вещи лежит в ячейках верстака. */
    public int onBench(BenchProducts.Need need) {
        int count = 0;
        for (int i = 0; i < bench.getContainerSize(); i++) {
            ItemStack stack = bench.getItem(i);
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
        if (id == BUTTON_WORK) {
            station.requestWork(serverPlayer);
        } else if (id >= 0 && id < BenchProducts.all().size()) {
            station.chooseProduct(serverPlayer, id);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return station == null || Container.stillValidBlockEntity(station, player);
    }

    // Shift+клик: с верстака — в карманы и сумки, из инвентаря — на верстак
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index >= BENCH_START
                ? moveItemStackTo(stack, 0, PLAYER_END, false)
                : moveItemStackTo(stack, BENCH_START, BENCH_END, false);
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
