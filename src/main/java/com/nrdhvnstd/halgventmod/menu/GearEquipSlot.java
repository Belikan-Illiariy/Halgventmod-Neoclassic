package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearRules;
import com.nrdhvnstd.halgventmod.gear.GearSlot;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Ячейка снаряжения в окне инвентаря: принимает только вещь своего вида, по одной. */
public class GearEquipSlot extends SlotItemHandler {
    private final GearInventory gear;
    private final GearSlot gearSlot;
    private final Player wearer;

    public GearEquipSlot(GearInventory gear, GearSlot gearSlot, Player wearer, int x, int y) {
        super(gear, gearSlot.ordinal(), x, y);
        this.gear = gear;
        this.gearSlot = gearSlot;
        this.wearer = wearer;
    }

    // Как у ячейки брони: один звук на надевание или снятие
    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        super.setByPlayer(newStack, oldStack);
        if (!newStack.isEmpty() || !oldStack.isEmpty()) {
            gearSlot.type().playEquip(wearer);
        }
    }

    public GearSlot gearSlot() {
        return gearSlot;
    }

    /** Почему сюда нельзя надеть вещь (нет пояса) или null. */
    @Nullable
    public Component refusal(ItemStack stack) {
        return GearRules.refusal(gear, gearSlot, stack);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && refusal(stack) == null;
    }

    /** Почему вещь нельзя снять (пояс, на котором что-то висит) или null. */
    @Nullable
    public Component takeOffRefusal() {
        return GearRules.takeOffRefusal(gear, gearSlot);
    }

    @Override
    public boolean mayPickup(Player player) {
        return super.mayPickup(player) && takeOffRefusal() == null;
    }

    // Окно меняет вещь на месте и зовёт setChanged — пусть снаряжение узнает и разошлёт
    @Override
    public void setChanged() {
        gear.markChanged(gearSlot);
    }
}
