package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.cooking.Fire;
import com.nrdhvnstd.halgventmod.pottery.KilnBlockEntity;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

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

/**
 * Окно горна: жар и к чему он тянется, запас дров (ячейка — дрова сразу уходят в запас), этап обжига строкой,
 * изделия (до 10; класть — в холодный и пустой от обожжённого, вынимать — когда остыл), тяга по ступеням, «Разжечь» / «Затушить».
 */
public class KilnMenu extends StationMenu {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 252;
    public static final int FUEL_X = 231;
    public static final int FUEL_Y = 97;
    public static final int WARES_X = 9;
    public static final int WARES_Y = 125;
    public static final int WARES = 10;
    public static final int FUEL_SLOT = PLAYER_END;
    public static final int WARES_START = FUEL_SLOT + 1;
    public static final int WARES_END = WARES_START + WARES;
    public static final int BUTTON_IGNITE = 0;
    public static final int BUTTON_EXTINGUISH = 1;
    public static final int BUTTON_DRAFT = 10; // + ступень тяги 0–5

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final KilnBlockEntity kiln;

    public static KilnMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new KilnMenu(containerId, inventory, new SimpleContainer(1), new SimpleContainer(WARES),
                new SimpleContainerData(KilnBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public KilnMenu(int containerId, Inventory inventory, KilnBlockEntity kiln) {
        this(containerId, inventory, kiln.fuelInput(), kiln.wares(), kiln.data(), kiln.getBlockPos(), kiln);
    }

    private KilnMenu(int containerId, Inventory inventory, Container fuel, Container wares, ContainerData data, BlockPos pos,
                     @Nullable KilnBlockEntity kiln) {
        super(ModMenus.KILN.get(), containerId, inventory, 47, 168);
        this.data = data;
        this.pos = pos;
        this.kiln = kiln;
        addSlot(new FuelSlot(fuel, FUEL_X, FUEL_Y));
        for (int i = 0; i < WARES; i++) {
            addSlot(new WareSlot(wares, i, WARES_X + (i % 5) * STEP, WARES_Y + (i / 5) * STEP));
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (kiln == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        switch (id) {
            case BUTTON_IGNITE -> kiln.igniteFromInventory(serverPlayer);
            case BUTTON_EXTINGUISH -> kiln.extinguish(serverPlayer);
            default -> {
                if (id < BUTTON_DRAFT || id > BUTTON_DRAFT + KilnBlockEntity.MAX_DRAFT) {
                    return false;
                }
                kiln.changeDraft(serverPlayer, id - BUTTON_DRAFT - data.get(KilnBlockEntity.D_DRAFT));
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return kiln == null || Container.stillValidBlockEntity(kiln, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        return Fire.fuelValue(stack) > 0 ? moveItemStackTo(stack, FUEL_SLOT, WARES_START, false)
                : moveItemStackTo(stack, WARES_START, WARES_END, false);
    }

    private static class FuelSlot extends FilteredSlot {
        FuelSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Fire.fuelValue(stack) > 0;
        }
    }

    /** Место в горне: открыто по вместимости горна; вынимать — когда остыл. */
    private class WareSlot extends FilteredSlot {
        WareSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return getContainerSlot() < data.get(KilnBlockEntity.D_CAPACITY);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // На клиенте горна нет — проверяем по тому, что знаем; окончательно решает сервер (Wares.canPlaceItem)
            if (kiln == null) {
                return (Pottery.isGreenware(stack) || Pottery.isRawClay(stack)) && Pottery.has(stack, Pottery.DRIED)
                        && getContainerSlot() == data.get(KilnBlockEntity.D_COUNT);
            }
            return getContainerSlot() == data.get(KilnBlockEntity.D_COUNT) && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return data.get(KilnBlockEntity.D_HEAT) <= Math.round(KilnBlockEntity.HOT_OPEN * 10);
        }
    }
}
