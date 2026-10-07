package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Shift + ПКМ ключом или связкой в воздух — окошко подписи. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class KeyNaming {
    private KeyNaming() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.KEY.get()) && !stack.is(ModItems.KEY_RING.get())) {
            return;
        }
        Minecraft.getInstance().setScreen(new KeyNameScreen(stack.getOrDefault(ModComponents.KEY_LABEL.get(), "")));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}