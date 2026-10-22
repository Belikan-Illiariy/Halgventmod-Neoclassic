package com.nrdhvnstd.halgventmod.pottery;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** Гончарное: порцию из горшка съели — в руке остаётся та глиняная миска, в которую её налили, а не деревянная (или треснувшая). */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class PotteryEvents {
    private PotteryEvents() {}

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        ItemStack bowl = event.getItem().get(ModComponents.SERVED_IN.get());
        if (bowl == null || !(event.getEntity() instanceof Player player) || player.hasInfiniteMaterials()) {
            return;
        }
        ItemStack back = bowl.copy();
        Pottery.wear(back, player); // от пользования миска может треснуть
        if (event.getResultStack().is(Items.BOWL)) {
            event.setResultStack(back);
            return;
        }
        // Съели одну из нескольких: деревянная миска уже ушла в инвентарь — заменить её глиняной
        if (player.level().isClientSide) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.BOWL)) {
                inventory.getItem(i).shrink(1);
                break;
            }
        }
        if (!inventory.add(back)) {
            player.drop(back, false);
        }
    }
}
