package com.nrdhvnstd.halgventmod.client.smithing;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.network.OreStrikePayload;
import com.nrdhvnstd.halgventmod.smithing.OreMining;
import com.nrdhvnstd.halgventmod.smithing.OreVeinBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ЛКМ по рудной жиле или илу: не ломать блок (в приключении игра и не дала бы), а бить — удар уходит на сервер
 * (OreMining), только когда замах восстановился и есть выносливость. Зажатая ЛКМ бьёт с каждым полным замахом.
 * Творческий режим без кирки и лопаты ломает жилу как обычно — строителям.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class OreMiningInput {
    private OreMiningInput() {}

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!event.isAttack() || player == null || minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (!(minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof OreVeinBlock vein)) {
            return;
        }
        if (player.isCreative() && !player.getMainHandItem().is(vein.kind().tool())) {
            return;
        }
        event.setCanceled(true);
        if (Stamina.exhausted(player) || player.getAttackStrengthScale(0.5F) < OreMining.STEADY) {
            event.setSwingHand(false);
            return;
        }
        event.setSwingHand(true);
        player.resetAttackStrengthTicker();
        PacketDistributor.sendToServer(new OreStrikePayload(hit.getBlockPos(), hit.getLocation().x,
                hit.getLocation().y, hit.getLocation().z));
    }
}
