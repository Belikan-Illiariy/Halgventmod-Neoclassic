package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.cooking.Cookery;
import com.nrdhvnstd.halgventmod.cooking.Fire;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно очага и костра: слева огонь (температура, сколько гореть, {@link Fire#SLOTS} ячейки дров, «Разжечь» /
 * «Затушить»), справа — горшок, если стоит (ячейки продуктов, варка, «Помешать», «Опорожнить»); внизу инвентарь.
 * Рецепты горшка — панель слева от окна (HearthScreen).
 */
public class HearthMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int FUEL_X = 27;
    public static final int FUEL_Y = 81;
    public static final int POT_X = 115;
    public static final int POT_Y = 37;
    public static final int FUEL_START = PLAYER_END;
    public static final int POT_START = FUEL_START + Fire.SLOTS;
    public static final int POT_END = POT_START + Cookery.CAPACITY;
    public static final int BUTTON_IGNITE = 0;
    public static final int BUTTON_EXTINGUISH = 1;
    public static final int BUTTON_STIR = 2;
    public static final int BUTTON_EMPTY = 3;
    public static final int BUTTON_REMOVE_POT = 4;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final HearthBlockEntity hearth; // только на сервере

    public static HearthMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new HearthMenu(containerId, inventory, new SimpleContainer(Fire.SLOTS), new SimpleContainer(Cookery.CAPACITY),
                new SimpleContainerData(HearthBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public HearthMenu(int containerId, Inventory inventory, HearthBlockEntity hearth) {
        this(containerId, inventory, hearth.fire().fuel(), hearth.potContents(), hearth.data(), hearth.getBlockPos(), hearth);
    }

    private HearthMenu(int containerId, Inventory inventory, Container fuel, Container pot, ContainerData data, BlockPos pos,
                       @Nullable HearthBlockEntity hearth) {
        super(ModMenus.HEARTH.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.hearth = hearth;
        for (int i = 0; i < Fire.SLOTS; i++) {
            addSlot(new FilteredSlot(fuel, i, FUEL_X + i * 18, FUEL_Y));
        }
        for (int i = 0; i < Cookery.CAPACITY; i++) {
            addSlot(new PotSlot(pot, i, POT_X + (i % 3) * 18, POT_Y + (i / 3) * 18));
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    public boolean hasPot() {
        return data.get(HearthBlockEntity.D_POT) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (hearth == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        switch (id) {
            case BUTTON_IGNITE -> hearth.igniteFromInventory(serverPlayer);
            case BUTTON_EXTINGUISH -> hearth.extinguish(serverPlayer);
            case BUTTON_STIR -> hearth.stir(serverPlayer);
            case BUTTON_EMPTY -> hearth.emptyPot(serverPlayer);
            case BUTTON_REMOVE_POT -> hearth.removePot(serverPlayer);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return hearth == null || Container.stillValidBlockEntity(hearth, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        return Fire.fuelValue(stack) > 0 ? moveItemStackTo(stack, FUEL_START, POT_START, false)
                : hasPot() && moveItemStackTo(stack, POT_START, POT_END, false);
    }

    /** Ячейка горшка: класть — когда есть вода и готовое не черпают; вынимать — пока не начало вариться. */
    private class PotSlot extends FilteredSlot {
        PotSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return hasPot();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return super.mayPlace(stack) && data.get(HearthBlockEntity.D_WATER) != 0
                    && data.get(HearthBlockEntity.D_LADLED) == 0;
        }

        @Override
        public boolean mayPickup(Player player) {
            return data.get(HearthBlockEntity.D_PROGRESS) == 0 && data.get(HearthBlockEntity.D_PORTIONS) == 0;
        }
    }
}
