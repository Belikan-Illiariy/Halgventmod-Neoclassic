package com.nrdhvnstd.halgventmod.menu;

import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.smithing.SmithProducts;
import com.nrdhvnstd.halgventmod.smithing.SmithingBlocks;
import com.nrdhvnstd.halgventmod.smithing.WorkshopBlockEntity;

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
 * Окно наковальни или литейного стола — раскладка как у верстака плотника: слева изделия по вкладкам, посередине
 * ячейки станции 3×3 (что в них лежит — видно на станции), справа — что выйдет и инструменты этапов; внизу — сумки,
 * карманы и вторая рука. «Ковать» / «Лить» (clickMenuButton) запускает этапы работы; выбор изделия — тоже кнопкой,
 * по его номеру.
 */
public class WorkshopMenu extends AbstractContainerMenu {
    public static final int WIDTH = CarpenterBenchMenu.WIDTH;
    public static final int HEIGHT = CarpenterBenchMenu.HEIGHT;
    public static final int STEP = CarpenterBenchMenu.STEP;
    public static final int PLAYER_END = CarpenterBenchMenu.PLAYER_END;
    public static final int STATION_START = PLAYER_END;
    public static final int STATION_END = STATION_START + WorkshopBlockEntity.SLOTS;
    public static final int BUTTON_WORK = 1000;

    private final Container station;
    private final ContainerData data;
    private final BlockPos pos;
    private final boolean casting;
    @Nullable
    private final WorkshopBlockEntity entity; // только на сервере

    public static WorkshopMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean casting = buf.readBoolean();
        return new WorkshopMenu(containerId, inventory, new SimpleContainer(WorkshopBlockEntity.SLOTS),
                new SimpleContainerData(WorkshopBlockEntity.DATA), pos, casting, null);
    }

    public WorkshopMenu(int containerId, Inventory inventory, WorkshopBlockEntity entity) {
        this(containerId, inventory, entity.materials(), entity.data(), entity.getBlockPos(), entity.casting(), entity);
    }

    private WorkshopMenu(int containerId, Inventory inventory, Container station, ContainerData data, BlockPos pos,
                         boolean casting, @Nullable WorkshopBlockEntity entity) {
        super(SmithingBlocks.WORKSHOP_MENU.get(), containerId);
        this.station = station;
        this.data = data;
        this.pos = pos;
        this.casting = casting;
        this.entity = entity;
        // Ячейки 9–35 инвентаря становятся ячейками сумок (AbstractContainerMenuMixin)
        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(inventory, 9 + i, CarpenterBenchMenu.POCKETS_X + (i % 9) * STEP,
                    CarpenterBenchMenu.STORAGE_Y + (i / 9) * STEP));
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, CarpenterBenchMenu.POCKETS_X + col * STEP, CarpenterBenchMenu.POCKETS_Y));
        }
        addSlot(new Slot(inventory, Inventory.SLOT_OFFHAND, CarpenterBenchMenu.OFFHAND_X, CarpenterBenchMenu.POCKETS_Y));
        for (int i = 0; i < WorkshopBlockEntity.SLOTS; i++) {
            addSlot(new Slot(station, i, CarpenterBenchMenu.GRID_X + (i % 3) * STEP, CarpenterBenchMenu.GRID_Y + (i / 3) * STEP));
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean casting() {
        return casting;
    }

    public List<SmithProducts.Product> products() {
        return casting ? SmithProducts.casting() : SmithProducts.anvil();
    }

    /** Выбранное изделие, −1 — никакое. */
    public int product() {
        int index = data.get(0);
        return index >= 0 && index < products().size() ? index : -1;
    }

    /** Сколько такой вещи лежит в ячейках станции. */
    public int onStation(SmithProducts.Need need) {
        int count = 0;
        for (int i = 0; i < station.getContainerSize(); i++) {
            ItemStack stack = station.getItem(i);
            if (need.matches(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == BUTTON_WORK) {
            entity.requestWork(serverPlayer);
        } else if (id >= 0 && id < products().size()) {
            entity.chooseProduct(serverPlayer, id);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return entity == null || Container.stillValidBlockEntity(entity, player);
    }

    // Shift+клик: со станции — в карманы и сумки, из инвентаря — на станцию
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index >= STATION_START
                ? moveItemStackTo(stack, 0, PLAYER_END, false)
                : moveItemStackTo(stack, STATION_START, STATION_END, false);
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
