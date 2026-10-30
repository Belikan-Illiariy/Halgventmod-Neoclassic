package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.brewing.Intoxication;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Хмель у себя на экране (уровень приходит с сервера — Intoxication.SYNC). Вместо ванильной тошноты — мягкая качка:
 * камера плавает по крену и чуть по сторонам, тем сильнее, чем больше выпито; у пьяного походку уводит вбок.
 * Прицел и направление удара качка не трогает — только вид.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class DrunkEffects {
    // Меняйте здесь ⚠️
    private static final float ROLL_DEGREES = 7.0F;    // крен при 100 хмеля
    private static final float YAW_DEGREES = 3.0F;     // повело в сторону при 100
    private static final float PITCH_DEGREES = 1.5F;
    private static final float SWAY_SPEED = 0.035F;    // оборотов качки за тик
    private static final float DRIFT = 0.45F;          // увод походки вбок при 100 (доля шага)
    private static final float DRIFT_SPEED = 0.02F;

    private DrunkEffects() {}

    /** Хмель 0…1: трезвому 0, «Навеселе» начинает с малого. */
    public static float strength(Player player) {
        float level = Intoxication.data(player).level();
        return level < Intoxication.TIPSY ? 0 : Mth.clamp(level / 100.0F, 0.0F, 1.0F);
    }

    // Качка: крен и рыскание — две синусоиды разной частоты, чтобы не было похоже на маятник
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float strength = strength(player);
        if (strength <= 0) {
            return;
        }
        float time = (float) (player.tickCount + event.getPartialTick()) * SWAY_SPEED * Mth.TWO_PI;
        float ease = strength * strength; // навеселе — едва, мертвецки — вовсю
        event.setRoll(event.getRoll() + (Mth.sin(time) + 0.4F * Mth.sin(time * 2.3F)) * ROLL_DEGREES * ease);
        event.setYaw(event.getYaw() + Mth.sin(time * 0.7F + 1.0F) * YAW_DEGREES * ease);
        event.setPitch(event.getPitch() + Mth.sin(time * 1.3F + 2.0F) * PITCH_DEGREES * ease);
    }

    // Походка: пьяного (и сильнее) при ходьбе уводит вбок
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (Intoxication.stage(Intoxication.data(player).level()).ordinal() < Intoxication.Stage.DRUNK.ordinal()) {
            return;
        }
        Input input = event.getInput();
        if (input.forwardImpulse == 0 && input.leftImpulse == 0) {
            return;
        }
        float time = player.tickCount * DRIFT_SPEED * Mth.TWO_PI;
        float drift = (Mth.sin(time) + 0.5F * Mth.sin(time * 2.7F)) * DRIFT * strength(player);
        input.leftImpulse = Mth.clamp(input.leftImpulse + drift, -1.0F, 1.0F);
    }
}
