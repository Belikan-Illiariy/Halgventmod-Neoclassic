package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.brewing.Brewing;
import com.nrdhvnstd.halgventmod.brewing.StillBlockEntity;
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
import net.minecraft.world.item.Items;

/**
 * Окно перегонного куба: сырьё → куб → крепкое (ячейки «сырьё», «склянки», «крепкое»), сколько залито и перегнано,
 * «Слить»; над очагом — ещё огонь очага (дрова, температура, «Разжечь» / «Затушить»).
 */
public class StillMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int FUEL_X = 27;
    public static final int FUEL_Y = 81;
    public static final int IN_X = 112;
    public static final int IN_Y = 22;
    public static final int BOTTLES_X = 112;
    public static final int BOTTLES_Y = 62;
    public static final int OUT_X = 166;
    public static final int OUT_Y = 62;
    public static final int STILL_START = PLAYER_END;
    public static final int FUEL_START = STILL_START + 3;
    public static final int BUTTON_IGNITE = 0;
    public static final int BUTTON_EXTINGUISH = 1;
    public static final int BUTTON_DRAIN = 2;

    private final ContainerData still;
    private final ContainerData fire;
    private final boolean hasHearth;
    private final BlockPos pos;
    @Nullable
    private final StillBlockEntity station;
    @Nullable
    private final HearthBlockEntity hearth;

    public static StillMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean hasHearth = buf.readBoolean();
        return new StillMenu(containerId, inventory, new SimpleContainer(3), new SimpleContainerData(StillBlockEntity.DATA),
                hasHearth ? new SimpleContainer(Fire.SLOTS) : null, new SimpleContainerData(HearthBlockEntity.DATA), pos,
                null, null);
    }

    public StillMenu(int containerId, Inventory inventory, StillBlockEntity still, @Nullable HearthBlockEntity hearth) {
        this(containerId, inventory, still.slots(), still.data(), hearth == null ? null : hearth.fire().fuel(),
                hearth == null ? new SimpleContainerData(HearthBlockEntity.DATA) : hearth.data(), still.getBlockPos(), still, hearth);
    }

    private StillMenu(int containerId, Inventory inventory, Container slots, ContainerData still, @Nullable Container fuel,
                      ContainerData fire, BlockPos pos, @Nullable StillBlockEntity station, @Nullable HearthBlockEntity hearth) {
        super(ModMenus.STILL.get(), containerId, inventory, 30, 130);
        this.still = still;
        this.fire = fire;
        this.hasHearth = fuel != null;
        this.pos = pos;
        this.station = station;
        this.hearth = hearth;
        addSlot(new FilteredSlot(slots, StillBlockEntity.SLOT_IN, IN_X, IN_Y));
        addSlot(new FilteredSlot(slots, StillBlockEntity.SLOT_BOTTLES, BOTTLES_X, BOTTLES_Y));
        addSlot(new Slot(slots, StillBlockEntity.SLOT_OUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        if (fuel != null) {
            for (int i = 0; i < Fire.SLOTS; i++) {
                addSlot(new FilteredSlot(fuel, i, FUEL_X + i * STEP, FUEL_Y));
            }
        }
        addDataSlots(still);
        addDataSlots(fire);
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean hasHearth() {
        return hasHearth;
    }

    public int still(int index) {
        return still.get(index);
    }

    public int fire(int index) {
        return fire.get(index);
    }

    public static int ratio() {
        return Brewing.DISTILL_RATIO;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (station == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        switch (id) {
            case BUTTON_IGNITE -> {
                if (hearth != null) {
                    hearth.igniteFromInventory(serverPlayer);
                }
            }
            case BUTTON_EXTINGUISH -> {
                if (hearth != null) {
                    hearth.extinguish(serverPlayer);
                }
            }
            case BUTTON_DRAIN -> station.drain(serverPlayer);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return station == null || Container.stillValidBlockEntity(station, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        if (hasHearth && Fire.fuelValue(stack) > 0) {
            return moveItemStackTo(stack, FUEL_START, FUEL_START + Fire.SLOTS, false);
        }
        // Сосуд с пивом, вином, сидром, медовухой — «сырьё»; пустой или с крепким — под крепкое
        var stored = stack.get(com.nrdhvnstd.halgventmod.registry.ModComponents.STORED.get());
        boolean rawInside = stored != null && com.nrdhvnstd.halgventmod.brewing.Brewing.distillsInto(stored.stack()) != null;
        int slot = StillBlockEntity.vessel(stack) && !rawInside ? StillBlockEntity.SLOT_BOTTLES : StillBlockEntity.SLOT_IN;
        return moveItemStackTo(stack, STILL_START + slot, STILL_START + slot + 1, false);
    }
}
