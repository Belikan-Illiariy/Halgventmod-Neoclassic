package com.nrdhvnstd.halgventmod.client;

import java.util.Arrays;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.pickup.Pickup;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.Phase;
import com.nrdhvnstd.halgventmod.combat.StrikeSide;
import com.nrdhvnstd.halgventmod.combat.WeaponProfiles;
import com.nrdhvnstd.halgventmod.network.CombatInputPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ввод боя (docs/TZ_chivalry_combat.md, 2 и 4.9): кнопки ударов → CombatInputPayload, сервер решает всё сам.
 * С оружием из профилей (и пустой рукой) ванильная атака не работает: клики ЛКМ забираем раньше ванили (в начале
 * тика), остальное (удержание по блоку) гасим через InteractionKeyMappingTriggered. В творческом режиме ЛКМ по
 * блоку — как в ванили (ломать при стройке). ПКМ — блок, пока зажата (ванильное использование предмета отменено);
 * в своём замахе ПКМ — финт. Финт — ещё и отдельная клавиша.
 *
 * Сторона удара и блока — по движению мыши за последние FLICK_TICKS тиков перед кликом (как в Mount &amp; Blade):
 * мышь влево — слева, вправо — справа, вверх — сверху, вниз — снизу; мышь стоит — со стороны стойки (последнее
 * заметное движение, как в KCD; индикатор у прицела). В захвате цели (CombatLock) мышь не крутит камеру, а только
 * выбирает сторону.
 * Пока блок зажат, его сторона меняется только резким движением (BLOCK_SWITCH), чтобы не прыгала, когда ведёшь
 * взглядом противника.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CombatInput {
    @Nullable
    private static KeyMapping held; // кнопка удара, которая сейчас зажата (её отпускание — RELEASE)
    private static boolean blockSent; // сервер знает, что ПКМ зажата
    private static final int SPRINT_GUESS = 40; // бег закрыт, пока не придёт удар с разбега с сервера
    // Движение мыши ⚠️
    private static final int FLICK_TICKS = 3;          // окно перед кликом
    private static final float FLICK_MIN = 2.0F;       // градусов поворота за окно; меньше — мышь стоит
    private static final float BLOCK_SWITCH = 6.0F;    // смена стороны зажатого блока — только резким движением
    private static final float[] YAW = new float[FLICK_TICKS];
    private static final float[] PITCH = new float[FLICK_TICKS];
    private static int cursor;
    private static float lastYaw, lastPitch;
    private static StrikeSide blockSide = StrikeSide.UP; // сторона блока, которую знает сервер
    // Стойка (как в KCD): выбранная мышью сторона держится, пока её не сменят другим движением; удар и блок без
    // свежего рывка идут с неё. В захвате цели мышь не крутит камеру — её движение копится здесь (MouseHandlerMixin)
    private static final float STANCE_SWITCH = 3.0F;   // смена стойки — движение не меньше этого за окно ⚠️
    private static StrikeSide stance = StrikeSide.RIGHT;
    private static float virtualYaw, virtualPitch;

    private CombatInput() {}

    /** Удар этого игрока сейчас ведёт бой, а не ваниль. */
    private static boolean combat(Minecraft minecraft, LocalPlayer player) {
        if (player.isSpectator() || !WeaponProfiles.handles(player)) {
            return false;
        }
        return !player.getAbilities().instabuild
                || minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // Клики своих клавиш копятся, пока не забраны: забираем всегда, чтобы не сработали разом потом
        int feint = clicks(ModKeys.FEINT);
        int kick = clicks(ModKeys.KICK);
        int grip = clicks(ModKeys.GRIP);
        if (player != null) {
            track(player);
        }
        if (player == null || minecraft.screen != null || !combat(minecraft, player)) {
            if (held != null) {
                send(CombatInputPayload.Action.ATTACK_RELEASE, AttackKind.SLASH);
                held = null;
            }
            block(false);
            return;
        }
        block(minecraft.options.keyUse.isDown() && !aimsAtMenu(minecraft));
        if (feint > 0) {
            send(CombatInputPayload.Action.FEINT, AttackKind.SLASH);
        }
        if (kick > 0) {
            send(CombatInputPayload.Action.KICK, AttackKind.SLASH);
        }
        if (grip > 0) {
            send(CombatInputPayload.Action.GRIP, AttackKind.SLASH);
        }
        int slash = clicks(minecraft.options.keyAttack);
        if (held != null && !held.isDown()) {
            send(CombatInputPayload.Action.ATTACK_RELEASE, AttackKind.SLASH);
            held = null;
        }
        // Одно нажатие за тик; лишние клики того же тика сервер всё равно не примет (удар уже идёт)
        if (slash > 0) {
            start(player, minecraft.options.keyAttack, AttackKind.SLASH);
        }
    }

    // ПКМ — блок, пока зажата (в своём замахе — финт и блок, решает сервер). Сторона — по мыши
    private static void block(boolean down) {
        if (down != blockSent) {
            blockSent = down;
            if (down) {
                StrikeSide side = flick(FLICK_MIN);
                blockSide = side != null ? side : stance; // без рывка — со стороны стойки
            }
            send(down ? CombatInputPayload.Action.BLOCK_DOWN : CombatInputPayload.Action.BLOCK_UP, AttackKind.SLASH, blockSide);
        } else if (down) {
            StrikeSide side = flick(BLOCK_SWITCH);
            if (side != null && side != blockSide) {
                blockSide = side;
                send(CombatInputPayload.Action.BLOCK_SIDE, AttackKind.SLASH, side);
                Arrays.fill(YAW, 0);
                Arrays.fill(PITCH, 0);
            }
        }
    }

    // Поворот камеры за тик — это и есть движение мыши; в захвате цели камеру ведёт захват, а мышь копится отдельно
    private static void track(LocalPlayer player) {
        if (CombatLock.locked()) {
            YAW[cursor] = virtualYaw;
            PITCH[cursor] = virtualPitch;
        } else {
            YAW[cursor] = Mth.wrapDegrees(player.getYRot() - lastYaw);
            PITCH[cursor] = player.getXRot() - lastPitch;
        }
        virtualYaw = 0;
        virtualPitch = 0;
        cursor = (cursor + 1) % FLICK_TICKS;
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        // Стойка — за движением мыши
        StrikeSide side = flick(STANCE_SWITCH);
        if (side != null && side != stance) {
            stance = side;
            if (CombatLock.locked()) {
                send(CombatInputPayload.Action.STANCE_ON, AttackKind.SLASH, stance);
            }
        }
    }

    /** Движение мыши в захвате цели — не в камеру, а в выбор стороны (как Entity.turn: 0,15° на единицу). */
    public static void virtualTurn(double yaw, double pitch) {
        virtualYaw += (float) (yaw * 0.15);
        virtualPitch += (float) (pitch * 0.15);
    }

    /** Выбранная мышью сторона (стойка): индикатор у прицела, удар и блок без свежего рывка. */
    public static StrikeSide stance() {
        return stance;
    }

    /** Захват цели взят или отпущен (CombatLock): сервер покажет стойку другим. */
    static void stanceChanged(boolean on) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.getData(ModAttachments.COMBAT).engaged = on;
        }
        send(on ? CombatInputPayload.Action.STANCE_ON : CombatInputPayload.Action.STANCE_OFF, AttackKind.SLASH, stance);
    }

    /** Куда двинули мышь за окно, или null — почти не двигали. Рыскание вправо растёт, взгляд вверх — тангаж меньше. */
    @Nullable
    private static StrikeSide flick(float min) {
        float yaw = 0;
        float pitch = 0;
        for (int i = 0; i < FLICK_TICKS; i++) {
            yaw += YAW[i];
            pitch += PITCH[i];
        }
        if (Math.abs(yaw) > 90 || Math.hypot(yaw, pitch) < min) {
            return null; // больше 90° за окно — телепорт или поворот сервером, не мышь
        }
        if (Math.abs(yaw) >= Math.abs(pitch)) {
            return yaw > 0 ? StrikeSide.RIGHT : StrikeSide.LEFT;
        }
        return pitch < 0 ? StrikeSide.UP : StrikeSide.DOWN;
    }

    // На бегу любая кнопка удара — удар с разбега (4.7): бег сразу гасим, пока сервер не пришлёт точный запрет
    private static void start(LocalPlayer player, KeyMapping key, AttackKind kind) {
        if (player.isSprinting() && player.getData(ModAttachments.COMBAT).phase == Phase.IDLE) {
            kind = AttackKind.SPRINT;
            player.setSprinting(false);
            player.getData(ModAttachments.COMBAT).sprintLockUntil = player.level().getGameTime() + SPRINT_GUESS;
        }
        StrikeSide side = flick(FLICK_MIN);
        send(CombatInputPayload.Action.ATTACK_START, kind, side != null ? side : stance); // без рывка — из стойки
        held = key;
        // Быстрый щелчок: нажал и отпустил внутри одного тика
        if (!key.isDown()) {
            send(CombatInputPayload.Action.ATTACK_RELEASE, kind);
            held = null;
        }
    }

    private static void send(CombatInputPayload.Action action, AttackKind kind) {
        send(action, kind, StrikeSide.RIGHT);
    }

    private static void send(CombatInputPayload.Action action, AttackKind kind, StrikeSide side) {
        PacketDistributor.sendToServer(new CombatInputPayload(action, kind, side));
    }

    private static int clicks(KeyMapping key) {
        int count = 0;
        while (key.consumeClick()) {
            count++;
        }
        return count;
    }

    // Ванильная атака (и удержание ЛКМ по блоку) с оружием боя — нет. ПКМ в своём замахе — финт (ТЗ боя, 4.4)
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !combat(minecraft, player)) {
            return;
        }
        if (event.isAttack()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        } else if (event.isUseItem() && !aimsAtMenu(minecraft)) {
            event.setSwingHand(false);
            event.setCanceled(true); // ПКМ с оружием — блок (onClientTick), а не ванильное использование
        }
    }

    // Под прицелом блок с окном (станция, прилавок, печь): ПКМ открывает его, а не поднимает блок оружием
    private static boolean aimsAtMenu(Minecraft minecraft) {
        return minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK && Pickup.opensMenu(minecraft.level, hit.getBlockPos());
    }
}
