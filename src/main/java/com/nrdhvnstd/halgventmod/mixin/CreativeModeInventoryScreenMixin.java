package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nrdhvnstd.halgventmod.client.InventoryOverride;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    // Вкладка «Инвентарь» каталога открывает наш инвентарь вместо ванильного
    @Inject(method = "selectTab", at = @At("HEAD"), cancellable = true)
    private void halgventmod$openOwnInventory(CreativeModeTab tab, CallbackInfo ci) {
        if (tab.getType() == CreativeModeTab.Type.INVENTORY) {
            ci.cancel();
            InventoryOverride.requestInventory();
        }
    }
}
