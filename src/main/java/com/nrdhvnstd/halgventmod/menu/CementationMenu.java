package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.smithing.CementationBoxBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.SmithingBlocks;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Окно цементационной печи: камера на {@link CementationBoxBlockEntity#CAPACITY} слитков, ячейка угля (уходит в печь),
 * сколько угля и сколько надо, как томится; «Запечатать», «Разжечь», «Вскрыть». Запечатанную не трогают: ячейки закрыты.
 */
public class CementationMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int INGOTS_X = 12, INGOTS_Y = 22;
    public static final int FUEL_X = 12, FUEL_Y = 66;
    public static final int STATION_START = PLAYER_END;
    public static final int FUEL_SLOT = STATION_START + CementationBoxBlockEntity.CAPACITY;
    public static final int BUTTON_SEAL = 0;
    public static final int BUTTON_LIGHT = 1;
    public static final int BUTTON_OPEN = 2;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final CementationBoxBlockEntity box;

    public static CementationMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new CementationMenu(containerId, inventory, new SimpleContainer(CementationBoxBlockEntity.CAPACITY),
                new SimpleContainer(1), new SimpleContainerData(CementationBoxBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public CementationMenu(int containerId, Inventory inventory, CementationBoxBlockEntity box) {
        this(containerId, inventory, box.slots(), box.fuelInput(), box.data(), box.getBlockPos(), box);
    }

    private CementationMenu(int containerId, Inventory inventory, Container ingots, Container fuel, ContainerData data,
                            BlockPos pos, @Nullable CementationBoxBlockEntity box) {
        super(SmithingBlocks.CEMENTATION_MENU.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.box = box;
        for (int i = 0; i < CementationBoxBlockEntity.CAPACITY; i++) {
            addSlot(new ChamberSlot(ingots, i, INGOTS_X + (i % 4) * STEP, INGOTS_Y + (i / 4) * STEP, false));
        }
        addSlot(new ChamberSlot(fuel, 0, FUEL_X, FUEL_Y, true));
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    public boolean sealed() {
        return data.get(CementationBoxBlockEntity.D_SEALED) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (box == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        switch (id) {
            case BUTTON_SEAL -> box.sealFromMenu(serverPlayer);
            case BUTTON_LIGHT -> box.lightFromMenu(serverPlayer);
            case BUTTON_OPEN -> box.openFromMenu(serverPlayer);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return box == null || Container.stillValidBlockEntity(box, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        if (stack.is(Items.CHARCOAL)) {
            return moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false);
        }
        return stack.is(SmithingItems.IRON_INGOT.get()) && moveItemStackTo(stack, STATION_START, FUEL_SLOT, false);
    }

    /** Ячейка камеры или угля: запечатанная печь закрыта. */
    private class ChamberSlot extends FilteredSlot {
        private final boolean fuel;

        ChamberSlot(Container container, int slot, int x, int y, boolean fuel) {
            super(container, slot, x, y);
            this.fuel = fuel;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !sealed() && (fuel ? stack.is(Items.CHARCOAL) : stack.is(SmithingItems.IRON_INGOT.get()));
        }

        @Override
        public boolean mayPickup(Player player) {
            return !sealed();
        }

        @Override
        public int getMaxStackSize() {
            return fuel ? 64 : 1;
        }
    }
}
