package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.network.DrinkWaterPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** ПКМ пустой рукой по воде — глоток. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class DrinkWaterInput {
    private DrinkWaterInput() {}

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.getMainHandItem().isEmpty() || !Thirst.needs(player)) {
            return;
        }
        // Луч, который видит воду. Если перед водой стоит блок (дверь, сундук) — луч упрётся в него,
        // и обычное действие не пострадает
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, true);
        if (hit.getType() != HitResult.Type.BLOCK
                || !player.level().getBlockState(((BlockHitResult) hit).getBlockPos()).is(Blocks.WATER)) {
            return;
        }
        PacketDistributor.sendToServer(DrinkWaterPayload.INSTANCE);
        event.setSwingHand(false);
        event.setCanceled(true);
    }
}