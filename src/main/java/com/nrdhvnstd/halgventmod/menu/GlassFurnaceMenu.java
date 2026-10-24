package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.cooking.Fire;
import com.nrdhvnstd.halgventmod.glass.GlassBlocks;
import com.nrdhvnstd.halgventmod.glass.GlassFurnaceBlockEntity;
import com.nrdhvnstd.halgventmod.glass.Glassmaking;

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
 * Окно стекловаренной печи — всё в одном окне: слева что выдувать (выбор щелчком) и «Выдувать» (нужна трубка при
 * себе), посередине огонь ({@link Fire#SLOTS} ячейки дров, «Разжечь» / «Затушить»), справа шихта и стекломасса,
 * ячейка добавки-цвета, камера отжига (из неё только забирают) и «Забрать отожжённое».
 */
public class GlassFurnaceMenu extends StationMenu {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 212;
    public static final int FUEL_X = 88;
    public static final int FUEL_Y = 81;
    public static final int COLORANT_X = 162;
    public static final int COLORANT_Y = 62;
    public static final int LEHR_X = 162;
    public static final int LEHR_Y = 86;
    public static final int FUEL_START = PLAYER_END;
    public static final int COLORANT_SLOT = FUEL_START + Fire.SLOTS;
    public static final int LEHR_START = COLORANT_SLOT + 1;
    public static final int BATCH_SLOT = LEHR_START + Glassmaking.LEHR_SLOTS;
    public static final int BATCH_X = 162;
    public static final int BATCH_Y = 20;
    public static final int BUTTON_IGNITE = 0;
    public static final int BUTTON_EXTINGUISH = 1;
    public static final int BUTTON_BLOW = 2;
    public static final int BUTTON_TAKE = 3;
    public static final int BUTTON_FORM = 10; // + номер формы

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final GlassFurnaceBlockEntity furnace;

    public static GlassFurnaceMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new GlassFurnaceMenu(containerId, inventory, new SimpleContainer(Fire.SLOTS), new SimpleContainer(1),
                new SimpleContainer(Glassmaking.LEHR_SLOTS), new SimpleContainer(1),
                new SimpleContainerData(GlassFurnaceBlockEntity.DATA), buf.readBlockPos(), null);
    }

    public GlassFurnaceMenu(int containerId, Inventory inventory, GlassFurnaceBlockEntity furnace) {
        this(containerId, inventory, furnace.fire().fuel(), furnace.colorant(), furnace.lehr(), furnace.batch(),
                furnace.data(), furnace.getBlockPos(), furnace);
    }

    private GlassFurnaceMenu(int containerId, Inventory inventory, Container fuel, Container colorant, Container lehr,
                             Container batch, ContainerData data, BlockPos pos, @Nullable GlassFurnaceBlockEntity furnace) {
        super(GlassBlocks.GLASS_FURNACE_MENU.get(), containerId, inventory, 47, 130);
        this.data = data;
        this.pos = pos;
        this.furnace = furnace;
        for (int i = 0; i < Fire.SLOTS; i++) {
            addSlot(new FilteredSlot(fuel, i, FUEL_X + i * STEP, FUEL_Y));
        }
        addSlot(new FilteredSlot(colorant, 0, COLORANT_X, COLORANT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Glassmaking.Tint.of(stack) != null;
            }
        });
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            addSlot(new Slot(lehr, i, LEHR_X + i * STEP, LEHR_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addSlot(new FilteredSlot(batch, 0, BATCH_X, BATCH_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Glassmaking.batch(stack);
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
        if (furnace == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == BUTTON_IGNITE) {
            furnace.igniteFromInventory(serverPlayer);
        } else if (id == BUTTON_EXTINGUISH) {
            furnace.extinguish(serverPlayer);
        } else if (id == BUTTON_BLOW) {
            furnace.blowFromWindow(serverPlayer);
        } else if (id == BUTTON_TAKE) {
            furnace.takeFromWindow(serverPlayer);
        } else if (id >= BUTTON_FORM && id < BUTTON_FORM + Glassmaking.Form.values().length) {
            furnace.chooseForm(serverPlayer, id - BUTTON_FORM);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return furnace == null || Container.stillValidBlockEntity(furnace, player);
    }

    @Override
    protected boolean moveIntoStation(ItemStack stack) {
        if (Fire.fuelValue(stack) > 0) {
            return moveItemStackTo(stack, FUEL_START, COLORANT_SLOT, false);
        }
        if (Glassmaking.batch(stack)) {
            return moveItemStackTo(stack, BATCH_SLOT, BATCH_SLOT + 1, false);
        }
        return Glassmaking.Tint.of(stack) != null && moveItemStackTo(stack, COLORANT_SLOT, LEHR_START, false);
    }
}
