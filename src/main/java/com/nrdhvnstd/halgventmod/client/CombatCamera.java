package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.StrikeSide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Камера в бою — «сочность» ударов, только вид (прицел и трасса клинка не сдвигаются):
 * <ul>
 *   <li>свой взмах — камера чуть идёт за клинком: сбоку — крен в сторону удара, сверху и снизу — кивок;</li>
 *   <li>заряд — взгляд сужается (поле зрения), укол — короткий рывок вперёд;</li>
 *   <li>попал — короткий толчок (punch), получил удар или удар пришёлся в блок — тряска (shake, затухает).</li>
 * </ul>
 * Исходы приходят в ClientCombat.onHit (CombatHitPayload).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CombatCamera {
    // Меняйте здесь ⚠️
    public static final float HIT_PUNCH = 1.0F;           // сила толчка при своём попадании
    public static final float HURT_SHAKE = 1.0F;          // тряска, когда задели тебя
    public static final float BLOCK_SHAKE = 0.5F;         // тряска от удара в блок (свой или чужой)
    private static final float SHAKE_DEGREES = 3.5F;      // размах тряски при силе 1
    private static final float SHAKE_DECAY = 0.12F;       // за тик
    private static final float PUNCH_DEGREES = 2.5F;      // кивок камеры при попадании
    private static final float PUNCH_DECAY = 0.25F;
    private static final float FOLLOW_ROLL = 4.0F;        // крен за ударом сбоку
    private static final float FOLLOW_YAW = 2.5F;
    private static final float FOLLOW_PITCH = 3.0F;       // кивок за ударом сверху и снизу
    private static final float CHARGE_ZOOM = 0.06F;       // заряд сужает поле зрения на столько
    private static final float STAB_ZOOM = 0.05F;         // укол — рывок

    private static float shake;
    private static float shakePrev;
    private static float punch;
    private static float punchPrev;

    private CombatCamera() {}

    public static void shake(float power) {
        shake = Math.min(1.0F, Math.max(shake, power));
    }

    public static void punch(float power) {
        punch = Math.min(1.0F, punch + power);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        shakePrev = shake;
        punchPrev = punch;
        shake = Math.max(0.0F, shake - SHAKE_DECAY);
        punch = Math.max(0.0F, punch - PUNCH_DECAY);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float time = player.tickCount + partial;
        // Тряска: квадрат силы — сильный удар трясёт заметно, слабый едва
        float s = Mth.lerp(partial, shakePrev, shake);
        if (s > 0) {
            float amount = s * s * SHAKE_DEGREES;
            event.setPitch(event.getPitch() + Mth.sin(time * 2.9F) * amount);
            event.setYaw(event.getYaw() + Mth.sin(time * 3.7F + 1.3F) * amount);
            event.setRoll(event.getRoll() + Mth.sin(time * 2.3F + 2.1F) * amount);
        }
        float p = Mth.lerp(partial, punchPrev, punch);
        if (p > 0) {
            event.setPitch(event.getPitch() + p * PUNCH_DEGREES); // клинок упёрся — голову чуть качнуло вниз
        }
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        // За своим взмахом
        CombatAnimation.Pose pose = CombatAnimation.of(player, partial);
        if (pose.kind() != CombatAnimation.Kind.SWING || pose.shape() == AttackKind.STAB) {
            return;
        }
        float arc = Mth.sin(Mth.clamp(pose.path(), 0, 1) * Mth.PI) * pose.blend();
        switch (pose.side()) {
            case LEFT, RIGHT -> {
                float dir = pose.side() == StrikeSide.RIGHT ? -1 : 1;
                event.setRoll(event.getRoll() + dir * arc * FOLLOW_ROLL);
                event.setYaw(event.getYaw() + dir * arc * FOLLOW_YAW);
            }
            case UP -> event.setPitch(event.getPitch() + arc * FOLLOW_PITCH);
            case DOWN -> event.setPitch(event.getPitch() - arc * FOLLOW_PITCH);
        }
    }

    // Заряд сужает взгляд, укол — рывок вперёд
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !event.usedConfiguredFov()) {
            return;
        }
        CombatAnimation.Pose pose = CombatAnimation.of(player, (float) event.getPartialTick());
        if (pose.kind() != CombatAnimation.Kind.SWING) {
            return;
        }
        float zoom = pose.charge() * CHARGE_ZOOM;
        if (pose.shape() == AttackKind.STAB) {
            zoom += Mth.sin(Mth.clamp(pose.path(), 0, 1) * Mth.PI) * pose.blend() * STAB_ZOOM;
        }
        event.setFOV(event.getFOV() * (1.0 - zoom));
    }
}
