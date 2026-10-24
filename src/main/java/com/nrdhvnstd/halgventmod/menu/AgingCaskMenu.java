package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.brewing.AgingCaskBlockEntity;
import com.nrdhvnstd.halgventmod.brewing.Brewing;
import com.nrdhvnstd.halgventmod.brewing.StillBlockEntity;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.pottery.Stored;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
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
 * Окно бочки для выдержки: «влить» (склянка питья или сосуд с ним) и пустые склянки после него; что налито, сколько,
 * где стоит бочка (погреб, дом, под небом), сколько ступеней набрало и когда следующая; склянки или сосуд — «налить»,
 * и «налитое»; «Вылить».
 */
public class AgingCaskMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int IN_X = 12, IN_Y = 22;
    public static final int EMPTIES_X = 12, EMPTIES_Y = 48;
    public static final int BOTTLES_X = 112, BOTTLES_Y = 76;
    public static final int OUT_X = 166, OUT_Y = 76;
    public static final int CASK_START = PLAYER_END;
    public static final int BUTTON_POUR_OUT = 0;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final AgingCaskBlockEntity cask;

    public static AgingCaskMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new AgingCaskMenu(containerId, inventory, new SimpleContainer(AgingCaskBlockEntity.SLOTS),
                new SimpleContainerData(AgingCaskBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public AgingCaskMenu(int containerId, Inventory inventory, AgingCaskBlockEntity cask) {
        this(containerId, inventory, cask.slots(), cask.data(), cask.getBlockPos(), cask);
    }

    private AgingCaskMenu(int containerId, Inventory inventory, Container slots, ContainerData data, BlockPos pos,
                          @Nullable AgingCaskBlockEntity cask) {
        super(ModMenus.AGING_CASK.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.cask = cask;
        addSlot(new FilteredSlot(slots, AgingCaskBlockEntity.SLOT_IN, IN_X, IN_Y));
        addSlot(new Slot(slots, AgingCaskBlockEntity.SLOT_EMPTIES, EMPTIES_X, EMPTIES_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new FilteredSlot(slots, AgingCaskBlockEntity.SLOT_BOTTLES, BOTTLES_X, BOTTLES_Y));
        addSlot(new Slot(slots, AgingCaskBlockEntity.SLOT_OUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (cask == null || !(player instanceof ServerPlayer serverPlayer) || id != BUTTON_POUR_OUT) {
            return false;
        }
        cask.pourOut(serverPlayer);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return cask == null || Container.stillValidBlockEntity(cask, player);
    }

    /** Shift+клик: питьё и сосуд с ним — «влить», пустые склянки и сосуды — «налить». */
    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        Stored stored = stack.get(ModComponents.STORED.get());
        boolean full = Pottery.drinkCapacity(stack) > 0 && stored != null && !stored.sealed();
        int slot = Brewing.ageable(stack) || full ? AgingCaskBlockEntity.SLOT_IN
                : StillBlockEntity.vessel(stack) ? AgingCaskBlockEntity.SLOT_BOTTLES : -1;
        return slot >= 0 && moveItemStackTo(stack, CASK_START + slot, CASK_START + slot + 1, false);
    }
}
