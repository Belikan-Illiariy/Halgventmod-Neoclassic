package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    // Сервер шлёт ячейки инвентаря (окно 0), а у клиента под номером 0 открыто чужое окно,
    // о котором сервер не знает: например, палитра Conquest Reforged (19 ячеек).
    // Ваниль записала бы ячейку в него и упала на «Index 27 out of bounds for length 19» —
    // пишем в настоящий инвентарь. Каталог творческого режима ваниль обрабатывает сама.
    @Inject(method = "handleContainerSetSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void halgventmod$foreignClientMenu(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || packet.getContainerId() != 0) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu || menu.containerId != 0
                || Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen) {
            return;
        }
        int slot = packet.getSlot();
        if (slot >= 0 && slot < player.inventoryMenu.slots.size()) {
            player.inventoryMenu.setItem(slot, packet.getStateId(), packet.getItem());
        }
        ci.cancel();
    }
}
