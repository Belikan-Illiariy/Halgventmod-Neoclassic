package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.brewing.AgingCaskBlockEntity;
import com.nrdhvnstd.halgventmod.brewing.Brewing;
import com.nrdhvnstd.halgventmod.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Окно бочки для выдержки: {@link AgingCaskBlockEntity#CAPACITY} мест под склянки (под каждой — сколько ступеней
 * набрала и полоса до следующей), где стоит бочка (погреб, дом, под небом).
 */
public class AgingCaskMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int BOTTLES_X = 47;
    public static final int BOTTLES_Y = 40;
    public static final int BOTTLE_STEP = 18;
    public static final int CASK_START = PLAYER_END;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final AgingCaskBlockEntity cask;

    public static AgingCaskMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new AgingCaskMenu(containerId, inventory, new SimpleContainer(AgingCaskBlockEntity.CAPACITY),
                new SimpleContainerData(AgingCaskBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public AgingCaskMenu(int containerId, Inventory inventory, AgingCaskBlockEntity cask) {
        this(containerId, inventory, cask.bottles(), cask.data(), cask.getBlockPos(), cask);
    }

    private AgingCaskMenu(int containerId, Inventory inventory, Container bottles, ContainerData data, BlockPos pos,
                          @Nullable AgingCaskBlockEntity cask) {
        super(ModMenus.AGING_CASK.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.cask = cask;
        for (int i = 0; i < AgingCaskBlockEntity.CAPACITY; i++) {
            addSlot(new Slot(bottles, i, BOTTLES_X + i * BOTTLE_STEP, BOTTLES_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Brewing.ageable(stack);
                }
            });
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
    public boolean stillValid(Player player) {
        return cask == null || Container.stillValidBlockEntity(cask, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        return Brewing.ageable(stack)
                && moveItemStackTo(stack, CASK_START, CASK_START + AgingCaskBlockEntity.CAPACITY, false);
    }
}
