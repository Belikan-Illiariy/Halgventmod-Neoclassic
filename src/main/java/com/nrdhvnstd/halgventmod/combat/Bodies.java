package com.nrdhvnstd.halgventmod.combat;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.CorpsePayload;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Тела убитых: смерть игрока или НПС (после «Повержен», который её отменяет) — клиенты кладут тело на землю (client/Corpses). */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Bodies {
    private Bodies() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!event.isCanceled() && !entity.level().isClientSide && (entity instanceof Player || entity instanceof HalgNpc)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new CorpsePayload(entity.getId()));
        }
    }
}
