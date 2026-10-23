package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.smithing.Crucible;
import com.nrdhvnstd.halgventmod.smithing.ForgeBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.SmithingBlocks;

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
 * Окно кузнечного горна: слева огонь и ячейка угля (уходит в горн), справа тигель и ячейка шихты (уходит в тигель),
 * что в тигле и что из этого выйдет; «Мехи» и «Плавить». Тигель с жидким металлом вынимают только с клещами.
 */
public class ForgeMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int FUEL_X = 27, FUEL_Y = 81;
    public static final int CRUCIBLE_X = 106, CRUCIBLE_Y = 20;
    public static final int CHARGE_X = 106, CHARGE_Y = 42;
    public static final int INPUT_START = PLAYER_END;
    public static final int BUTTON_MELT = 0;
    public static final int BUTTON_PUMP = 1;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final ForgeBlockEntity forge;

    public static ForgeMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new ForgeMenu(containerId, inventory, new SimpleContainer(3), new SimpleContainerData(ForgeBlockEntity.DATA),
                buf.readBlockPos(), null);
    }

    public ForgeMenu(int containerId, Inventory inventory, ForgeBlockEntity forge) {
        this(containerId, inventory, forge.slots(), forge.data(), forge.getBlockPos(), forge);
    }

    private ForgeMenu(int containerId, Inventory inventory, Container slots, ContainerData data, BlockPos pos,
                      @Nullable ForgeBlockEntity forge) {
        super(SmithingBlocks.FORGE_MENU.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.forge = forge;
        addSlot(new CrucibleSlot(slots, ForgeBlockEntity.IN_CRUCIBLE, CRUCIBLE_X, CRUCIBLE_Y));
        addSlot(new FilteredSlot(slots, ForgeBlockEntity.IN_CHARGE, CHARGE_X, CHARGE_Y));
        addSlot(new FilteredSlot(slots, ForgeBlockEntity.IN_FUEL, FUEL_X, FUEL_Y));
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    public ItemStack crucible() {
        return slots.get(INPUT_START + ForgeBlockEntity.IN_CRUCIBLE).getItem();
    }

    public ItemStack charge() {
        return slots.get(INPUT_START + ForgeBlockEntity.IN_CHARGE).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (forge == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        switch (id) {
            case BUTTON_MELT -> forge.startMelt(serverPlayer);
            case BUTTON_PUMP -> forge.pump(serverPlayer);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return forge == null || Container.stillValidBlockEntity(forge, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        int slot = Crucible.is(stack) ? ForgeBlockEntity.IN_CRUCIBLE
                : stack.is(Items.CHARCOAL) || stack.is(Items.COAL) ? ForgeBlockEntity.IN_FUEL
                : ForgeBlockEntity.charge(stack) ? ForgeBlockEntity.IN_CHARGE : -1;
        return slot >= 0 && moveItemStackTo(stack, INPUT_START + slot, INPUT_START + slot + 1, false);
    }
}
