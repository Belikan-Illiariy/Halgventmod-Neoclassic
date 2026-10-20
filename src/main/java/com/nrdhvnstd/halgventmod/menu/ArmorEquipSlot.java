package com.nrdhvnstd.halgventmod.menu;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ArmorSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Ячейка брони в окне инвентаря: принимает только вещь своего слота, по одной. */
public class ArmorEquipSlot extends SlotItemHandler {
    private final ArmorInventory armor;
    private final ArmorSlot armorSlot;
    private final Entity wearer; // на ком броня: игрок или стойка — от него звук надевания

    public ArmorEquipSlot(ArmorInventory armor, ArmorSlot armorSlot, Entity wearer, int x, int y) {
        super(armor, armorSlot.ordinal(), x, y);
        this.armor = armor;
        this.armorSlot = armorSlot;
        this.wearer = wearer;
    }

    // Надел или снял щелчком, Shift+щелчком или цифрой: один звук на операцию — по новой вещи, а если ячейка опустела — по снятой.
    // Как у ванильной второй руки: здесь, а не в onTake или quickMoveStack, иначе звук двойной
    @Override
    public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
        super.setByPlayer(newStack, oldStack);
        ArmorSounds.equip(wearer, newStack.isEmpty() ? oldStack : newStack);
    }

    public ArmorSlot armorSlot() {
        return armorSlot;
    }

    /** Почему сюда нельзя положить вещь (правила «капюшон — шлем», «шлем — подшлемник») или null. */
    @Nullable
    public Component refusal(ItemStack stack) {
        return ArmorRules.refusal(armor, armorSlot, stack);
    }

    // Без сообщений: игра спрашивает об этом и при наведении/перетаскивании; сообщает меню при клике
    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && refusal(stack) == null;
    }

    /** Почему отсюда нельзя вынуть вещь (подшлемник под шлемом) или null. */
    @Nullable
    public Component takeOffRefusal() {
        return ArmorRules.takeOffRefusal(armor, armorSlot);
    }

    @Override
    public boolean mayPickup(Player player) {
        return super.mayPickup(player) && takeOffRefusal() == null;
    }

    // Силуэт пустой ячейки. Временно — ванильные силуэты по части тела;
    // рисунки художника лягут в textures/item/empty_slot/<папка слота>.png
    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        ResourceLocation icon = switch (armorSlot.bodyPart()) {
            case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case TORSO -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> armorSlot == ArmorSlot.BOOTS
                    ? InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS
                    : InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
        };
        return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
    }
}
