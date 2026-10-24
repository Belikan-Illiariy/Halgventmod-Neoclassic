package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.alchemy.Alchemy;
import com.nrdhvnstd.halgventmod.brewing.Brewing;
import com.nrdhvnstd.halgventmod.brewing.FermentingBarrelBlockEntity;
import com.nrdhvnstd.halgventmod.brewing.StillBlockEntity;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;
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
 * Окно бродильной бочки: «влить» (сусло или крепкое), «добавка» (мёд, травы, ягоды), склянки или корчага, «готовое»;
 * сколько налито, как бродит и сколько ждать, когда скиснет; «Запечатать» / «Распечатать», «Вылить».
 * Запечатанную не трогают: ячейки закрыты.
 */
public class FermentingBarrelMenu extends StationMenu {
    public static final int WIDTH = 200;
    public static final int HEIGHT = 212;
    public static final int IN_X = 12, IN_Y = 22;
    public static final int FLAVOUR_X = 12, FLAVOUR_Y = 48;
    public static final int BOTTLES_X = 112, BOTTLES_Y = 76;
    public static final int OUT_X = 166, OUT_Y = 76;
    public static final int BARREL_START = PLAYER_END;
    public static final int BUTTON_POUR_OUT = 0;
    public static final int BUTTON_SEAL = 1;

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final FermentingBarrelBlockEntity barrel;

    public static FermentingBarrelMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new FermentingBarrelMenu(containerId, inventory, new SimpleContainer(4),
                new SimpleContainerData(FermentingBarrelBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public FermentingBarrelMenu(int containerId, Inventory inventory, FermentingBarrelBlockEntity barrel) {
        this(containerId, inventory, barrel.slots(), barrel.data(), barrel.getBlockPos(), barrel);
    }

    private FermentingBarrelMenu(int containerId, Inventory inventory, Container slots, ContainerData data, BlockPos pos,
                                 @Nullable FermentingBarrelBlockEntity barrel) {
        super(ModMenus.FERMENTING_BARREL.get(), containerId, inventory, 30, 130);
        this.data = data;
        this.pos = pos;
        this.barrel = barrel;
        addSlot(new FilteredSlot(slots, FermentingBarrelBlockEntity.SLOT_IN, IN_X, IN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return open() && (FermentingBarrelBlockEntity.liquid(stack) || Pottery.drinkCapacity(stack) > 0
                        || Pottery.isWater(stack, 1));
            }

            @Override
            public boolean mayPickup(Player player) {
                return open();
            }
        });
        addSlot(new FilteredSlot(slots, FermentingBarrelBlockEntity.SLOT_FLAVOUR, FLAVOUR_X, FLAVOUR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return open() && (Brewing.flavour(stack) != null || Alchemy.reagent(stack));
            }

            @Override
            public boolean mayPickup(Player player) {
                return open();
            }
        });
        addSlot(new FilteredSlot(slots, FermentingBarrelBlockEntity.SLOT_BOTTLES, BOTTLES_X, BOTTLES_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return open() && StillBlockEntity.vessel(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return open();
            }
        });
        addSlot(new Slot(slots, FermentingBarrelBlockEntity.SLOT_OUT, OUT_X, OUT_Y) {
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

    /** Не запечатана: ячейки открыты. */
    public boolean open() {
        return data.get(FermentingBarrelBlockEntity.D_SEALED) == 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (barrel == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == BUTTON_POUR_OUT) {
            barrel.pourOut(serverPlayer);
        } else if (id == BUTTON_SEAL) {
            barrel.toggleSeal(serverPlayer);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return barrel == null || Container.stillValidBlockEntity(barrel, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        boolean vesselOfMust = Pottery.drinkCapacity(stack) > 0 && stack.get(ModComponents.STORED.get()) != null
                && FermentingBarrelBlockEntity.liquid(stack.get(ModComponents.STORED.get()).stack());
        int slot = FermentingBarrelBlockEntity.liquid(stack) || vesselOfMust || stack.is(net.minecraft.world.item.Items.WATER_BUCKET)
                ? FermentingBarrelBlockEntity.SLOT_IN
                : Brewing.flavour(stack) != null || Alchemy.reagent(stack) ? FermentingBarrelBlockEntity.SLOT_FLAVOUR
                : StillBlockEntity.vessel(stack) ? FermentingBarrelBlockEntity.SLOT_BOTTLES : -1;
        return slot >= 0 && open() && moveItemStackTo(stack, BARREL_START + slot, BARREL_START + slot + 1, false);
    }
}
