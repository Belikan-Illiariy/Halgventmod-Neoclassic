package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.smithing.BloomeryBlockEntity;
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
 * Окно сыродутного горна: ячейки «руда», «уголь», «флюс» (положенное сразу уходит в горн, что не влезло — остаётся) и «тигель» (медь, олово и
 * серебро стекают в него расплавом; пока горит — не трогать),
 * что заложено и сколько закладок, что выйдет и что повлияет на качество; «Плавить» (огниво из инвентаря) и «Выгрести».
 */
public class BloomeryMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int ORE_X = 12, ORE_Y = 20;
    public static final int FUEL_X = 12, FUEL_Y = 40;
    public static final int FLUX_X = 12, FLUX_Y = 60;
    public static final int CRUCIBLE_X = 12, CRUCIBLE_Y = 82;
    public static final int INPUT_START = PLAYER_END;
    public static final int BUTTON_SMELT = 0;
    public static final int BUTTON_TAKE_ALL = 1;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final BloomeryBlockEntity bloomery;

    public static BloomeryMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new BloomeryMenu(containerId, inventory, new SimpleContainer(4),
                new SimpleContainerData(BloomeryBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public BloomeryMenu(int containerId, Inventory inventory, BloomeryBlockEntity bloomery) {
        this(containerId, inventory, bloomery.inputs(), bloomery.data(), bloomery.getBlockPos(), bloomery);
    }

    private BloomeryMenu(int containerId, Inventory inventory, Container inputs, ContainerData data, BlockPos pos,
                         @Nullable BloomeryBlockEntity bloomery) {
        super(SmithingBlocks.BLOOMERY_MENU.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.bloomery = bloomery;
        addSlot(new InputSlot(inputs, BloomeryBlockEntity.IN_ORE, ORE_X, ORE_Y));
        addSlot(new InputSlot(inputs, BloomeryBlockEntity.IN_FUEL, FUEL_X, FUEL_Y));
        addSlot(new InputSlot(inputs, BloomeryBlockEntity.IN_FLUX, FLUX_X, FLUX_Y));
        addSlot(new CrucibleSlot(inputs, BloomeryBlockEntity.IN_CRUCIBLE, CRUCIBLE_X, CRUCIBLE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !lit() && super.mayPlace(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return !lit() && super.mayPickup(player);
            }
        });
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    public boolean lit() {
        return data.get(BloomeryBlockEntity.D_LIT) != 0;
    }

    public ItemStack crucible() {
        return slots.get(INPUT_START + BloomeryBlockEntity.IN_CRUCIBLE).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (bloomery == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        switch (id) {
            case BUTTON_SMELT -> bloomery.startFromMenu(serverPlayer);
            case BUTTON_TAKE_ALL -> bloomery.takeAllFromMenu(serverPlayer);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return bloomery == null || Container.stillValidBlockEntity(bloomery, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        int slot = com.nrdhvnstd.halgventmod.smithing.Crucible.is(stack) ? BloomeryBlockEntity.IN_CRUCIBLE
                : BloomeryBlockEntity.Smelt.of(stack) != null ? BloomeryBlockEntity.IN_ORE
                : stack.is(Items.CHARCOAL) || stack.is(Items.COAL) ? BloomeryBlockEntity.IN_FUEL
                : stack.is(SmithingItems.FLUX.get()) ? BloomeryBlockEntity.IN_FLUX : -1;
        return slot >= 0 && moveItemStackTo(stack, INPUT_START + slot, INPUT_START + slot + 1, false);
    }

    /** Ячейка закладки: пока горн горит — закрыта. */
    private class InputSlot extends FilteredSlot {
        InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (lit()) {
                return false;
            }
            return switch (getContainerSlot()) {
                case BloomeryBlockEntity.IN_ORE -> BloomeryBlockEntity.Smelt.of(stack) != null;
                case BloomeryBlockEntity.IN_FUEL -> stack.is(Items.CHARCOAL) || stack.is(Items.COAL);
                default -> stack.is(SmithingItems.FLUX.get());
            };
        }
    }
}
