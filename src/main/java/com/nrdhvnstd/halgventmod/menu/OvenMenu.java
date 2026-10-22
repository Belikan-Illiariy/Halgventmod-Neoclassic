package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.cooking.Fire;
import com.nrdhvnstd.halgventmod.cooking.OvenBlockEntity;
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
 * Окно хлебной печи: жар и сколько гореть, {@link Fire#SLOTS} ячейки дров, «Разжечь» / «Затушить»; справа — три места
 * в печи: что печётся, насколько готово и сколько ждать. Вынутое из ячейки печи — уже выпечка (OvenBlockEntity).
 */
public class OvenMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int FUEL_X = 27;
    public static final int FUEL_Y = 81;
    public static final int BAKE_X = 112;
    public static final int BAKE_Y = 22;
    public static final int BAKE_STEP = 26;
    public static final int FUEL_START = PLAYER_END;
    public static final int BAKE_START = FUEL_START + Fire.SLOTS;
    public static final int BAKE_END = BAKE_START + OvenBlockEntity.SLOTS;
    public static final int BUTTON_IGNITE = 0;
    public static final int BUTTON_EXTINGUISH = 1;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final OvenBlockEntity oven;

    public static OvenMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new OvenMenu(containerId, inventory, new SimpleContainer(Fire.SLOTS), new SimpleContainer(OvenBlockEntity.SLOTS),
                new SimpleContainerData(OvenBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public OvenMenu(int containerId, Inventory inventory, OvenBlockEntity oven) {
        this(containerId, inventory, oven.fire().fuel(), oven.items(), oven.data(), oven.getBlockPos(), oven);
    }

    private OvenMenu(int containerId, Inventory inventory, Container fuel, Container items, ContainerData data, BlockPos pos,
                     @Nullable OvenBlockEntity oven) {
        super(ModMenus.OVEN.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.oven = oven;
        for (int i = 0; i < Fire.SLOTS; i++) {
            addSlot(new FilteredSlot(fuel, i, FUEL_X + i * STEP, FUEL_Y));
        }
        for (int i = 0; i < OvenBlockEntity.SLOTS; i++) {
            addSlot(new FilteredSlot(items, i, BAKE_X, BAKE_Y + i * BAKE_STEP));
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
        if (oven == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == BUTTON_IGNITE) {
            oven.igniteFromInventory(serverPlayer);
        } else if (id == BUTTON_EXTINGUISH) {
            oven.extinguish(serverPlayer);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return oven == null || Container.stillValidBlockEntity(oven, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        return Fire.fuelValue(stack) > 0 ? moveItemStackTo(stack, FUEL_START, BAKE_START, false)
                : !OvenBlockEntity.baked(stack).isEmpty() && moveItemStackTo(stack, BAKE_START, BAKE_END, false);
    }
}
