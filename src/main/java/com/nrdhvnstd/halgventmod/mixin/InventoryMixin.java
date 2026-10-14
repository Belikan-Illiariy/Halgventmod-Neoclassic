package com.nrdhvnstd.halgventmod.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nrdhvnstd.halgventmod.storage.Storage;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Ячейки 9–35 ванильного инвентаря не видны (бывший рюкзак) — сюда игра больше ничего не кладёт.
 * Свободное место ищется только в карманах (0–8), а остальное идёт в надетые сумки через Storage.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    @Shadow
    public int selected;

    @Shadow
    @Final
    public Player player;

    @Shadow
    private boolean hasRemainingSpaceForItem(ItemStack destination, ItemStack origin) {
        throw new AssertionError();
    }

    @Shadow
    public abstract ItemStack getItem(int index);

    // Первая пустая ячейка — только среди карманов
    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void halgventmod$pocketsOnly(CallbackInfoReturnable<Integer> cir) {
        for (int i = 0; i < Storage.POCKETS; i++) {
            if (items.get(i).isEmpty()) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    // Стопка, которую можно дополнить: рука, вторая рука, карманы — как в ванили, но без скрытых ячеек
    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void halgventmod$stackInPockets(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (hasRemainingSpaceForItem(getItem(selected), stack)) {
            cir.setReturnValue(selected);
            return;
        }
        if (hasRemainingSpaceForItem(getItem(Inventory.SLOT_OFFHAND), stack)) {
            cir.setReturnValue(Inventory.SLOT_OFFHAND);
            return;
        }
        for (int i = 0; i < Storage.POCKETS; i++) {
            if (hasRemainingSpaceForItem(items.get(i), stack)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    // Подбор, /give, выдача из команд: карманы, затем подсумки и спина
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void halgventmod$addToStorage(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Storage.add((Inventory) (Object) this, stack));
    }

    // Вещь с курсора при закрытии окна и прочие возвраты: так же, а что не влезло — под ноги
    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    private void halgventmod$placeBack(ItemStack stack, boolean sendPacket, CallbackInfo ci) {
        Storage.addOrDrop((Inventory) (Object) this, stack);
        ci.cancel();
    }

    // /clear и подсчёт вещей (/clear <игрок> <вещь> 0): и содержимое надетых сумок, подсумков, колчана.
    // Сами сумки надеты (снаряжение) и, как броня, остаются
    @Inject(method = "clearOrCountMatchingItems", at = @At("RETURN"), cancellable = true)
    private void halgventmod$clearBags(Predicate<ItemStack> predicate, int maxCount, Container craft,
                                       CallbackInfoReturnable<Integer> cir) {
        int done = cir.getReturnValueI();
        int left = maxCount < 0 ? -1 : maxCount == 0 ? 0 : maxCount - done;
        if (maxCount != 0 && left == 0) {
            return; // предел уже набран
        }
        cir.setReturnValue(done + Storage.clearOrCount(player, predicate, left));
    }
}
