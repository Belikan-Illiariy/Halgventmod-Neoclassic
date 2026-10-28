package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.WeaponProfiles;
import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Изнеможение на стороне клиента: движение считает клиент, поэтому прыжок и всплытие
 * гасим здесь. Бег гасит LocalPlayerMixin, лук и арбалет — Stamina.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class StaminaInput {
    private StaminaInput() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.getData(ModAttachments.STAMINA).tickClient();
        }
    }

    // Ни прыжка, ни всплытия в воде
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (Stamina.exhausted(event.getEntity())) {
            event.getInput().jumping = false;
        }
    }

    // Удар не оружием боя по существу или по воздуху запрещён; копать блоки можно. Оружие боя бьёт и при одышке (CombatInput)
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isAttack() || minecraft.player == null || !Stamina.exhausted(minecraft.player)
                || WeaponProfiles.handles(minecraft.player)) {
            return;
        }
        if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }
}
