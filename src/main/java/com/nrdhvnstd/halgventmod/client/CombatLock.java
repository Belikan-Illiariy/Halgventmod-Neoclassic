package com.nrdhvnstd.halgventmod.client;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.WeaponProfiles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Захват цели (по образцу Kingdom Come: Deliverance и Kingdom Come Combat): клавиша (средняя кнопка мыши) берёт
 * противника ближе всех к прицелу — до {@link #RANGE} блоков, в поле зрения, видимого; ещё раз — отпустить.
 * <ul>
 *   <li>камера сама держит противника, а мышь не крутит взгляд, а выбирает сторону удара и блока (CombatInput,
 *       MouseHandlerMixin) — как в KCD;</li>
 *   <li>шаг осторожный (×{@link #STEP}), бег закрыт; оружие в стойке выбранной стороны — видно другим;</li>
 *   <li>захват слетает: противник погиб, ушёл дальше {@link #DROP_RANGE}, скрылся из виду на {@link #LOST_TICKS} тиков,
 *       в руке нет оружия боя, открыт экран.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CombatLock {
    // Меняйте здесь ⚠️
    private static final double RANGE = 10;
    private static final double DROP_RANGE = 14;
    private static final double CONE = 0.5;            // косинус половины угла поиска (~60°)
    private static final int LOST_TICKS = 30;
    private static final float TURN_SPEED = 12.0F;     // как быстро камера доводит до цели (доля за секунду)
    private static final double AIM_BELOW_EYES = 0.35; // смотреть чуть ниже глаз — в грудь-шею
    private static final float STEP = 0.7F;

    @Nullable
    private static LivingEntity target;
    private static int lost;
    private static long lastFrame;

    private CombatLock() {}

    @Nullable
    public static LivingEntity target() {
        return target;
    }

    public static boolean locked() {
        return target != null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean pressed = false;
        while (ModKeys.LOCK_ON.consumeClick()) {
            pressed = true;
        }
        if (player == null) {
            target = null;
            return;
        }
        if (pressed && minecraft.screen == null) {
            if (target != null) {
                release();
            } else if (WeaponProfiles.handles(player)) {
                LivingEntity found = find(player);
                if (found != null) {
                    target = found;
                    lost = 0;
                    CombatInput.stanceChanged(true);
                }
            }
        }
        if (target == null) {
            return;
        }
        boolean visible = player.hasLineOfSight(target);
        lost = visible ? 0 : lost + 1;
        if (!target.isAlive() || target.isRemoved() || player.distanceTo(target) > DROP_RANGE || lost > LOST_TICKS
                || !WeaponProfiles.handles(player) || minecraft.screen != null || !player.isAlive()) {
            release();
            return;
        }
        player.setSprinting(false); // в стойке не бегают
    }

    private static void release() {
        target = null;
        CombatInput.stanceChanged(false);
    }

    @Nullable
    private static LivingEntity find(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        LivingEntity best = null;
        double bestScore = -1;
        for (Entity entity : player.level().getEntities(player, new AABB(eye, eye).inflate(RANGE),
                e -> e instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && e.isPickable())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            double facing = to.normalize().dot(look);
            if (distance > RANGE || facing < CONE || !player.hasLineOfSight(entity)) {
                continue;
            }
            double score = facing * 2 - distance / RANGE; // ближе к прицелу важнее, чем ближе к себе
            if (score > bestScore) {
                bestScore = score;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    // Камера доводит взгляд до цели каждый кадр — плавно, как поворот мышью (Entity.turn меняет и прошлый угол)
    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        long nowNanos = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min(0.1F, (nowNanos - lastFrame) / 1.0E9F);
        lastFrame = nowNanos;
        if (player == null || target == null || minecraft.isPaused()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partial);
        Vec3 aim = target.getPosition(partial).add(0, target.getEyeHeight() - AIM_BELOW_EYES, 0);
        Vec3 to = aim.subtract(eye);
        float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        float wantPitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        float share = Math.min(1, seconds * TURN_SPEED);
        float dYaw = Mth.wrapDegrees(wantYaw - player.getYRot()) * share;
        float dPitch = (wantPitch - player.getXRot()) * share;
        player.setYRot(player.getYRot() + dYaw);
        player.setXRot(Mth.clamp(player.getXRot() + dPitch, -90, 90));
        player.yRotO += dYaw;
        player.xRotO += dPitch;
    }

    // Осторожный шаг в стойке
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (target != null) {
            event.getInput().forwardImpulse *= STEP;
            event.getInput().leftImpulse *= STEP;
        }
    }
}
