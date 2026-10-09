package com.nrdhvnstd.halgventmod.needs;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.HungerData;
import com.nrdhvnstd.halgventmod.network.NeedsSyncPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Голод, насыщение, жажда и выносливость → клиенту, когда что-то из них заметно изменилось. */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class NeedsSync {
    private NeedsSync() {}

    /** Что последним ушло клиенту. Не сохраняется. */
    public static final class Sent {
        int hunger = -1;
        int saturation = -1;  // ×10, чтобы не слать каждую сотую
        int thirst = -1;
        int stamina = -1;     // ×2: шаг полединицы, шкала двигается плавно
        boolean exhausted;
        int staminaTick = Integer.MIN_VALUE / 2; // когда выносливость уходила последний раз
    }

    // Выносливость меняется почти каждый тик (бег, восстановление) — одну её шлём не чаще раза в столько тиков.
    // Голод, жажда, изнеможение, пустая и полная шкала — сразу
    private static final int STAMINA_INTERVAL = 4;

    // LOWEST — после того, как голод, жажда и выносливость посчитали этот тик
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        HungerData hunger = player.getData(ModAttachments.HUNGER);
        StaminaData stamina = player.getData(ModAttachments.STAMINA);
        int thirst = player.getData(ModAttachments.THIRST).level();
        Sent sent = player.getData(ModAttachments.NEEDS_SENT);

        int saturation = Math.round(hunger.saturation() * 10);
        int staminaSteps = Math.round(stamina.level() * 2);
        boolean exhausted = stamina.exhaustedTicks() > 0;
        boolean others = hunger.level() != sent.hunger || saturation != sent.saturation || thirst != sent.thirst
                || exhausted != sent.exhausted;
        if (!others) {
            if (staminaSteps == sent.stamina) {
                return;
            }
            boolean edge = stamina.level() <= 0 || stamina.level() >= StaminaData.MAX;
            if (!edge && player.tickCount - sent.staminaTick < STAMINA_INTERVAL) {
                return;
            }
        }
        sent.staminaTick = player.tickCount;
        sent.hunger = hunger.level();
        sent.saturation = saturation;
        sent.thirst = thirst;
        sent.stamina = staminaSteps;
        sent.exhausted = exhausted;
        PacketDistributor.sendToPlayer(player, new NeedsSyncPayload(
                hunger.level(), hunger.saturation(), thirst, stamina.level(), stamina.exhaustedTicks()));
    }

    // При смене измерения клиент создаёт игрока заново — отправим всё ещё раз
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        event.getEntity().setData(ModAttachments.NEEDS_SENT, new Sent());
    }
}
