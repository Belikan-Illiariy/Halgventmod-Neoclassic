package com.nrdhvnstd.halgventmod.client.sound;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSounds;
import com.nrdhvnstd.halgventmod.draw.Drawing;
import com.nrdhvnstd.halgventmod.mixin.AbstractArrowAccessor;
import com.nrdhvnstd.halgventmod.needs.Stamina;
import com.nrdhvnstd.halgventmod.network.SwingPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.sound.CombatSounds;
import com.nrdhvnstd.halgventmod.sound.SoundPlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Звуки боя, которые считает каждый клиент сам (docs/sound_spec/TZ_sounds.md, 6.2, 6.4, 6.6):
 * звон лат и кольчуги на ходу у всех видимых игроков (без пакетов), свист пролетающей стрелы, замах в воздух.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CombatSoundsClient {
    // ===== Звон брони на ходу ⚠️ =====
    public static final double STEP_DISTANCE = 1.8;   // раз на столько пройденных по земле блоков
    public static final float WALK_VOLUME = 0.25F;
    public static final float SPRINT_VOLUME = 0.4F;
    public static final float SNEAK_VOLUME = 0.1F;
    public static final float MOVE_PITCH_SPREAD = 0.1F;
    private static final double MOVE_RANGE = 24.0;    // дальше всё равно не слышно
    private static final double TELEPORT = 2.0;       // сдвиг за тик больше этого — не шаги

    // ===== Свист стрелы ⚠️ =====
    public static final double ARROW_FLY_DISTANCE = 6.0;
    public static final float ARROW_FLY_VOLUME = 0.5F;
    public static final float ARROW_FLY_PITCH_SPREAD = 0.1F;
    private static final double ARROW_MIN_SPEED = 0.5; // блоков за тик: лежащая и падающая стрела не свистит

    private static final class Steps {
        double lastX;
        double lastZ;
        double walked;
        boolean seen;
    }

    private static final Map<Player, Steps> STEPS = new WeakHashMap<>();
    private static final Set<AbstractArrow> WHISTLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static long lastAirSwing = Long.MIN_VALUE / 2;

    private CombatSoundsClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer self = minecraft.player;
        if (level == null || self == null || minecraft.isPaused()) {
            return;
        }
        for (Player player : level.players()) {
            if (player.distanceToSqr(self) <= MOVE_RANGE * MOVE_RANGE) {
                armorSteps(level, player);
            }
        }
        arrowsFlyingBy(minecraft, level, self);
    }

    // ----- Латы и кольчуга звенят в такт шагам -----

    private static void armorSteps(ClientLevel level, Player player) {
        Steps steps = STEPS.computeIfAbsent(player, p -> new Steps());
        double moved = Math.hypot(player.getX() - steps.lastX, player.getZ() - steps.lastZ);
        steps.lastX = player.getX();
        steps.lastZ = player.getZ();
        if (!steps.seen || moved > TELEPORT) {
            steps.seen = true;
            return;
        }
        if (player.isSpectator() || player.isPassenger() || player.isInWater() || !player.onGround()) {
            return; // в воде без звука, в прыжке шаги не идут
        }
        steps.walked += moved;
        if (steps.walked < STEP_DISTANCE) {
            return;
        }
        steps.walked -= STEP_DISTANCE;
        ArmorInventory armor = player.getExistingDataOrNull(ModAttachments.ARMOR);
        SoundEvent sound = armor != null ? ArmorSounds.moveSound(armor) : null;
        if (sound == null) {
            return;
        }
        float volume = player.isCrouching() ? SNEAK_VOLUME : player.isSprinting() ? SPRINT_VOLUME : WALK_VOLUME;
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS,
                volume, SoundPlay.pitch(level.getRandom(), MOVE_PITCH_SPREAD), false);
    }

    // ----- Чужая стрела пролетает рядом: свист, один раз на стрелу, звук летит вместе с ней -----

    private static void arrowsFlyingBy(Minecraft minecraft, ClientLevel level, LocalPlayer self) {
        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, self.getBoundingBox().inflate(ARROW_FLY_DISTANCE))) {
            if (arrow instanceof ThrownTrident || arrow.getOwner() == self || WHISTLED.contains(arrow)
                    || ((AbstractArrowAccessor) arrow).halgventmod$inGround()
                    || arrow.getDeltaMovement().lengthSqr() < ARROW_MIN_SPEED * ARROW_MIN_SPEED
                    || arrow.distanceToSqr(self) > ARROW_FLY_DISTANCE * ARROW_FLY_DISTANCE) {
                continue;
            }
            WHISTLED.add(arrow);
            minecraft.getSoundManager().play(new EntityBoundSoundInstance(ModSounds.COMBAT_ARROW_FLY.get(), SoundSource.NEUTRAL,
                    ARROW_FLY_VOLUME, SoundPlay.pitch(level.getRandom(), ARROW_FLY_PITCH_SPREAD), arrow, level.getRandom().nextLong()));
        }
    }

    // ----- Замах в воздух: сразу у себя и пакет на сервер, чтобы услышали остальные -----

    // Сила удара к этому моменту уже сброшена, поэтому частота — своим таймером
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        ItemStack hand = player.getMainHandItem();
        long now = player.level().getGameTime();
        if (!CombatSounds.swings(hand) || player.isSpectator() || !Drawing.ready(player) || Stamina.exhausted(player)
                || now - lastAirSwing < CombatSounds.SWING_COOLDOWN_TICKS) {
            return;
        }
        lastAirSwing = now;
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), CombatSounds.swingSound(hand),
                SoundSource.PLAYERS, CombatSounds.swingVolume(hand),
                SoundPlay.pitch(player.getRandom(), CombatSounds.SWING_PITCH_SPREAD), false);
        PacketDistributor.sendToServer(SwingPayload.INSTANCE);
    }
}
